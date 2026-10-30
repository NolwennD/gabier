# A create never overwrites a file that appeared meanwhile

Opened: 30-10-2026 16:09

The step that writes a `CreateFile` goes through `NioFileStore.write` without `CREATE_NEW`: a file that appears at the
target between the planning and the writing is overwritten, and the undo then deletes it (found by the worker of the
hardened change application). To reproduce: a store, like `FaultyFileStore`, that creates the target just before the
write.

Done when:
- [ ] A create of a path that exists when it is written fails (`WRITE_FAILED` or a key of its own) and leaves the file
      that appeared untouched, undo included; a test is red first.
- [ ] The creation of a new file in the normal case is unchanged.
- [ ] `bin/ci` green.

## Resolution

Merged as `126bc4f` (rebased three times on `main` as the other tickets landed).

Checked by the worker's `bin/ci` (full, on the rebased commit): format, build, test (100% coverage), native and jdtls
passed. New port method `FileStore.create(Path, byte[])`: `NioFileStore` opens with `CREATE_NEW`, so it fails with
`FileAlreadyExistsException` on any existing path, a dangling link included. New `Step.Create`, used by `PlanBuilder` for
`CreateFile`: when `create` throws `FileAlreadyExistsException` the step marks the file as foreign and the undo does not
delete it; otherwise the undo deletes what the step created, a half-written file included. `Step.Write` now only replaces
an existing file (its `previous` bytes are no longer an `Optional`). Proofs: `ChangeApplierRollbackTest`
`leavesAloneAFileThatAppearedBeforeTheCreateAndReportsOnlyTheFailedWrite` (red first, failed at the assertions before
`Step.Create` existed) and `removesAFileItCreatedWhenItsWriteFailsHalfWay`; `NioFileStoreTest.Creating`
(`createsAFileWithItsBytes`, `neverReplacesAFile`, `neverWritesThroughALink`, `doesNotCreateTheDirectory`).

Review (one pass): nothing to report. Left out of scope by the worker: `Step.Delete.undo` can overwrite a file that
appeared meanwhile; issue `a-delete-undone-never-overwrites-a-file`.

Tokens:
- worker: 91706 (its last completion notification; it was resumed by messages)
- reviewer: 49449
- supervisor: 106618 output, 183726 input + cache write over 98 calls (cache read 13428294), supervisor session over the
  period of the ticket (from the launch of the workers; four tickets ran in parallel and the rest of the session is
  included, so this figure is shared with the other tickets of the period); exchanges with the user: 3 messages
