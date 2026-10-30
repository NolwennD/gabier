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

## Resolution

Done in 0e9b48c (rebased by the supervisor from 4dcf0d2). New module `modules/core` (`fr.craft.gabier.core`, pure Java,
`shared` transitive because `EditingException` extends `GeneratorException`), context `fr.craft.gabier.editing`. Domain:
`Position`, `Range`, `TextEdit`, the sealed `FileChange` (`EditFile`, `CreateFile`, `RenameFile`, `DeleteFile`),
`ChangeSet`, `ChangeReport`, the port `FileStore`, the entry point `ChangeApplier` (`dryRun`, `apply`), and the internal
`TextFile`, `UnifiedDiff`, `PlanBuilder`, `ChangePlan`, `Step`; adapter `NioFileStore`. Typed errors: one
`EditingException` with an `EditingErrorKey` (`FILE_EXISTS`, `FILE_MISSING`, `INVALID_RANGE`, `OVERLAPPING_EDITS`,
`READ_FAILED`, `ROLLBACK_INCOMPLETE`, `STALE_EDIT`, `UNSUPPORTED_ENCODING`, `WRITE_FAILED`), each with parameters.

Choices: position = 0-based line and column in UTF-16 code units, LSP's default (the adapter copies the numbers, the CLI
adds 1 for display); UTF-8 only, strictly decoded, BOM kept, anything else refused rather than corrupted; everything
outside the edits kept byte for byte, a replacement takes the file's first line terminator; the set is first run in
memory so every refusal comes before any write, then written in the order of the set (what makes rename plus edit work),
and a failure undoes the steps in reverse, the failing one included; no `application` package, since nothing
orchestrates beyond the domain; the unified diff is a 150-line Myers implementation, no library (a library in the core
is the user's decision), cross-checked against `diff -u`, with a non-minimal but correct fallback beyond 2000 changed
lines; no exclusion annotation.

Checked by the subagent: `bin/ci` green with `GABIER_JDTLS` set (format 3s, build 17s, test 42s, native 95s, jdtls 46s);
counts: core 130, shared 236, architecture 16, daemon 22, daemon quick probe tests 43, cli 9, protocol 4, no failure;
the rollback is proven on the real file system (a missing folder fails the second write, the first file is restored byte
for byte) and by a faulty store decorator, nothing depends on permissions or on not being root.

Review (one neutral reviewer, nothing blocking). Suggestions taken: the note on clamping in the `Position` Javadoc (the
supervisor). Opened as the issue `change-application-is-hardened`: paths keyed by raw `Path` (`./x` and `x` differ), a
missing parent folder not seen by the dry run, a move undo that can touch a foreign file, writes that truncate rather
than replace atomically and lose permissions. Left to the adapter tickets (their issues say so): a `TextDocumentEdit` with
no edit is legal in LSP and refused by `EditFile` (the conversion drops it), and the clamping of positions past a line.
To decide when the daemon is written: the architecture rule keeping contexts off each other's domain sees `daemon` as a
context and will flag its use of `editing.domain`; the ticket of the first adapter adjusts the rule. The README has no
module list, so none was changed; the table of the `verify` skill is not aligned (left).
