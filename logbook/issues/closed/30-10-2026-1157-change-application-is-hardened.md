# Applying a change set survives paths, missing folders and foreign files

Opened: 30-10-2026 11:57

The review of `apply-edits-to-files-atomically` found cases where the all-or-nothing application, or the dry run that
must equal it, is wrong or incomplete. None is met by the happy path, all will be by a real refactoring (moves of
folders, renamed files): path spellings, parents that do not exist, files that appear meanwhile, files that must not
lose their permissions.

Done when:
- two spellings of one path (`./x` and `x`, a `..` segment) are one file in the plan: after `rename ./x -> y`, an edit on
  `x` is refused as a missing file instead of reading the stale file and recreating `x`;
- a create or a rename whose parent folder does not exist is refused when the set is planned, so that the dry run gives
  the same verdict as the application, and the report says which folder is missing;
- the undo of a failed move leaves alone a target that was not created by that move, and reports no spurious
  `ROLLBACK_INCOMPLETE`;
- a written file is replaced atomically (a temporary file in the same folder, then a move) and keeps its permissions, an
  undone deletion gives the file back with its permissions; a symbolic link is edited through, not replaced;
- each point is shown by a test that fails before the change.

Depends on: core-operations-on-a-fake-language-server (same module, the use cases build on `ChangeApplier`).

## Resolution

Done in fc7d520 (two comments wrapped to 120 columns in 92f18fe).

- `bin/ci` (worker, full run on the final code): format, build, test and jdtls passed; native skipped (the cli does not require core). `core`: 248 tests, 0 failures; JaCoCo 100%; architecture tests green.
- Each point is shown by tests that failed before the change (hard link replaced in place, spellings `./A` / `A` / `sub/../B`, missing or non-folder parent in apply and dry run, foreign target after a failed move, permissions of an undone delete); the tests of permissions and links on edit pass before too and only guard the new implementation. Mutations done by the worker: removing the temporary copy turns the permission guards red, replacing `toRealPath()` by the path turns the link guards red.
- Choices: reports and errors show normalized absolute paths (they showed the path as given); new error key `editing-folder-missing`; new methods of the port `FileStore` (`isFolder`, `permissions`, `setPermissions`); `Step.Move` and `Step.Delete` are classes with state (a record cannot hold it); an existing file is replaced through a temporary file next to it, so a folder without write permission now refuses a write that only the file's own permission governed before (consequence of the atomic replace the ticket asks for). Two real-filesystem rollback tests use a 300-character file name instead of a missing folder to make the write fail (a missing folder is now refused at planning); the review found the proof not weakened.
- Review (once): no blocking finding. Suggestions, opened as issues: a rollback test whose name says more than it proves, the 300-character name tied to a NAME_MAX of 255, no test that a failed atomic write leaves the original intact, no test with a relative path in the spellings. A create overwrites a file that appears between planning and writing, and the undo then deletes it (worker's finding; issue). Not opened: `getPosixFilePermissions` throws on a non-POSIX file system (no such target today).

Tokens:
- worker: 144,685
- reviewer: 68,476
- supervisor: 10 calls, 6,915 output, 22,432 input + cache write, 1,831,778 cache read; exchanges with the user: 1 message (a floor). The period runs from the launch of the worker (15:48) to the closing, and includes the /context report.
