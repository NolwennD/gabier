# The operation descriptor says what a second front end needs (review suggestions)

Opened: 09-11-2026 08:59
On hold: review suggestions of the batches of PR 33 (command line, rendered results, named operations) and PR 34 (entry point, guide). The first two points and those on the runner matter when the MCP adapter (plan step 6) is built; lift the hold then.

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
