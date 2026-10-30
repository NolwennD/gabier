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
