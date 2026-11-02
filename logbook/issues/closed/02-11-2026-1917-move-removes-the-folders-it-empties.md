# gabier move removes the folders it empties

Opened: 02-11-2026 19:17

Met while renaming the base package (`rename-the-base-package`): after `gabier move` of every class of a package, the
old folder tree (`fr/craft/gabier/...`) stays, empty, and has to be removed by hand. A move that empties the source
folder of a package removes it, and its parents up to the source root when they are empty too.

Done when:
- a move that empties a package folder leaves no empty folder between it and the source root; a folder that still holds a
  file (a `package-info.java` not moved, a resource) stays; a dry run removes nothing;
- `docs/commands/move.md` says so; `bin/ci` passes.

## Resolution

Fixed in `00d2b8a`. `MoveToPackage` lists the folders each moved file leaves, from the deepest, up to its source folder;
`ChangeApplier.apply(ChangeSet, List<Path>)` adds a `Step.RemoveFolderIfEmpty` for each, which skips a folder that is
missing or not empty (`DirectoryNotEmptyException`, now in the `FileStore.delete` contract) and makes it again on undo.
The report of the move does not list the removed folders. Proven by `MoveToPackageTest`
(`removesTheFolderItEmptiesAndItsEmptyParentsUpToTheSourceFolder`, `keepsTheFolderOfThePackageWhenItsPackageInfoStays`,
`keepsTheFolderOfThePackageWhenAResourceStaysInIt`, `removesNoFolderOnADryRun`), `ChangeApplierTest$RemovingTheFoldersEmptied`
and `ChangeApplierRollbackTest$WhenAFolderCannotBeRemoved`; the scenarios `move-the-last-class-of-a-package` and
`move-the-last-class-of-a-package-that-has-a-package-info` are replayed against jdtls. `docs/commands/move.md` gains
"The folders it empties".

Checked by the worker's full `bin/ci` on the last commit of the batch: format 10s, build 3s, test 179s, native 163s, jdtls 413s, all passed; cli 598, core 693, daemon 866, protocol 366, shared 236, architecture 19 tests, 0 failures; jdtls coupling counts unchanged (4/4, 4/4, 3/3, 26/26, 130 scenarios).

Reviewed once with `move-rewrites-references-in-other-modules`: nothing blocking. Suggestions: no test moves a class into
a parent or child package, issue `02-11-2026-2025-move-to-a-parent-package-keeps-its-folder` (on hold); a Javadoc line of
`Effect.java:28` is over 120 columns, fixed by the supervisor on `main`.

Tokens:
- worker: counted in `move-rewrites-references-in-other-modules` (batch)
- reviewer: counted in `move-rewrites-references-in-other-modules` (one review of the batch)
- supervisor: counted in `move-rewrites-references-in-other-modules` (same period)
