# Each operation names its parameters once

Opened: 09-11-2026 07:55
Depends on: the-text-of-a-result-is-a-pure-function-of-the-result

Step 3 of the plan that prepares the cli for an MCP facade (structure only). The parameters of a daemon operation are
written three times (the usage line, the table of `docs/protocol.md`, the `Map.of(...)` of each command), and an MCP
`inputSchema` would be a fourth copy. They are named once, in a descriptor.

Done when:
- a type of `eu.gabier.cli.domain` (no protocol type, no JSON) describes each operation the daemon serves: the method
  name, summary, notes, refusals, and its typed parameters (name, type string / integer / boolean / array, required,
  description; a position is counted from 1 on this side);
- `Command` keeps only the usage and the example of the command line and points to its operation(s) (`rename` has two
  methods, `rename` and `rename-token`); `gabier help` and `gabier help <command>` print exactly what they print today;
- a test fails when the parameters of an operation, the parameters of its usage line and the row of
  `docs/protocol.md` disagree (show it with one parameter removed from each place in turn);
- `.claude/skills/add-daemon-method/SKILL.md` and `docs/cli.md` say where a new operation is declared;
- `bin/ci` is green.

## Resolution

Commit 97a62bd (PR #33). Domain types `Operation`, `Parameter` (string, integer, boolean, array; required; description), `Operations` (19 operations, `all()`) and `Refusals` (the daemon-side refusal strings that left the primary `Refusal`). `Command` keeps the usage and the example and points to its operations; its summary, notes and refusals are computed from them, with the refusals of the command line appended. `help` and `init-agent` are not daemon operations and keep their own. The merge of the refusals of several operations (`rename`, `rename-token`) keeps today's order, pinned by `CommandTest`.

- Help output identical (same snapshots as the first issue, again after this commit).
- `OperationParametersTest`: the parameters of the descriptor, of the usage line and of the row of `docs/protocol.md` must agree; removing `newName` from each place in turn fails it (also run by hand on the real doc, then reverted). Every table row has an operation and every operation is run by a command. The path of the doc reaches the test as the property `gabier.docs.protocol`.
- Choices: positions counted from 1 are stated in the description (checked by `OperationsTest`), with no flag; every parameter is required, as the protocol says; the `Map.of` call sites are left to the next issue.
- `add-daemon-method` step 5 and `docs/cli.md` say where an operation is declared.
- `bin/ci` of the worker, run once on 97a62bd: format, build, test, native, jdtls passed; release-install skipped (no changed file reaches `install.sh`). Check `bin-ci` of PR #33 green (11m56s).
- Review (neutral, once): nothing blocking. Seven suggestions, grouped in the on-hold issue `operation-descriptor-review-suggestions` (command-line words in the descriptor notes, wire form of the array parameters, dead `required` flag, tests of the descriptor, liveness test of the new exemption); the fourth copy of the parameter names in each `Map.of` is the subject of the next issue of the plan.

Tokens:
- worker: 335,623 (cumulative figure of its last completion notification; one resume).
- reviewer: 102,227.
- supervisor: 25 calls, output 17,642, input + cache write 32,176 (cache reads 2,821,962), covering the supervisor session from the launch of the worker (06:55 UTC) to the merge, other work included; exchanges with the user: 0 messages.
