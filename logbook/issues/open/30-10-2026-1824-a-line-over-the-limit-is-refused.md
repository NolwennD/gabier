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
