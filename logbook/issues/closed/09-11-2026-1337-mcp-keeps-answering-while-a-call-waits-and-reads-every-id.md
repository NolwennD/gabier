# `gabier mcp` keeps answering while a call waits and reads every id (review findings of PR 36)

Opened: 09-11-2026 13:37

The hold is lifted (09-11-2026) after a probe of the installed build on a cold daemon, in a real session of Claude Code 2.1.319 (modern era, connected in 306 ms, tools named `mcp__gabier__<operation>`): a `diagnostics` call sent at about 19:53:38 was answered at 19:54:28, 50 s later, and a `status` (sent at about 19:53:42) and a `ping` (about 19:53:45) sent during that wait were answered only then, in the same second, because the server reads one message at a time. The agent sees nothing during the import: the progress of jdtls ("importing the project for 15s: Build model ...") goes to the standard error, which a host logs and does not show.

Done when:
- a `tools/call` that waits for the daemon (start, jdtls import, a slow answer) does not stop the server from reading and answering `ping` meanwhile, and a call has a bound after which it ends as a failed call (`isError: true`, the problem `daemon-timeout` or the key the cli already has);
- a JSON-RPC `id` is echoed exactly as received: `1.0` and `1e2` are not rewritten, and an id the server cannot hold (above `Long.MAX_VALUE`) is answered, not dropped;
- `ping` carries `resultType: "complete"` in the modern era if the 2026-07-28 text says so (checked against the specification);
- tests for what only a regression would show: the `status` tool with no daemon (`isError`, `daemon-not-running`), a line of 1 MiB or more, EOF in the middle of a line, invalid UTF-8, an array nested deeper than 64;
- `gabier move ""` and a tool call with `"file":""` are refused as an invalid value, not sent as the working directory;
- `bin/ci` is green.
- (review of PR 37) `guide` and calls with invalid arguments do not wait for the warm-up (`McpServer.toolsCall`, `warmUp.awaitEnd()`): only the operations that ask the daemon do; a stuck `start` must not hang the `guide` tool;
- an `Error` in the warm-up thread (`NoClassDefFoundError`, `UnsatisfiedLinkError` when the native image spawns a process) does not end the server: `WarmUp.startQuietly` catches it as a failed start, or `awaitEnd` does not throw it;
- `mcp-instructions.md` lists `rename-token` among the tools that change code, `McpInstructionsTest` fails when a tool that changes code is missing from the text, and the text says `status` starts no daemon but a handshake has already started one;
- `InvalidValuesException` keeps its guard against swapped strings (a small `OperationName` record, or the `Operation` overload for the operations).
- while a call waits for the daemon, an agent can learn that jdtls is importing the project and for how long: the call sends `notifications/progress` (with the state line of jdtls: starting, launching, importing and its last task, and the seconds so far) when the request carries a `progressToken`, and `status`, called on the side, answers at once with the same state (`jdtlsProgress`); a test drives a call against a fake daemon that stays in "starting" and shows both.

## Resolution

Commit `d4a3479`. `bin/ci` of the worker on the last commit of the branch: format, build, test, native and jdtls
passed, release-install and release-notes skipped (no changed file reaches them); the check `bin-ci` of the pull request
#41 passed on the pushed head.

- `ping` and `status` are answered while a call waits; the call is bound to 11 minutes (`daemon-timeout`); a
  `notifications/cancelled` ends it; a call with a `progressToken` gets progress notices every 5 s; ids are echoed as
  received; the other points (robustness cases, empty path, warm-up scope, instructions) are tested as listed in the
  worker's report.
- Reviewed once. BLOCKING, fixed in the commit: with a progress token, the notice asked the daemon for `status` on the
  thread that enforces the bound, so a daemon that never answers kept the bound from firing and end of input hung. The
  status question now runs in its own thread, limited to 2 s, one at a time per call, and the bound is checked first
  (`WaitingCallsTest.GivingUp...DoesNotAnswerTheQuestionOfItsNoticesEither`). The worker saw the old code hang but did not
  keep a red report of it.
- Also fixed, a real bug in scope: concurrent first calls each launched a daemon (`DaemonLauncher.connect` now locks and
  re-checks the socket).
- Suggestions of the review are one open issue (symlinks in the path of a new file, a launch lock per socket, a duplicate
  id on an immediate answer, `status` right after the handshake).
- Process: the worker edited by hand, no gabier command applied (new classes and signatures, no rename or move).

Tokens:
- worker: 289,659 (both issues, one batch)
- reviewer: 119,711
- supervisor: 93 calls, 76,695 output, 286,842 input + cache write (cache reads 20,395,522), over the period of this batch,
  other work included; exchanges with the user: 9 messages.
