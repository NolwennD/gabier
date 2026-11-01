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

## Resolution

Commit 95f3926. `bin/ci` (worker, full, once on the state of the second commit): format 15s, build 5s, test 180s, native 187s, jdtls 536s, all passed; `ScenarioReplayTest` replayed alone, passed. Reviewed once: nothing blocking.

- Behaviour: `java/getRefactorEdit` with the command `extractConstant`, no client capability, no `JdtlsSettings` or `JdtlsCapabilities` entry. jdtls answers the edit and a `java.action.rename` command; the adapter applies the edit in memory, renames through `textDocument/rename`, and folds the two edits into one, as `extract-variable` does. The constant carries the name given (`JdtlsLanguageServerTest$ExtractingAConstant.answersOneEditOfTheFileAsItIsOnDiskWithTheNameAskedFor`; replay asks `COUNT_LABEL`, jdtls proposed `COUNT2`).
- Divergence from the issue: for a selection that cannot be a constant jdtls answers null, with no message to relay. The refusal text is gabier's own (`language-server-not-extractable`, `LanguageServerException.notAConstant`), shown by the scenario `extract-a-constant-that-uses-a-local-variable`.
- Scenarios replayed on jdtls: `extract-a-constant`, `extract-a-constant-that-uses-a-local-variable`, invalid name, server still importing, file outside the project.
- Review suggestions: all in issue `01-11-2026-2128-extract-constant-and-field-review-suggestions.md` (on hold).

Tokens (the batch of two issues shares these figures):
- worker: 345213 (`subagent_tokens`).
- reviewer: 86509 (`subagent_tokens`).
- supervisor: 13 calls, output 6710, input + cache write 39699 (cache read 844643); covers the supervisor session over the period of the batch (launch of the worker to now), other work included; exchanges with the user: 0 messages.
