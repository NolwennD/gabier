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

## Resolution

Commit e3669d3. `bin/ci` (worker, full, once on the state of the second commit): format 15s, build 5s, test 180s, native 187s, jdtls 536s, all passed; `ScenarioReplayTest` replayed alone, passed. Reviewed once: nothing blocking.

- Behaviour: `java/getRefactorEdit` with the command `extractField`, same flow as the constant. Initialisation choice: the command takes an optional first argument (`Field declaration`, `Current method`, `Class constructors`) that gabier does not send, so jdtls's default, **current method**, applies: `private String label;` at the top of the class and `label = "count: ";` before the statement holding the expression (`GetRefactorEditHandler`, `ExtractFieldRefactoring` of the jdtls clone). The field carries the name given (`JdtlsLanguageServerTest$ExtractingAField.answersOneEditOfTheFileAsItIsOnDiskWithTheNameAskedFor`; replay asks `label`, jdtls proposed `string`).
- Change to existing code: `JdtlsLanguageServer.leavesTheDeclarationAsItWas` checked the rename at the byte offset where jdtls introduced the name; for a field that offset is the assignment, below the declaration, so every valid name was refused. It now looks the line up by number in the renamed text (a rename adds no line) and requires it to end with the name and the original rest of the line. The extract-variable and extract-method corruption tests and replays stay green.
- Refusals: scenarios `extract-a-field-from-something-that-is-not-an-expression` and `extract-a-field-from-outside-a-class-body`, both replayed as `NOT_AN_EXPRESSION`; jdtls answers null, so the text is gabier's own.
- Review suggestions: all in issue `01-11-2026-2128-extract-constant-and-field-review-suggestions.md` (on hold).

Tokens (the batch of two issues shares these figures):
- worker: 345213 (`subagent_tokens`).
- reviewer: 86509 (`subagent_tokens`).
- supervisor: 13 calls, output 6710, input + cache write 39699 (cache read 844643); covers the supervisor session over the period of the batch (launch of the worker to now), other work included; exchanges with the user: 0 messages.
