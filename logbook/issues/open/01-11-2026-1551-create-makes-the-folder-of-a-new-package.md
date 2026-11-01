# gabier create makes the folder of a new package

Opened: 01-11-2026 15:51

An agent creates a type in a package that does not exist yet with one `gabier create`, instead of a `mkdir -p` first. Today a change set only
creates files, never folders, so `gabier create src/main/java/demo/newpkg/Foo.java class` is refused with `editing-folder-missing` (documented in
`docs/commands/create.md` and the help of the command). The `package` line is already right for a missing folder (scenario
`CREATE_A_TYPE_IN_A_NEW_PACKAGE`). Once closed, the folder is created with the file and reported.

Done when:
- [ ] A change set can create the missing folders of a created file, inside the project only; `ChangeApplier` creates them, a dry run creates
      nothing, and the answer says which folders were created (text and `--json`), as a test on the fake shows.
- [ ] `gabier create` of a type in a new package creates its folders and the file; the scenario `CREATE_A_TYPE_IN_A_NEW_PACKAGE` is replayed on the
      real jdtls with the folders created; `editing-folder-missing` stays only where a folder cannot be created (a file in the way, outside the
      project), each case a test.
- [ ] The page, the help and the notes that say a `mkdir -p` is needed are updated. `bin/ci` green, coverage 100%.
