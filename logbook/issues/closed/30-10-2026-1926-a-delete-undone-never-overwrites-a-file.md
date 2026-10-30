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
