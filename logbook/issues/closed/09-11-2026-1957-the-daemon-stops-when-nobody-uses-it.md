# The daemon stops when nobody has used it for a while

Opened: 09-11-2026 19:57

Real risk met while reviewing the MCP server: no code stops the daemon but `gabier stop` (nothing says "idle" in
`modules/daemon`, and `docs/daemon.md` has no such rule). The daemon is one per project, shared by the sessions of that
project, but with MCP every session starts it when the host connects, so every project an agent has worked in leaves a
daemon and its jdtls (a JVM and the Gradle import) running until `gabier stop` or a reboot. The command line already
had the leak; MCP makes it the normal case.

What an agent can do once it is closed and could not before: open projects through its host without leaving a jdtls
running behind each of them.

Done when:
- the daemon stops itself, with jdtls and its workspace, after a delay with no request of any kind (`status` and
  `ping` count as a request); the delay is read from a variable (`GABIER_IDLE_TIMEOUT`, in minutes, `0` turns it off),
  has the default of 60 minutes (decision of the user, 09-11-2026), said in `docs/daemon.md`, and is tested with a fake clock, with no real wait;
- a command sent while the daemon is stopping, or just after, starts a new one and is answered (no refusal that the
  next call would not have), shown by a test;
- the MCP server does not stop the daemon when the host disconnects (other sessions and the command line share it);
- `gabier status` says the delay and the time left;
- `bin/ci` is green.

## Resolution

Commit `a40de99`. `bin/ci` of the worker on the last commit: format, build, test (cli 1464, core 723, daemon 1017,
architecture 22), release-notes, native and jdtls passed, release-install skipped (no changed file reaches it); the check
`bin-ci` of the pull request #40 passed on the pushed head.

- The daemon stops itself after `GABIER_IDLE_TIMEOUT` minutes (60 by default, `0` off; an invalid or overflowing value is a
  `usage-error`), tested with a clock the test moves. `status` says the delay and the time left. The MCP server never stops
  the daemon when the host disconnects (test in `ServingMcpTest`).
- Reviewed once. BLOCKING, fixed in the commit: the cli retried on any lost connection, so a request the daemon had read
  could run twice, and a retry could meet the stopping daemon whose listener was still open. The daemon now answers
  `daemon-stopping` without reading the request, and the cli retries only on that key (a protocol addition, one new key).
  Also fixed: the overflow of the delay (the daemon never stopped) and the connection count left open on an exception.
  Suggestions opened as an on-hold issue (release notes check on hard cases).
- Remaining: the old daemon's `close()` may delete the socket file of a new daemon that bound in between (tiny race, not
  opened).
- Process: the worker edited some of its own files with a script and wrote some tests with the code, not strictly test first.

Tokens:
- worker: 440,291 (294,587 then 145,704 after the review fixes)
- reviewer: 101,153
- supervisor: 78 calls, 67,267 output, 271,922 input + cache write (cache reads 18,386,429), measured over the period of this
  ticket and the next one, other work included; exchanges with the user: 9 messages.
