# The MCP server answers questions to the guide

Opened: 09-11-2026 12:08
Depends on: gabier-mcp-carries-instructions-and-a-warm-up

Step 7. An agent that does not know which tool does a refactoring asks the `guide` tool, as it would run
`gabier help --for="<task>"`, and learns whether jdtls does it and with which tool.

Done when:
- `tools/list` has a tool `guide` with a required string parameter `task` (the task, in English), read-only, whose
  description says when to call it; `tools/call` answers with `AskTheGuide` and the guide of the cli, in the same words
  as `gabier help --for` for the same task, with the command names as tool names where the text says a command;
- a task the guide does not know is answered as `help --for` does (nothing matches), not as an error; a missing or blank
  `task` is `isError: true` as an invalid value;
- a test per case above (a known task, an unknown task, a blank task), and one that checks that every tool the guide
  names exists in `tools/list`;
- `docs/commands/mcp.md` and `mcp-instructions.md` name the tool; the skill `extend-guide` says that a guide entry
  now serves both the command line and MCP;
- `bin/ci` is green.

## Resolution

Commit 783cf90 (PR #37). The tool `guide` (required string `task`, read-only) answers with `AskTheGuide` and the guide of the cli, in the words of `gabier help --for` for the same task, as a tool: the text comes from `application/GuideText`, now shared with `HelpText`, then `mcp/ToolWords` rewrites it (`gabier <tool>` to `<tool>`, `gabier <command> --token` to `<command>-token`, any other `--kebab-option` to its camelCase boolean parameter set to true, as `dryRun=true`). A blank `task` is `isError: true` with `operation-invalid-values`; a task the guide does not know is answered as `help --for` does.

- Modelling: `guide` is not an `Operation` (it asks no daemon): `Tools.GUIDE` is the one tool declared by hand in the primary adapter, `McpServer.Backend` carries the `Guide`; the word MCP appears only in the adapter; `InvalidValuesException` takes the operation name as a String, so the application needs no fake operation (the guard against swapped strings is in the on-hold issue).
- Proof: `GuideToolTest`, `ToolWordsTest`, `GuideTextTest`, `GuideThroughMcpTest` (the task of every section of `improve.md`: no `--word` left, every `name=true` a boolean parameter of some tool, the answer by token names `rename-token`, every tool named exists in `tools/list`); `gabier help --for` and `help guide` unchanged.
- Docs and skills: `docs/commands/help.md`, `docs/cli.md`, `README.md`, `docs/commands/mcp.md`, `.claude/skills/extend-guide/SKILL.md` (a guide entry now serves the command line and MCP), `verify` and `add-daemon-method`.
- `bin/ci` of the worker on 783cf90 (after the fix of the blocking finding): format, build, test, native (it runs `server/discover`, the earlier-era `initialize`, `tools/list`, a `guide` call and a blank `guide` call on the built `gabier mcp`, requires a phrase of the instructions and exactly one warm-up failure line on stderr), jdtls passed; release-install skipped. The first check of PR #37 went red on `StartingJdtlsTest.aWaitThatIsInterruptedIsAConnectionFailureAndKeepsTheInterruption` (untouched by the branch; issue `an-interrupted-wait-test-fails-on-ci-at-random`); the check `bin-ci` on the amended branch is green (10m32s).
- Review (neutral, once): one blocking finding, fixed by the worker in this commit set (the `guide` answer held `--dry-run`, `--token` and `--include-declaration`, which an agent cannot pass to a tool; the rewrite is in `mcp/ToolWords`, with a test over every section of the guide). Suggestions (guide and invalid calls wait for the warm-up, an `Error` in the warm-up thread, `rename-token` missing from the instructions, Strings of `InvalidValuesException`) added to the on-hold issue `mcp-keeps-answering-while-a-call-waits-and-reads-every-id`.
- Process: the worker used `sed` once for one line of a test, against the dogfooding rule.

Tokens:
- worker: 125,174 (cumulative figure of its last completion notification; one resume).
- reviewer: 110,428.
- supervisor: 32 calls, output 20,658, input + cache write 45,649 (cache reads 7,113,525), covering the supervisor session from the launch of the worker (12:44 UTC) to the merge, other work included (the first manual try of `gabier mcp`, the opening of the issues of the findings, the wait of two CI runs); exchanges with the user: 0 messages.
