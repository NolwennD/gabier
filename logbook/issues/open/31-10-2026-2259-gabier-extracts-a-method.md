# gabier extracts a method from statements

Opened: 31-10-2026 22:59

An agent turns a block of statements, or an expression, into a method with `gabier extract-method`, instead of cutting and
pasting text: the second of the `extract-*` commands of the Python driver, on the shape of `extract-variable`
(`docs/commands/extract-variable.md`). `gabier extract-method [--dry-run] <file> <line>:<column> <line>:<column> <name>`
(1-based, end excluded) extracts the selection into a method called `<name>`, with the parameters and the return value jdtls
works out, and replaces the selection by the call. The answer is the files changed and the unified diff, text and `--json`;
a refusal is a problem object, nothing written. Daemon method (skill `add-daemon-method`) and cli command.

**Care**: jdtls is configured, not fought (`.claude/rules/workflow.md`). Read the JDT LS source and documentation first
(`java/getRefactorEdit` with the command `extractMethod`; the clone is at /home/nolwenn/dev/projects/eclipse.jdt.ls, at the
tag pinned by gabier, read-only), and write in the resolution which settings and capabilities give the behaviour. jdtls
names the method itself and expects the client to rename it (as for the variable): reuse the way `extract-variable` applies the
name (an in-memory overlay, `textDocument/rename`, one folded edit) if the documentation shows no better way.

Done when:
- [ ] `gabier extract-method <file> <start> <end> <name>` extracts statements, and an expression, into a method with the
      name given, on disk; `--dry-run` writes nothing; shown on the fake language server (`GabierTest`, daemon tests on a real
      socket) and with contract scenarios replayed on the real jdtls (skill `add-scenario`): statements that use a local
      variable (a parameter appears), statements that assign one (a return value appears), an expression.
- [ ] The method carries the name the agent gave, not the one jdtls proposes (a test that fails if it does not).
- [ ] A selection jdtls cannot extract (half a statement, a `return` of a branch), a name that is not a Java identifier and a
      file outside the project are refusals with a problem object, nothing written; each is a scenario replayed on the real
      jdtls. What happens with a name another method of the class already has is replayed too, and the resolution says which
      refusal comes back and who produces it (jdtls or a check of gabier).
- [ ] The page `docs/commands/extract-method.md`, the line of the README list and the row of `docs/protocol.md` are added
      (skill `add-daemon-method`); `.claude/rules/dogfooding.md` names the command. `bin/ci` green, coverage 100%.

Coupled with `31-10-2026-2259-gabier-has-a-help.md` (both touch the usage text of `Gabier` and `UsageException`): they run in parallel on the
user's decision; the second one to be merged rebases over the first.
