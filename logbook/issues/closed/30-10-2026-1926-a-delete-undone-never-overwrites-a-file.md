# Undoing a delete never overwrites a file that appeared meanwhile

Opened: 30-10-2026 19:26

`Step.Delete.undo` puts the deleted file back with `store.write` when nothing exists at the target. A file that appears
at that path between the delete and its undo is replaced by the old bytes. Found by the worker of
`a-create-never-overwrites-a-file`, which left it out of scope; the fix there is `FileStore.create` with `CREATE_NEW`.

Done when:
- the undo of a delete whose target exists again fails without touching that file, and the rollback reports it as
  incomplete (`ROLLBACK_INCOMPLETE`); a test with `FaultyFileStore` that creates the target just before the undo, red
  first;
- the undo in the normal case puts the file back as before;
- `bin/ci` green, coverage 100%.

## Resolution

Merged as `50e9b22` (second commit of the batch).

Checked by the worker's `bin/ci` (see the first issue of the batch). `Step.Delete` records `deleted` once `store.delete` has
returned, and its undo calls `store.create` (`CREATE_NEW`) then sets the permissions, only if it deleted. A file that appears
at the target between the delete and its undo makes the undo throw `FileAlreadyExistsException`: the rollback is reported as
`ROLLBACK_INCOMPLETE` with `notRestored` set to that file, which is left untouched. Test:
`ChangeApplierRollbackTest.leavesAloneAFileThatAppearedWhereADeletedFileWasAndReportsTheRollbackIncomplete`, with the new
`FaultyFileStore.Failure.FOREIGN_FILE_APPEARS_AFTER`; red first, for the expected reason (the key was `WRITE_FAILED`, the
rollback was reported complete). The normal case stays green (`givesBackADeletedFileWithItsPermissions`,
`givesBackAFileMovedThenDeletedWithItsPermissions`); the `deleted` flag is what keeps
`leavesTheFileAloneWhenItsDeletionItselfFails` green, its undo runs on a file that was never deleted.

Review (one pass, of this commit): nothing to report. Not covered by a test, settled by `CREATE_NEW`: a directory or a
symlink at the target of the undo.

Tokens:
- worker: shared with the batch, see the first issue (124154)
- reviewer: 50883
- supervisor: shared with the batch, see the first issue
