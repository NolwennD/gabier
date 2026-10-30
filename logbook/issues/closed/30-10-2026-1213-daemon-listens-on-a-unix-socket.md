# The daemon listens on a Unix socket, and the CLI can ask and stop it

Opened: 30-10-2026 12:13

First slice of the daemon of the design: one per user, joined by a Unix domain socket (`UnixDomainSocketAddress`,
NIO channels, JDK only). It answers `status` and `stop`, nothing about Java yet (the operations plug in later, first
against the fake server). Requests and responses are JSON-RPC 2.0, one JSON object per line: the supervisor's choice,
which the user can change before this starts. A line needs no framing header, so the CLI native image (no third-party
library) writes and reads it by hand, and an error is a JSON-RPC error whose `data` holds the problem object of
`errors-reported-as-problem-objects`. The CLI gets two commands, `gabier status` (version, pid, uptime of the daemon, or
"no daemon running") and `gabier stop`. Starting the daemon on demand from the CLI is another ticket; here it is started
by hand (`gabier-daemon` from the installed distribution).

**The path of a socket is limited**: `sun_path` holds 108 bytes on Linux, so at most 107 for the path, and Java refuses to
bind a longer one. A path under a checkout or a worktree (`/home/.../.claude/worktrees/agent-.../...`) is easily too
long. The socket is therefore per user, not per worktree, in a short place: `$XDG_RUNTIME_DIR/gabier/gabier.sock`
(`/run/user/<uid>`), else `/tmp/gabier-<uid>/gabier.sock`, and `GABIER_SOCKET` overrides it. The length is measured in
bytes of the encoded path, and checked by one function used by the daemon and the CLI.

Done when:
- the socket path is resolved in one place; a path over 107 bytes (an override, a long `XDG_RUNTIME_DIR`, a multi-byte
  name) is refused before any bind with a typed error that gives the path, its length, the limit and the override,
  shown by a test with a path of 120 characters and one of 107 bytes made of multi-byte characters, and the default
  paths are shown to be short;
- the folder of the socket is private to the user (mode 700) and the socket file too; a folder or socket owned by
  someone else is refused;
- the daemon refuses to start when another one answers on the socket, removes a stale socket file that nobody answers
  on, removes its socket when it stops (`stop`, SIGTERM), and does not leave a process behind;
- `status` and `stop` work end to end with the native `gabier` against a started daemon, with the exit codes of
  `errors-reported-as-problem-objects`; with no daemon, `gabier status` says so and does not hang; a malformed line, an
  unknown method and a client that disconnects mid-request leave the daemon alive and answer with the problem object;
- the server logic is tested without a real socket where possible (streams), and with a real socket in a temporary
  directory for the paths above; nothing in `check` needs jdtls; the coverage stays at 100% and the CLI keeps no
  third-party dependency.

Depends on: errors-reported-as-problem-objects (the shape of an error), jdtls-adapter-starts-and-awaits-jdtls (both edit
the `daemon` module).

## Resolution

Merged as `6b0eb83`.

The first worker's report was lost with its supervisor session, so a second agent re-proved the ticket in the same
worktree. Its `bin/ci` (full): format, build, test (JaCoCo 100% and architecture tests), native and jdtls all passed;
each `Done when` point was shown by named tests and by the native `gabier status` / `stop` run (exit codes, 120-byte
`GABIER_SOCKET` refused, second daemon refused, stale socket removed, no process left).

Review (one pass): two BLOCKING findings, both fixed in the same commit with `bin/ci` green again (format 7s, build 5s,
test 85s, native 157s, jdtls 115s):
- a number with an absurd exponent (`1e99999999999`, `"id":1e999999999`) killed the connection with no answer; it now
  gets the `malformed-json` problem object (exponent limited to ±1000);
- deeply nested input (100,000 `[`) threw `StackOverflowError`; the nesting is now limited to 64 levels, with the same
  problem object. `DaemonServerTest` proves the daemon answers a normal `status` after each hostile line.

Suggestions, left open: no limit on the length of a line (`LineReader.java:25`, a client that never sends `\n` grows the
buffer until `OutOfMemoryError`); the uptime uses `Clock.systemUTC()`, so a clock set back makes `DaemonStatus` throw
and `gabier status` fail on a healthy daemon; `DaemonServer.open` leaves the listener and the socket file behind when
`guard.restrict` fails after `bind`.

Worth remembering: the transport is JSON-RPC 2.0, one object per line, as the issue chose. The skill
`add-daemon-method` was delivered with the mechanism and describes how to plug a method in.

Tokens:
- worker: first session unknown (its notification was lost); second agent 73083 (sum of its two notifications, 56832 + the resumed run)
- reviewer: 68343
- supervisor: 9146 output, 37700 input + cache write over 16 calls (cache read 999180), supervisor session over the
  period of the ticket (the resumption of the worktree, other work included); exchanges with the user: 0 messages
