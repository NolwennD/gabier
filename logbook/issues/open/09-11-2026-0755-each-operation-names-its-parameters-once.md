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
