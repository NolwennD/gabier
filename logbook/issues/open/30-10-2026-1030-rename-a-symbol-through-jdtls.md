# gabier renames a symbol

Opened: 30-10-2026 10:30

Second operation of the port, and the first that writes: rename the symbol at a position (line:column) or at the first
occurrence of an identifier in a file (the `--token` of the Python driver), to a new name. The adapter asks jdtls
(`textDocument/prepareRename` then `textDocument/rename`), turns the `WorkspaceEdit` into the change set of the core,
and the core applies it, or gives the diff on a dry run. It is the first refactoring that gabier will do on itself
(the pending rename of the base package needs it, with moves of folders).

Done when:
- on the real jdtls, renaming a method, a field, a local variable and a type (the file is renamed with it) updates every
  reference, shown on a small fixture project that jdtls imports without network access, and the result compiles;
- a position that holds no renamable symbol, an invalid name and a project still importing fail with a readable message
  and change nothing;
- the dry run writes nothing and shows the diff; the unit tests need no jdtls and the coverage stays at 100%.

Depends on: diagnostics-of-a-file-through-jdtls (same adapter), apply-edits-to-files-atomically.
