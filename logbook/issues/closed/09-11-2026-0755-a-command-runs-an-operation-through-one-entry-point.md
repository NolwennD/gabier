# A command runs an operation through one entry point

Opened: 09-11-2026 07:55
Depends on: each-operation-names-its-parameters-once

Step 4 of the plan that prepares the cli for an MCP facade (structure only). Each command reads its words, builds the
parameter map, asks the daemon and decodes the answer. The application gets one entry point, `run(operation, named
values)`, that a second primary adapter calls too.

Done when:
- the application layer has `run(operation, named values)`: it checks the values against the descriptor (a required
  one missing, an unknown one), converts the positions from 1 to the daemon's 0, asks the `Daemon` port and returns the
  result decoded into its `protocol` type;
- every command that asks the daemon (`diagnostics`, `rename` both forms, `organize-imports`, `cleanup`, the four
  `extract-*`, `move-type-to-file`, `move`, `inline`, `create`, `references`, `actions`, `apply-action`) is
  reduced to "words to named values" and "result to output"; none builds a `Map.of(...)` of daemon parameters;
- started with `diagnostics`, `rename` and `extract-method`, to prove the shape before the others;
- the cli tests pass unchanged apart from their package; the refusal a command shows is the same as before;
- the resolution says whether the six-field `DaemonRefusal` is kept or replaced, and why;
- `bin/ci` is green.

## Resolution

Commit b3762a2 (PR #34). `OperationRunner.run(operation, named values)` in `eu.gabier.cli.application` checks the values against the descriptor (a required one missing, an unknown one, a wrong kind, a position below 1, a list element with a line break), converts the positions from 1 to 0 once (`zeroBased`), asks the `Daemon` port and returns an `OperationResult` (`Changed`, `Diagnosed`, `Referenced`, `Offered`) that holds the decoded protocol type. Every command that asks the daemon is reduced to words to named values and result to output; `diagnostics`, `rename` (both forms) and `extract-method` were converted first, the rest after. `Arguments.position` now keeps the numbers from 1 (the expectations of `ArgumentsTest` changed with it; the conversion is covered by `OperationRunnerTest.sendsAPositionFromZeroWhenItIsGivenFromOne`).

- Kept: `DaemonRefusal` with its six fields. The domain cannot see `protocol.Problem`, and the port must carry all six for `CommandLine.problemOf` to rebuild the problem; replacing it would be a second copy of `Problem` in the domain for no gain. The runner lets it through untouched.
- Wire form of `files` and `cleanUps`: the descriptor keeps `Type.ARRAY`; `run` takes a `List<String>` and joins it with a line break, which is what the daemon reads, so `docs/protocol.md` ("a value is a string") stays true. An element with a line break is refused with the new key `operation-invalid-values`, which no command can reach (`Move` and `Cleanup` keep their checks and answer `usage-error`): the MCP ticket must pick it up.
- Every INTEGER is a position (existing convention, documented); `required=false` is honoured by `run`. A new operation must be named in `kindOf`; the `add-daemon-method` skill says so.
- The tests of `OperationRunner` and `AskTheGuide` were written with their code in one step, so they were red only because the classes did not compile.
- Proof: `OperationRunnerTest` (`Asking`, `Decoding`, `Refusing`, the refusal of the daemon passing through); the command tests over a real socket keep the wire JSON.
- `bin/ci` of the worker on the two commits as first made: format, build, test, native, jdtls passed; release-install skipped (no changed file reaches `install.sh`). The worker then rewrote commit 1 to add one skill fix (`.claude/skills/add-daemon-method/SKILL.md`, a `.md` file) and did not rerun `bin/ci`; the check `bin-ci` of PR #34 (13m22s) ran on the final commits and is green.
- Review (neutral, once): nothing blocking. Seven suggestions (a position type of its own, numbers a JSON front end gives, empty list element, the new refusal key `operation-invalid-values` without a command-line test, `kindOf` against `Operations.all()`, a package-private exception that a second adapter cannot catch, order of unknown and missing values) added to the on-hold issue `operation-descriptor-review-suggestions`.

Tokens:
- worker: 282,127 (its completion notification).
- reviewer: 94,858.
- supervisor: 18 calls, output 11,482, input + cache write 31,332 (cache reads 2,527,212), covering the supervisor session from the launch of the worker (08:16 UTC) to the merge, other work included; exchanges with the user: 0 messages.
