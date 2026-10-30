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
