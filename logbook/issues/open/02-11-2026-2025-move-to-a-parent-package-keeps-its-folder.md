# gabier move to a parent or child package keeps the folder it still uses

Opened: 02-11-2026 20:25
On hold: a finding of the review of `move-removes-the-folders-it-empties`; it breaks no `Done when` point.

`MoveToPackage.foldersLeft` lists `demo/old/legacy` and `demo/old` for a move from `demo.old.legacy` to `demo.old`; only
the `DirectoryNotEmptyException` branch of `Step.RemoveFolderIfEmpty` keeps `demo/old`, and no test moves a class into a
parent or a child package (`ChangeApplierTest` covers that branch with a leftover file, not the moved file).

Done when:
- a test of `MoveToPackageTest` moves the last class of a package into its parent package, and one into a child package,
  and shows the destination folder kept and the emptied one removed;
- `bin/ci` passes.
