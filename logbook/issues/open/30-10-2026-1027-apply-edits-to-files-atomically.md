# A set of edits is applied to the files all or nothing

Opened: 30-10-2026 10:27

Every operation of gabier (rename, extract, organize imports...) ends the same way: the language server answers with a
set of changes, and gabier writes them. That logic is common to all servers and to all operations, and it knows no LSP
type, so it comes first. In a new pure-Java module of the core (JPMS, `java.base` and `shared` only; its package a
context of `fr.craft.gabier` that satisfies the architecture tests), model a change set: text edits on files (a range
and a replacement), and the resource operations create, rename and delete. Applying it writes the files, or none.

Done when:
- text edits of one file are applied together whatever their order in the set, overlapping ones are refused, and the
  line endings (LF or CRLF) and the encoding of the file are kept;
- the position convention (the CLI shows line:column 1-based, LSP counts 0-based UTF-16 units) is chosen once,
  documented in the model, and tested with a character outside the BMP;
- create, rename and delete of files work, a refused set (existing target, missing file, stale text) changes nothing,
  and a failure in the middle of the writes restores the files already written (shown by a test);
- a dry run returns the unified diff and the list of created, renamed, deleted and modified files without writing.

Depends on: shared-error-package-from-seed4j (so that the domain is born null-safe and validated with Assert).
