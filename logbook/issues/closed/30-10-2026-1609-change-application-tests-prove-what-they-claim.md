# The tests of the change application prove what their names say

Opened: 30-10-2026 16:09

The review of the hardened change application found tests that prove less than they say, or less than the code promises:
- `ChangeApplierRollbackTest.putsBackAFileMovedAwayWhenTheFileCreatedUnderItsOldNameCannotBeRemoved` asserts only
  `ROLLBACK_INCOMPLETE`, no file state, and passes unchanged before the hardening: rename it or assert the files.
- The 300-character file name of the two real-filesystem rollback tests assumes a NAME_MAX of 255; on a larger limit
  the write succeeds and they fail on `assertThatThrownBy` (the cause is also the broader `FileSystemException`).
- Nothing shows that a failed atomic write leaves the original intact: a 0444 file, where the copy of the attributes
  makes the temporary file read-only and the write throws; the test asserts the old content and no `.gabier-*.tmp`.
- No spelling test uses a relative path: removing `toAbsolutePath()` from `PlanBuilder.normalized` keeps every test
  green (`Path.of("A.java")` and `directory.resolve("A.java")` in one set).

Done when:
- [ ] Each of the four points above has a test that fails for its own reason (a mutation shown once for the last two,
      then restored), or its name states what it proves.
- [ ] The rollback tests no longer depend on the limit of the file system.
- [ ] `bin/ci` green.

## Resolution

Merged as `9756116` (first commit of a batch of three issues of `core`, one worktree, one `bin/ci`).

Checked by the worker's `bin/ci` (full, once, on the last commit before the rebase): format 7s, build 5s, test 100s, native
209s, jdtls 181s; after the rebase on `main`, `bin/ci --stage test` on the head: 108s, core 259 tests, architecture 19, no
failure. All four points are tests, no change to main code:
- the rename test `reportsBothFilesAndOverwritesNeitherWhenTheFileCreatedUnderTheOldNameCannotBeRemoved` keeps
  `ROLLBACK_INCOMPLETE` and `notRestored` and asserts the state of the three files;
- the two rollback tests no longer use a 300-character name (NAME_MAX): they are
  `restoresTheFileAlreadyWrittenWhenTheSecondOneCannotBeCreated` and `removesTheFilesAlreadyCreated`, with a CREATE failure
  injected in `FaultyFileStore`, the cause asserted as `IOException`;
- `NioFileStoreTest.Writing.leavesTheOriginalIntactWhenTheNewContentCannotBeWritten` (a 0444 file: old content, permissions,
  no `.gabier-*.tmp`; skipped when run as root, where the file stays writable);
- `ChangeApplierTest.SpellingsOfOnePath`: `refusesTheSameFileDeletedUnderAnAbsoluteAndARelativeSpelling` and
  `reportsTheAbsolutePathOfAFileGivenAsARelativePath`.
Mutations shown once and restored: `PlanBuilder.normalized` without `toAbsolutePath()` fails the two new tests; removing
`Files.deleteIfExists(temporary)` in `NioFileStore.replace` fails the new test and `leavesNoTemporaryFileBehindWhenTheWriteFails`.

Not reviewed: small ticket (tests only, no production code), by the rule of `ship` step 1.

Tokens:
- worker: 124154 (the whole batch of three issues; the sum of its completion notifications is the last one)
- reviewer: none (not reviewed)
- supervisor: 37159 output, 62829 input + cache write over 24 calls (cache read 8114437), supervisor session over the period of the batch (from the launch of the worker; three other workers and the rest of the session are included, so the figure is shared); exchanges with the user: 0 messages
