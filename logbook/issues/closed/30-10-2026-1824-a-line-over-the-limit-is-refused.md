# A line over a length limit is refused, and the daemon goes on

Opened: 30-10-2026 18:24

`LineReader` (`modules/daemon/.../LineReader.java:25`) reads a request line into a `ByteArrayOutputStream` with no limit:
a client that streams bytes and never sends `\n` grows it until `OutOfMemoryError`. Found in the review of
`daemon-listens-on-a-unix-socket`.

Done when:
- a line longer than a fixed limit is refused with the problem object of `errors-reported-as-problem-objects`, and the
  daemon answers the next connection (shown by a test with a line one byte over the limit and one at the limit, which is
  read);
- the buffer never grows past the limit, whatever the client sends (shown by a test with a stream that has no `\n` and
  is longer than the limit);
- coverage stays at 100%, nothing in `check` needs jdtls.

## Resolution

Merged as `db94be4` (first commit of a batch of three issues of the `daemon` socket server).

Checked by the worker's `bin/ci --all` on the worktree before the rebase (format 7s, build 44s, test 97s, native 179s, jdtls 284s; each commit had passed `bin/ci --fast`), then, after the rebase on `main` (ArchUnit 1.5.1), `bin/ci --stage test` on the head: 135s, cli 67, core 259, daemon 319, protocol 255, shared 236, architecture 19, no failure; then rebased by the supervisor over a markdown-only commit.
`LineReader.MAX_LINE_BYTES` is 1 MiB, counted in bytes, the line break not counted (the `\\r` of `\\r\\n` is, see the issue `the-daemon-server-bounds-its-connections`). The reader throws `LineTooLongException` (key `line-too-long`, bad request, parameter `limit`) at the first byte over; `Connection` answers it with `JsonRpc.failure(null, ...)` (code -32000, id null), the reader drops the rest of that line without buffering it and the connection goes on with the next line. Proofs: `ConnectionTest.refusesALineOverTheLimitWithAProblemObjectAndAnswersTheNextRequest` and `readsALineOfExactlyTheLimit`; `DaemonServerTest.Requests.aLineOneByteOverTheLimitIsRefusedAndTheDaemonAnswersTheNextConnection` and `aLineOfExactlyTheLimitIsRead` on a real socket; `LineReaderTest.Limit` (one byte over, exactly the limit, bytes not characters, the rest of the line skipped, an endless stream without a line break throws at once having consumed at most one fill of the `BufferedInputStream`). No red step for the first run (the code was written before it); the mutation of the check to `Integer.MAX_VALUE` failed 7 tests, then was restored. The README protocol section says the limit.

Review (one pass, of the three commits): no BLOCKING finding. Suggestions, in the issue `the-daemon-server-bounds-its-connections`: a `\\r` counted while the wording says it is not; no cap on the connections and no read timeout; the test that asserts `<= 8192` instead of the reader's own count.

Tokens:
- worker: 126954 (the whole batch of three issues; its last completion notification)
- reviewer: 62392
- supervisor: 70432 output, 82525 input + cache write over 57 calls (cache read 24154977), supervisor session over the period of the batch (from the launch of the worker; the other workers and the rest of the session are included, so the figure is shared); exchanges with the user: 5 messages
