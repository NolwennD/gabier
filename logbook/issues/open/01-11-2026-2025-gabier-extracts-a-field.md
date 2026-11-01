# gabier extracts a field from an expression

Opened: 01-11-2026 20:25
Depends on: 01-11-2026-2024-gabier-extracts-a-constant.md

An agent turns an expression into a field of the class with `gabier extract-field`, instead of editing text: the fourth
of the `extract-*` commands. `gabier extract-field [--dry-run] <file> <line>:<column> <line>:<column> <name>` (1-based, end
excluded) extracts the expression into an instance field called `<name>`, initialised where jdtls puts it, and replaces the
selection by the field. The answer is the files changed and the unified diff, text and `--json`; a refusal is a problem object,
nothing written. Daemon method, cli command, `docs/commands/extract-field.md`, a line in `help` and in
`.claude/rules/dogfooding.md`.

**Care**: as for `extract-constant`: `ripwire` and the JDT LS source first (command `extractField` of
`java/getRefactorEdit`); if jdtls offers a choice of initialisation (field declaration, constructor, current method), read
its documentation, take the default it proposes and write the choice in the resolution.

Done when:
- [ ] `gabier extract-field <file> <start> <end> <name>` extracts an expression into a field with the name given, on disk;
      `--dry-run` writes nothing; shown on the fake language server and with a contract scenario replayed on the real jdtls.
- [ ] The field carries the name the agent gave, not the one jdtls proposes (a test that fails if it does not).
- [ ] A selection outside a class body, or that jdtls refuses, is refused with the jdtls message, nothing written.
- [ ] `help`, the command's document and the dogfooding rule list the command; `bin/ci` passes.
