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
