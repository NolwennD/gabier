# gabier move updates the exports of the module-info

Opened: 02-11-2026 19:34

Met while diagnosing `move-rewrites-references-in-other-modules`: a move of every class of a package of a JPMS module
leaves the `exports` (and `opens`) line of its `module-info.java` on the old name, which no longer exists, so the module
stops compiling for its dependents. Reproduce: in `modules/shared`, `gabier move --dry-run
modules/shared/src/main/java/eu/gabier/shared/error/domain/*.java eu.gabier.shared.error.domain` shows no change of
`modules/shared/src/main/java/module-info.java`. Start from what jdtls's move refactoring offers for the module-info.

Done when:
- a move that empties an exported or opened package renames it in the `exports`/`opens` lines of the module-info; a move
  that leaves classes in it changes nothing there; a scenario and its replay show both;
- `bin/ci` passes.

Depends on: move-rewrites-references-in-other-modules.

## Resolution

Done in e23e41b.

- Observed red on the real jdtls: a move that empties `demo.shared` leaves `exports demo.shared` and `opens demo.shared`
  as they were. No setting of jdtls does it.
- **Workaround, in the adapter only** (`ModuleExports`, wired in `JdtlsLanguageServer.moveToPackage` and `askToMove`),
  because the issue asks for the descriptor to follow and jdtls leaves it: acts only when no `.java` file is left in the
  old package folder, edits only the `module-info.java` at the root of the source folder of the moved files, renames the
  package in `exports` and `opens` keeping a `to` clause, and drops the old directive when the module already has the
  same one for the new package (a rename would give a duplicate). About 110 lines plus 50 in the server: not very light,
  kept in the adapter and out of the core; the review notes that the reason cites no documentation of jdtls (listed in
  `move-and-references-review-suggestions`).
- Proof: `ModuleExportsTest` (12 cases) and `JdtlsMoveToPackageTest` (9 new), red with the call disabled; scenarios
  `move-the-last-class-of-an-exported-and-opened-package` (project `EXPORTS`) and
  `move-every-class-of-an-exported-package-to-an-exported-package`, both replayed. "A move that leaves classes changes
  nothing there" is `move-a-class-that-another-module-uses` and the nested scenario. Docs: `move.md`, `gabier help move`,
  `improve.md`.
- Known gap, now a ticket: the drop narrows an export when the target is already exported with a `to` clause
  (`move-keeps-the-visibility-of-an-exported-package`).
- The batch was reviewed once. One finding showed a `Done when` point of `move-keeps-nested-and-qualified-names` unmet (the order of the imports); the user decided on 07-11-2026 to document it and amend the point. The other findings are in `move-keeps-the-visibility-of-an-exported-package` and `move-and-references-review-suggestions`; the bug met on the way is `move-on-a-root-with-two-eclipse-projects`. `bin-ci` of pull request 21: green. `bin/ci --all` of the worker: format, build, test (cli 885, core 723, daemon 974, protocol 397, shared 236, architecture 19), release-install, native, jdtls all passed. No coupling ceiling raised.

Tokens (one batch of three issues, shared by the three resolutions):
- worker: 329256
- reviewer: 89686
- supervisor: 33 calls, 26779 output, 177000 input + cache write (5401473 cache reads), over the period from the launch of
  the worker to the merge, the other work of the period included; exchanges with the user: 4 messages
