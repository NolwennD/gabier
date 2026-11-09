# `gabier mcp` carries instructions, a warm-up and its documentation

Opened: 09-11-2026 12:08
Depends on: an-agent-runs-a-gabier-operation-through-mcp

Step 6, third part. A host that connects to `gabier mcp` gets the guidance of an agent on how to use gabier, jdtls is
started before the first call, and a person can find how to add the server to their host.

Done when:
- `instructions` (of `server/discover` and of the `initialize` result) come from a resource
  `mcp-instructions.md` next to `agents.md` (what an agent must know: use the tools instead of editing Java as text,
  positions from 1, `dryRun` first on a change across files, `diagnostics` after a change, the `guide` tool before
  editing by hand), declared in the native-image resource configuration, with a test like `AgentInstructionsTest`
  (the file exists, is not blank, names only tools that exist);
- the first `server/discover` or `initialize` starts the daemon in the background (`start`, as `gabier start` does), so
  that jdtls imports the project while the host loads the tools; a failure to start is logged on stderr and does not
  fail the handshake;
- `docs/commands/mcp.md` (the command, the tools, the two eras, the transport, errors) and a line of the README that
  gives the command to add the server to a host (`claude mcp add gabier -- gabier mcp`, and the `.mcp.json` form;
  verify the syntax in the documentation of Claude Code); `docs/protocol.md`, `docs/cli.md` and the skills
  `add-daemon-method` (a new operation is a new tool, by itself) and `verify` are brought up to date;
- verified by hand on the installed build, reported in the resolution: `gabier mcp` fed a `server/discover`, a
  `tools/list` and a `tools/call` of `diagnostics` on a broken file of this repository by `printf`;
- `bin/ci` is green.

## Resolution

Commit 8a9d345 (PR #37). `mcp-instructions.md` is the `instructions` of both handshakes (declared in the native-image resource configuration, proven present in the image by the native stage); `McpInstructionsTest` checks that every tool it names is in `tools/list`. `WarmUp` starts the daemon in the background on the first `server/discover` or `initialize`: the handshake never waits or fails on it, a failure is logged once on stderr, and a `tools/call` waits for the start so that the daemon is not launched twice (`WarmUpTest`, `ServingMcpTest`). `docs/commands/mcp.md` and the README give the way to add the server to a host; the syntax of `claude mcp add` was checked against the documentation of Claude Code, not from memory.

- Hand check by the worker, on a scratchpad install with the real jdtls importing the worktree: `server/discover` returned the instructions; `tools/call` of `diagnostics` on a throwaway broken file gave `missing cannot be resolved to a variable` as text and `structuredContent`, `isError: false`. The check on the host itself (`claude mcp add`, `/mcp`) is not done yet.
- Not settled: whether an agent reads the instructions at all in every host (a host may ignore them).
- `bin/ci` of the worker on 783cf90 (after the fix of the blocking finding): format, build, test, native (it runs `server/discover`, the earlier-era `initialize`, `tools/list`, a `guide` call and a blank `guide` call on the built `gabier mcp`, requires a phrase of the instructions and exactly one warm-up failure line on stderr), jdtls passed; release-install skipped. The first check of PR #37 went red on `StartingJdtlsTest.aWaitThatIsInterruptedIsAConnectionFailureAndKeepsTheInterruption` (untouched by the branch; issue `an-interrupted-wait-test-fails-on-ci-at-random`); the check `bin-ci` on the amended branch is green (10m32s).
- Review (neutral, once): one blocking finding, fixed by the worker in this commit set (the `guide` answer held `--dry-run`, `--token` and `--include-declaration`, which an agent cannot pass to a tool; the rewrite is in `mcp/ToolWords`, with a test over every section of the guide). Suggestions (guide and invalid calls wait for the warm-up, an `Error` in the warm-up thread, `rename-token` missing from the instructions, Strings of `InvalidValuesException`) added to the on-hold issue `mcp-keeps-answering-while-a-call-waits-and-reads-every-id`.
- Process: the worker used `sed` once for one line of a test, against the dogfooding rule.

Tokens:
- worker: 125,174 (cumulative figure of its last completion notification; one resume).
- reviewer: 110,428.
- supervisor: 32 calls, output 20,658, input + cache write 45,649 (cache reads 7,113,525), covering the supervisor session from the launch of the worker (12:44 UTC) to the merge, other work included (the first manual try of `gabier mcp`, the opening of the issues of the findings, the wait of two CI runs); exchanges with the user: 0 messages.
