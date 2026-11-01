# gabier create makes the folder of a new package

Opened: 01-11-2026 15:51

An agent creates a type in a package that does not exist yet with one `gabier create`, instead of a `mkdir -p` first. Today
`gabier create src/main/java/demo/newpkg/Foo.java class` is refused with `editing-folder-missing` (`PlanBuilder.java:141`; documented in
`docs/commands/create.md` and the help of the command). The `package` line is already right for a missing folder (scenario
`CREATE_A_TYPE_IN_A_NEW_PACKAGE`). Once closed, the folder is created with the file.

**Leads, not a design** (the worker explores with `ripwire` and chooses, and writes the choice and the reason in the resolution):
- jdtls has no setting for it: LSP has no "create folder" operation, and jdtls itself relies on the client to create the parents of a
  `CreateFile` (`ChangeUtil.convertRenamePackcageChange`: a `.temp` file created then deleted in the new package, LSP issue #272).
- Where the folders are created: when a created file is applied (`ChangeApplier`), or an explicit step of the plan (a new `FileChange`), or
  only for `create`. The same need will come back with other refactorings that move a type to a new package.
- Whether the answer must say which folders were created: `ChangedFiles` is shared by every command that changes files, so a new field
  there costs every one of them; the created file may be enough.
- A failure after the folders are created, a dry run, a file or a link in the way, a path outside the project.

Done when:
- [ ] `gabier create` of a type in a package whose folder does not exist creates the folders and the file, inside the project; a dry run
      creates nothing; shown on the fake and by `CREATE_A_TYPE_IN_A_NEW_PACKAGE` replayed on the real jdtls.
- [ ] `editing-folder-missing` stays only where a folder cannot be created (a file in the way, outside the project), each case a test;
      a failure after the folders are made leaves no empty folder behind, or the resolution says why it cannot.
- [ ] The page, the help and the notes that say a `mkdir -p` is needed are updated. `bin/ci` green, coverage 100%.
