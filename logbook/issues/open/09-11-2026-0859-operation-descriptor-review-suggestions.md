# The operation descriptor says what a second front end needs (review suggestions)

Opened: 09-11-2026 08:59
On hold: review suggestions of the batch that moved the command line, rendered results and named the operations (PR 33). The first two matter when the MCP adapter (plan step 6) is built; lift the hold then. The third is covered by the issue "a command runs an operation through one entry point".

Done when:
- command-line words (`--wait`, `--timeout`, `--token`, `gabier organize-imports`) do not appear in the notes and refusals of `Operations.START`, `RENAME`, `RENAME_TOKEN` and `CLEANUP`: a tool built from the descriptor does not mention an option it lacks;
- the descriptor states the wire form of `files` and `cleanUps` (`Type.ARRAY` is declared, the daemon reads a newline-joined string, `docs/protocol.md` says a value is a string);
- `required` is used or removed (no parameter is optional today; `OperationsTest.requiresEveryParameter` pins it to true);
- `OperationParametersTest.usageLineOf` splits a usage of several lines, and `OperationsTest.countsEveryPositionFromOne` does not read every INTEGER as a position;
- the tests that refuse a malformed `Operation`, `Parameter` or `Command` assert the exception type and its message, not `RuntimeException` alone;
- `HexagonalArchTest` has a liveness test for the `CommandLine` exemption, like the one of the composition root.
