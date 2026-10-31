# gabier extracts a variable from an expression

Opened: 31-10-2026 19:52

An agent turns an expression into a named local variable with `gabier extract-variable`, instead of editing the
statement by hand: the first of the `extract-*` commands of the Python driver (`extract-method`, `extract-constant` and
`extract-field` come after, one ticket each, on the shape set here). The input is a selection (file, start line and column,
end line and column) and the new name; the answer is the files changed and the unified diff, text and `--json`, with
`--dry-run`, like `rename` and `organize-imports`. A selection that is not an expression, or a name that is not valid, is a
refusal as a problem object, nothing written. Daemon method (skill `add-daemon-method`) and cli command.

**Care**: jdtls is configured, not fought (`.claude/rules/workflow.md`, "Value first"). Start by reading the Eclipse JDT LS
documentation and source: the `refactor.extract.variable` code action, what the client capabilities must declare for it, and
how the name is chosen (jdtls proposes its own name, then expects a rename). Write in the resolution which settings and
capabilities give the behaviour. If jdtls cannot take the name from the client, say in the resolution how the name is
applied and why, instead of a text substitution.

Done when:
- [ ] `gabier extract-variable <file> <start> <end> <name>` replaces the expression by `<name>` and declares it above, on disk;
      `--dry-run` writes nothing; shown on the fake language server (`GabierTest`, daemon tests on a real socket) and with
      a contract scenario replayed on the real jdtls (skill `add-scenario`).
- [ ] The variable carries the name the agent gave, not the one jdtls proposes (a test that fails if it does not).
- [ ] A selection that is not an expression, a name that is not a Java identifier, and a file outside the project are
      refusals with a problem object, nothing written.
- [ ] The files changed and the diff are in the answer, text and `--json`; README and usage text document it with an example.
- [ ] `bin/ci` green, coverage 100%.
