# gabier moves classes to a package, creating it when it is missing

Opened: 01-11-2026 18:35

An agent moves one or several classes to another package with `gabier move [--dry-run] <file>... <package>` (the last operand is the package, in
dotted form: `demo.billing`), instead of moving files and fixing the `package` lines, the imports and the references by hand. The package is created
when it is missing. The answer is the files changed, moved and created and the unified diff, text and `--json`; a refusal is a problem object,
nothing written. Daemon method (skill `add-daemon-method`) and cli command on the shape of `move-type-to-file`; the moved file keeps its name.

**First, the trap left by the previous ticket** (`01-11-2026-1551-create-makes-the-folder-of-a-new-package.md`, resolution): `PlanBuilder.requireFolder`
(the path of `RenameFile`) checks `store.isFolder` and ignores the folders already planned, so `set(CreateFile(fresh/A.java), RenameFile(B.java ->
fresh/B.java))` is refused while the reverse order works, and no test pins it. A move into a new package needs the rename to make the folders: the
worker reuses `makeFolders` (the applier makes the folders as steps of the plan, so a dry run makes nothing and a failure undoes them).

**Leads, not a design** (the worker explores with `ripwire` and the jdtls clone at /home/nolwenn/dev/projects/eclipse.jdt.ls, read-only, and writes the
choice and the reason in the resolution): jdtls is configured, not fought. `java/move` with `moveKind: "moveResource"` takes `sourceUris`, a
`destination` (the uri of a package, `MoveHandler.moveCU`) and `updateReferences`; `java/getMoveDestinations` lists the packages. The destination is
a package of jdtls's model, so it probably has to exist on disk, and be seen by jdtls, before the request: find out whether the folder is made first
(and the watched files told) or jdtls is asked to move to a package that does not exist yet. Read what it does to a file that holds several types,
to a package-private member used from the old package, and to a file whose name is already taken in the target.

Done when:
- [ ] One class, then several, moved to an existing package: the files are moved on disk, their `package` line, the imports and the references of the
      project (main and test sources, other packages) are updated; `--dry-run` writes nothing. Shown on the fake and by contract scenarios replayed on
      the real jdtls (skill `add-scenario`).
- [ ] A class moved to a package that does not exist: the folders and the move in one change set, in either order of `CreateFile` and `RenameFile`
      (a test pins it, the trap above), a dry run makes no folder, a failure leaves none; replayed on the real jdtls.
- [ ] Refusals, each a scenario replayed on the real jdtls or a refusal of gabier with its reason in the resolution: a package name that is not one, a
      file of the same name already in the target (nothing overwritten), a file outside the project or that is not a Java source, a file already in the
      target package; terminal escapes neutralised as in `diagnostics`.
- [ ] `docs/commands/move.md`, the line of the README list, the row of `docs/protocol.md` and the help of the command (with its refusals) are added;
      `.claude/rules/dogfooding.md` names the command. `bin/ci` green, coverage 100%.
