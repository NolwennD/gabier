# An agent runs a gabier operation through MCP

Opened: 09-11-2026 12:08
Depends on: gabier-mcp-answers-the-handshake-and-lists-the-tools

Step 6, second part. Once the tools are listed, an agent can call one: it reads the errors of a Java file, renames a
symbol or extracts a method through `gabier mcp`, with the same results as the command line.

Done when:
- `tools/call` runs the operation with `OperationRunner.run` and the daemon port (a `SocketDaemon` built as `Gabier` does,
  the entry point keeps wiring the adapters); success gives the text of `ResultText` in `content` (type `text`) and the
  result of the daemon as it gave it in `structuredContent`; a result with no change says so as the command line does;
- a refusal of the daemon (`DaemonRefusal`), an invalid value (`operation-invalid-values` and the other refusals of the
  runner) and an unreachable daemon are results with `isError: true` holding the problem (title, detail, key, the
  parameters) and the pointer to `gabier help` that the command line gives; an unknown tool or arguments that are not
  an object are protocol errors (`-32602`); no exception ends the server;
- nothing is printed on stdout but the answers: the start notice (`WaitNotice`) and every log go to stderr; a test runs a
  call against the scripted daemon of the cli tests, for `diagnostics`, `rename` (`--dry-run` equivalent `dryRun`),
  `move` (`files` as an array) and a refusal, and compares the `content` with the text the command line prints for the
  same case;
- a call that reaches no daemon starts it as the command line does (`DaemonLauncher`), once;
- `bin/ci` is green.
