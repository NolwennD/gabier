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
