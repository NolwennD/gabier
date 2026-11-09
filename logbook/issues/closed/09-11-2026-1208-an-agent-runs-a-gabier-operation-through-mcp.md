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

## Resolution

Commit 0b6b607 (PR #36). `tools/call` runs `OperationRunner.run` over the daemon port built by `Gabier`: success gives the text of `ResultText` in `content` and the result as `--json` prints it in `structuredContent`; a refusal, an invalid value (`operation-invalid-values`) or a daemon that cannot be reached is `isError: true` with the problem and the help pointer (`Problems`, moved to `application` so any front end uses it; `Capabilities.commandOf` names the command of an operation, `rename-token` pointing to `rename`); an unknown tool, a `name` that is no text or `arguments` that is no object is `-32602`; stdout carries only answers (the start notice and logs go to stderr), proven by `CallingAToolTest` against the scripted daemon for `diagnostics`, `rename` with `dryRun`, `move` with `files` as an array and a refusal, each comparing `content` with the text of the command line.

- Choices: `status` is run by the runner (new kind `REPORTED`, `daemon.askRunning`, so it starts no daemon), reversing the old test that said `status` is not served by `run`; relative paths of `file` and `files` are made absolute from the working directory of the server, as the command line does, the parameters picked by name (`ToolCalls.FILES`); a `null` argument counts as not given; the MCP adapter says `nothing to change` for any file-changing operation with an empty result, the command line only for `organize-imports` and `cleanup`, and `cleanup` now prints the warnings of an empty change as `organize-imports` did.
- Out of scope but delivered: `docs/commands/mcp.md`, planned for the next issue; the review found it correct.
- A call that reaches no daemon starts it once (`CallingAToolTest.aCallThatReachesNoDaemonStartsTheDaemonOfThePathOnce`).
- `bin/ci` of the worker on 0b6b607: format, build, test, native (it runs `gabier mcp` of the built binary on `tools/list`, both handshakes, a parse error, then two `tools/call` against the fake-served daemon; check added to `bin/ci.d/40-native`), jdtls passed; release-install skipped (no changed file reaches `install.sh`). Check `bin-ci` of PR #36 green (9m56s).
- Review (neutral, once): nothing blocking. Findings: the empty text block of `diagnostics` on a clean file, command-line words in the descriptions of five tools, an unexpected exception not logged, `help mcp` refusals not telling the truth for a command whose calls ask the daemon (all in the issue `mcp-tools-read-well-to-an-agent`, ready); no wait or bound on a call while `ping` is not read, ids `1.0`/`1e2` rewritten, `ping` without `resultType`, tests of robustness, `file:""` sent as the working directory (all in the on-hold issue `mcp-keeps-answering-while-a-call-waits-and-reads-every-id`).
- Process: the worker used `sed` three times against the dogfooding rule (once in issue 1, twice in tests) and did not observe red first for every test of issue 2 (server and tests written together).

Tokens:
- worker: 276,134 (its completion notification).
- reviewer: 121,746.
- supervisor: 14 calls, output 14,086, input + cache write 353,721 (cache reads 2,385,854), covering the supervisor session from the launch of the worker (11:20 UTC) to the merge, other work included (the pull request on the entry-point layer and the opening of the MCP issues); exchanges with the user: 0 messages.
