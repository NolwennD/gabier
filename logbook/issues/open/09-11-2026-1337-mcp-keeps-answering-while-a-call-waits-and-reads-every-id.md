# `gabier mcp` keeps answering while a call waits and reads every id (review findings of PR 36)

Opened: 09-11-2026 13:37

The hold is lifted (09-11-2026) after a probe of the installed build on a cold daemon, in a real session of Claude Code 2.1.319 (modern era, connected in 306 ms, tools named `mcp__gabier__<operation>`): a `diagnostics` call sent at 19:53:42 was answered at 19:54:28, 51 s later, and a `status` and a `ping` sent during that wait were answered only then, in the same second, because the server reads one message at a time. The agent sees nothing during the import: the progress of jdtls ("importing the project for 15s: Build model ...") goes to the standard error, which a host logs and does not show.

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
