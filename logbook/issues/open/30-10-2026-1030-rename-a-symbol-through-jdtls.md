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
- the unit tests of the adapter need no jdtls (fake language server over piped streams) and the coverage stays at 100%.

Depends on: diagnostics-of-a-file-through-jdtls (same adapter).
