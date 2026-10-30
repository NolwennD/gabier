# The jdtls adapter renames a symbol

Opened: 30-10-2026 10:30

The jdtls adapter implements the rename of the port: it asks jdtls (`textDocument/prepareRename` then
`textDocument/rename`) and turns the `WorkspaceEdit` into the change set of the core, which applies it (the use case
and its refusals are already proven with the fake). It is the first refactoring that gabier will do on itself (the pending
rename of the base package needs it, with moves of folders).

Done when:
- on the real jdtls, renaming a method, a field, a local variable and a type (the file is renamed with it) updates every
  reference, shown on a small fixture project that jdtls imports without network access, and the result compiles;
- a position that holds no renamable symbol, an invalid name and a project still importing give the typed refusals of
  the port and change nothing;
- the contract scenarios of the rename (those the core's fake is scripted with) are replayed against the real jdtls in
  an integration test, and any difference between its answer and the scenario's fails the test;
- the unit tests of the adapter need no jdtls (fake language server over piped streams) and the coverage stays at 100%.

A `WorkspaceEdit` may hold a `TextDocumentEdit` with no edit, which is legal in LSP while a file change of the core
refuses an empty list: the conversion drops it. Positions past the end of a line are clamped by LSP and refused by the
core: the conversion decides.

Depends on: diagnostics-of-a-file-through-jdtls (same adapter), change-application-is-hardened (a rename moves files).

## Resolution

Done in d6b8738 (a skill sentence added in 2384439).

- `bin/ci` (worker, one full run on the final tree): format, build, test and jdtls passed; native skipped. Tests: core 248, daemon 169, cli 24, protocol 45, shared 236, 0 failures. JaCoCo 100%, no coverage exclusion added.
- Real jdtls: `ScenarioReplayTest` replays the 13 scenarios (four renames: method, field, local variable, type with its file moved; four refusals: no symbol, invalid name, still importing, outside the project; the diagnostics ones), compares each answer exactly (`isEqualTo` on the `ChangeSet`), applies it to a copy of the project and compiles that with javac, and checks the sources are untouched after each request, refusals included.
- Decision of the user (30-10-2026): the comparison stays exact. jdtls merges all the edits of a file into one (from the first to the last occurrence, repeating the code between), so the scenarios now hold jdtls' answer copied byte for byte, and the core test that assumed one edit per occurrence (`startsEveryRenameOnTheSymbolAndEditsOnlyThatSymbol`) was rewritten, with the user's authorisation, as `startsEveryRenameOnTheSymbolAndGivesEditsThatStartWhereItIsAndFitTheirFile`. The alternative, comparing what both answers do to the files, was rejected as looser than the rule of `add-scenario`.
- Choices: `JdtlsCapabilities` declares the file operations create, rename and delete and `rename.prepareSupport`: jdtls sends the file move of a renamed type, and `documentChanges`, only to a client that declares all three (read in `ClientPreferences.isResourceOperationSupported`). `RenameChanges` drops empty `TextDocumentEdit`s, clamps positions to the text as LSP defines it (CR, LF, CR LF) and refuses snippet edits, unknown file operations, non-file URIs and `changes`-only answers. A declined `prepareRename` or a null answer is `NO_SUCH_SYMBOL`; `InvalidRequest` on the rename is `INVALID_NAME`. The adapter opens nothing for a rename: jdtls reads the files as it knows them.
- Known limit, no ticket: jdtls returns an edit for a rename to a name that clashes inside the same method (`message` to the parameter `name`), which does not compile once applied.
- Review (once): no blocking finding. Suggestions, opened as issues: the rewritten core test no longer catches an edit that swallows a character or has a wrong `newText` (only the replay, which `check` does not run, does); a rename after the files changed on disk is untested. Fixed by the supervisor: step 2 of `add-scenario` contradicted step 7.

Tokens:
- worker: 508,559 (240,376 up to the question + 268,183 after the user's decision; the second figure may already include the first)
- reviewer: 101,539
- supervisor: 18 calls, 12,990 output, 34,836 input + cache write, 3,728,784 cache read; exchanges with the user: 0 typed messages and 1 answer to a question (a floor). The period runs from the launch of the worker (16:09) to the closing.
