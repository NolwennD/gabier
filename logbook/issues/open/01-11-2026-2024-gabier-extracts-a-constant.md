# gabier extracts a constant from an expression

Opened: 01-11-2026 20:24

An agent turns an expression into a `static final` field with `gabier extract-constant`, instead of editing text: the
third of the `extract-*` commands, on the shape of `extract-variable` and `extract-method`
(`docs/commands/extract-variable.md`). `gabier extract-constant [--dry-run] <file> <line>:<column> <line>:<column> <name>`
(1-based, end excluded) extracts the expression into a constant called `<name>` and replaces the selection by it. The answer
is the files changed and the unified diff, text and `--json`; a refusal is a problem object, nothing written. Daemon method
(skill `add-daemon-method`), cli command, `docs/commands/extract-constant.md`, a line in `help` and in
`.claude/rules/dogfooding.md`.

**Care**: jdtls is configured, not fought. Start with `ripwire` and the JDT LS source (`java/getRefactorEdit`, command
`extractConstant`, clone at /home/nolwenn/dev/projects/eclipse.jdt.ls, read-only); write in the resolution which settings
and capabilities give the behaviour. Reuse the way `extract-variable` applies the name given (rename of the proposal).

Done when:
- [ ] `gabier extract-constant <file> <start> <end> <name>` extracts an expression into a constant with the name given, on
      disk; `--dry-run` writes nothing; shown on the fake language server (`GabierTest`, daemon tests on a real socket) and
      with a contract scenario replayed on the real jdtls (skill `add-scenario`).
- [ ] The constant carries the name the agent gave, not the one jdtls proposes (a test that fails if it does not).
- [ ] A selection that cannot be a constant (it uses a local variable) is refused with the jdtls message, nothing written.
- [ ] `help`, the command's document and the dogfooding rule list the command; `bin/ci` passes.
