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
- [x] `gabier create` of a type in a package whose folder does not exist creates the folders and the file, inside the project; a dry run
      creates nothing; shown on the fake and by `CREATE_A_TYPE_IN_A_NEW_PACKAGE` replayed on the real jdtls.
- [x] `editing-folder-missing` stays only where a folder cannot be created (a file in the way, outside the project), each case a test;
      a failure after the folders are made leaves no empty folder behind, or the resolution says why it cannot.
- [x] The page, the help and the notes that say a `mkdir -p` is needed are updated. `bin/ci` green, coverage 100%.

## Resolution

Commit bb65ef7. `FileStore.createFolder(Path)` (one folder, the parent must exist; `NioFileStore`) and `Step.MakeFolder` (undoes itself, by deleting
the folder, only if its own `apply` succeeded). `PlanBuilder.makeFolders`, used only by `create(CreateFile)`: it walks up from the parent of the file to
the first existing folder and adds one `MakeFolder` per missing level, shallowest first, before the `Step.Create`; a set of planned folders makes
two files of the same new folder plan it once, and makes a `CreateFile` at the path of a planned folder an `editing-file-exists`. An ancestor that
exists and is not a folder (a file, a file created earlier in the set, a dangling link) is `editing-folder-missing`, with that path as `folder`.
`ChangedFiles` and `ChangeReport` are unchanged: the answer lists the created file, not the folders. Page, help, `dogfooding.md` and the Javadocs updated.

**The choice and why**: the folders are made by the applier, as steps of the plan. A dry run only plans, so it makes nothing; every folder is a
`Step`, so a failed write removes the folders already made in reverse order (`ROLLBACK_INCOMPLETE` if one cannot be removed); `RenameFile` into a new
package (the coming `move FILE... PACKAGE`) can call the same `makeFolders`. jdtls has no setting for it: LSP has no "create folder" operation and
jdtls relies on the client to create the parents of a `CreateFile` (`ChangeUtil.convertRenamePackcageChange`, a `.temp` file created then deleted).
Rejected: `createDirectories` in `NioFileStore.create` (no plan, so no dry-run verdict; no undo, the adapter cannot know which folders it made;
domain logic in the adapter); a `mkdir` in `CreateType` before the apply (outside all-or-nothing; `move` could not reuse it); a new
`FileChange.CreateFolder` (LSP has no such change and jdtls never emits one, so every producer would compute the missing folders, which needs the
store in the adapter and the scenarios); a project root given to `ChangeApplier` to refuse folders outside the project (a new argument in seven use
cases and `DaemonServer`). A field "folders created" in the answer: not added, `ChangedFiles` is shared by every command that changes files.

**"Outside the project"**: the applier has no root. For `create`, the server refuses a file outside the project (`language-server-file-outside-project`)
before any change set exists, so no folder is made; the guard is the server's, and `CreateTypeTest.refusesAFileOutsideTheProjectAsTheServerDoes` now also
asserts that `../outside` does not exist. `WorkspaceEdits` maps every jdtls `CreateFile` as it is, so a command that produced a `CreateFile` outside the
project would make its folders: none does today (`move-type-to-file` creates in the package of the type). `editing-folder-missing` now means "a file or a
dangling link is where a folder would go"; its message still reads "There is no folder X" in that case. A link to a folder that points outside the project
is followed, as before (`createsAFileInALinkToAFolder`).

Order of work and deviations (worker's report): `NioFileStoreTest` red, then the store; the first `ChangeApplierTest` red, then `MakeFolder` and
`PlanBuilder`; more applier and rollback tests; application and daemon-handler tests; `HelpFlagTest` red, then the help, the docs and the replay.
The whole walk (planned folders, file in the way) was written in the step that made the first test pass, so the later applier tests were not red first;
four mutations compensated (removing `folders.contains`, the `exists` refusal, the undo, the `made` guard: each made the intended tests fail).
`CreateTypeTest` and `CreateTypeMethodTest` passed at once (characterisation). A scripted edit deleted about 60 lines of `ChangeApplierTest` once; caught
in the diff and restored. Five tests were replaced because their expectation flipped on purpose (a folder is made instead of refused); the review compared
the test methods of every file with `main`: each has a counterpart of the same strength, none else is lost. `ScenarioReplayTest.compile` no longer
pre-creates the parent folders and asserts each created file is a regular file after the apply: the replay proves the applier makes them.

Checked: worker's full `bin/ci` green on bb65ef7 (format 12s, build 5s, test 169s with coverage 100% and Checkstyle, native 185s, jdtls 492s). Review
(neutral, once): no blocking finding. Fixed by the supervisor (`:core:test` of the class green): the assertion on `../outside`. Left, to carry into the
ticket of `move FILE... PACKAGE`: `PlanBuilder.requireFolder` (the rename path) checks `store.isFolder` and ignores the set of planned folders, so
`set(CreateFile(fresh/A.java), RenameFile(B.java -> fresh/B.java))` is refused while the reverse order works; no test pins it. Watched files:
`WatchedFiles.scan` walks the whole tree on every check, so a file in a new folder is seen by jdtls (read, not run end to end).

Tokens:
- worker: 172073 (subagent_tokens, resumed once after a pause for a usage limit)
- reviewer: 78969 (subagent_tokens)
- supervisor: 18 calls, 11935 output, 34485 input + cache write (4529138 cache read) over the supervisor session from the launch of the
  worker (16:20 UTC) to the close, other work included (a pause for a usage limit lies in the period); exchanges with the user: 2 messages
