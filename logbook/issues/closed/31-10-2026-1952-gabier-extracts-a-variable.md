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
- [x] `gabier extract-variable <file> <start> <end> <name>` replaces the expression by `<name>` and declares it above, on disk;
      `--dry-run` writes nothing; shown on the fake language server (`GabierTest`, daemon tests on a real socket) and with
      a contract scenario replayed on the real jdtls (skill `add-scenario`).
- [x] The variable carries the name the agent gave, not the one jdtls proposes (a test that fails if it does not).
- [x] A selection that is not an expression, a name that is not a Java identifier, and a file outside the project are
      refusals with a problem object, nothing written.
- [x] The files changed and the diff are in the answer, text and `--json`; README and usage text document it with an example.
- [x] `bin/ci` green, coverage 100%.

## Resolution

Merged as `1193a79`. An agent turns an expression into a local variable with
`gabier extract-variable [--dry-run] <file> <line>:<column> <line>:<column> <name>` (1-based positions, end excluded; text
and `--json`; refusals as problem objects, nothing written). New error key `language-server-not-an-expression`.

How jdtls is configured (source: `RefactorProposalUtility.getExtractVariableProposal`), no workaround of the text: no
setting and no capability. The request is `java/getRefactorEdit` (`command: "extractVariable"`), which needs no client
capability (`advancedExtractRefactoringSupport` only changes the code action path, not used). `JdtlsSettings` and
`JdtlsCapabilities` are unchanged. **jdtls cannot take the name from the client**: it always calls
`setTempName(guessTempName())` (it proposed `string` on the scenario). So the name is applied the way the ticket allowed,
by a rename and not by a text substitution: the edit of jdtls is applied in memory and sent with `didOpen` (never written
to disk), `textDocument/rename` renames the variable to the agent's name, `didClose`, and both edits are folded into one
`TextEdit` on the on-disk text (`SourceText.replacing`). When the name asked for is the one jdtls guessed, the rename and the
overlay are skipped. `java.codeGeneration.addFinalForNewDeclaration` (default none) would make the variable final: left alone.

Proofs (worker's full `bin/ci`: format 8s, build 3s, test 118s, native 151s, jdtls 329s; no stage skipped; tests: cli 140,
core 322, daemon 486, protocol 326, shared 236, architecture 19, 0 failures; coverage 100%, Checkstyle green):
- Extraction on disk, dry run writes nothing: `ExtractVariableTest`, `ExtractVariableMethodTest`, `DaemonServerTest` (real
  socket), `GabierTest$ExtractingAVariable`, the native stage `extractVariableWithTheFakeServer`, and the scenario
  `extract-a-variable` replayed on the real jdtls (matches exactly, the result compiles).
- The agent's name, not jdtls's: `JdtlsLanguageServerTest$ExtractingAVariable` (jdtls proposes `string`, the result is
  `text` or `joined`; fails if the rename is skipped) and the replay (`joined`).
- Refusals (not an expression, invalid name `1st`, file outside the project, server still importing): the four scenarios
  `EXTRACT_VARIABLE_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION`, `..._TO_AN_INVALID_NAME`, `..._OUTSIDE_THE_PROJECT`,
  `..._WHILE_THE_SERVER_IMPORTS`, replayed on the real jdtls.
- Worker's notes: some code was written before its test was run red (the tests were green on their first run); dogfooding
  limited to `gabier diagnostics` on a private daemon (no `rename` or `organize-imports`).

Review (neutral reviewer, once): nothing blocking. Suggestions, all in `JdtlsLanguageServer` and the scenarios:
- an `errorMessage` answer of jdtls with no edit becomes an internal error, not `not-an-expression`; the README claims a
  statement and a `void` call are refused, with no scenario behind it; no replay of a name equal to jdtls's guess nor of a
  name colliding with a local: issue `31-10-2026-2040-extract-variable-refuses-what-jdtls-refuses.md`;
- CRLF, BOM, non-BMP untested, and no stale-file check between the read and the write: issue
  `31-10-2026-2040-extract-variable-keeps-crlf-bom-and-concurrent-edits.md` (on hold).

Tokens:
- worker: 77447 (one agent; its two notifications, 72675 interim then 77447, are cumulative)
- reviewer: 96681
- supervisor: 30 calls, 18622 output, 56295 input + cache write (cache reads 2349842), exchanges with the user: 3 messages.
  Measured over the session from the launch of the worker to the merge; it includes the status talk, the diagnosis of the
  Gradle processes and other work of the period.
