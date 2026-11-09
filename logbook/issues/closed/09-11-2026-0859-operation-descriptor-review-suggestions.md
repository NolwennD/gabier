# The operation descriptor says what a second front end needs (review suggestions)

Opened: 09-11-2026 08:59

Tidy first for the MCP server (plan step 6): a tool is generated from this descriptor and run by `OperationRunner`, so what a second front end would trip on is fixed before it is built. The hold is lifted by the user's request to continue the MCP work (09-11-2026).

Done when:
- command-line words (`--wait`, `--timeout`, `--token`, `gabier organize-imports`) do not appear in the notes and refusals of `Operations.START`, `RENAME`, `RENAME_TOKEN` and `CLEANUP`: a tool built from the descriptor does not mention an option it lacks;
- the descriptor states the wire form of `files` and `cleanUps` (`Type.ARRAY` is declared, the daemon reads a newline-joined string, `docs/protocol.md` says a value is a string);
- `required` is used or removed (no parameter is optional today; `OperationsTest.requiresEveryParameter` pins it to true);
- `OperationParametersTest.usageLineOf` splits a usage of several lines, and `OperationsTest.countsEveryPositionFromOne` does not read every INTEGER as a position;
- the tests that refuse a malformed `Operation`, `Parameter` or `Command` assert the exception type and its message, not `RuntimeException` alone;
- `HexagonalArchTest` has a liveness test for the `CommandLine` exemption, like the one of the composition root.
- (review of the batch that added `OperationRunner` and moved the guide, PR 34) a position has its own type or flag: `OperationRunner.zeroBased` shifts every `Type.INTEGER`, so a later integer that is no position (a timeout) would be shifted and refused at 0;
- `OperationRunner` takes the numbers a JSON front end gives (`Long`, `Double` with no fraction), not `Integer` only, with a test; an empty element of a list is refused or documented (`List.of("a", "")` is sent as `"a\n"`);
- an element of `files` or `cleanUps` that holds a line break is covered by a command-line test (`gabier move` on a name with a line break): the refusal key `operation-invalid-values` is new and on no command's page;
- `InvalidValuesException` (package-private) is catchable by a second primary adapter, or `run` stops documenting it; a test runs every operation of `Operations.all()` but `status`, `start` and `stop` through `OperationRunner.kindOf`; a test pins the order of an unknown value together with a missing one.

## Resolution

Commit d273ad0 (PR #36, batch of three issues). Positions are typed apart from other integers (`Parameter`, `Parameters`), so `OperationRunner` shifts positions only; the runner takes numbers of any kind a JSON front end gives and a list with an empty element is refused; command-line words left the notes and refusals of `START`, `RENAME`, `RENAME_TOKEN` and `CLEANUP`; the wire form of `files` and `cleanUps` is stated in `docs/protocol.md`; the tests of the descriptor assert the type and the message of their exceptions; `usageLineOf` reads usage lines apart. The liveness test of the entry-point exemption was already merged (PR #35).

- The notes of `STATUS`, `MOVE`, `REFERENCES`, `ACTIONS` and `APPLY_ACTION` still hold command-line words: out of this issue's list, taken by `mcp-tools-read-well-to-an-agent`.
- `bin/ci` of the worker on 0b6b607: format, build, test, native (it runs `gabier mcp` of the built binary on `tools/list`, both handshakes, a parse error, then two `tools/call` against the fake-served daemon; check added to `bin/ci.d/40-native`), jdtls passed; release-install skipped (no changed file reaches `install.sh`). Check `bin-ci` of PR #36 green (9m56s).
- Review (neutral, once): nothing blocking. Findings: the empty text block of `diagnostics` on a clean file, command-line words in the descriptions of five tools, an unexpected exception not logged, `help mcp` refusals not telling the truth for a command whose calls ask the daemon (all in the issue `mcp-tools-read-well-to-an-agent`, ready); no wait or bound on a call while `ping` is not read, ids `1.0`/`1e2` rewritten, `ping` without `resultType`, tests of robustness, `file:""` sent as the working directory (all in the on-hold issue `mcp-keeps-answering-while-a-call-waits-and-reads-every-id`).
- Process: the worker used `sed` three times against the dogfooding rule (once in issue 1, twice in tests) and did not observe red first for every test of issue 2 (server and tests written together).

Tokens:
- worker: 276,134 (its completion notification).
- reviewer: 121,746.
- supervisor: 14 calls, output 14,086, input + cache write 353,721 (cache reads 2,385,854), covering the supervisor session from the launch of the worker (11:20 UTC) to the merge, other work included (the pull request on the entry-point layer and the opening of the MCP issues); exchanges with the user: 0 messages.
