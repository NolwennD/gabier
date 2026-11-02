# gabier rename takes its position as line:column

Opened: 02-11-2026 20:23

Met while working `move-rewrites-references-in-other-modules`: `gabier rename` takes the position as two operands
`<line> <column>`, while every other command and `.claude/rules/dogfooding.md` write `<line>:<column>`. An agent that
writes `gabier rename <file> 125:25 writeFiles` gets a generic `usage-error` that does not say which operand is wrong.

Done when:
- `gabier rename <file> <line>:<column> <new name>` renames as the other commands take their position; the help, `docs/`
  and the rules say so; whether the two-operand form stays is said in the `## Resolution`;
- a malformed position is refused with a message that names the operand and the expected form;
- `bin/ci` passes.
