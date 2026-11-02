# gabier move rewrites the references in the other JPMS modules

Opened: 02-11-2026 19:17

Met while renaming the base package (`rename-the-base-package`): `gabier move` of the classes of one JPMS module
(`modules/shared`, `modules/protocol`, `modules/core`) rewrote the `package` lines and the references inside that module
and in `architecture/` (no module-info), but left 592 `import fr.craft.gabier…` lines in the other modules, with exit 0 and
no warning. Afterwards `gabier diagnostics` reports `The import … cannot be resolved`, `gabier actions` on the use offers
no import of the moved type, and `organize-imports` answers `nothing to change`, then, once the jar is rebuilt, refuses
with `language-server-unresolved-imports`. Reproduce: in a worktree of `a2310c2`, `gabier move
modules/shared/src/main/java/fr/craft/gabier/shared/error/domain/*.java eu.gabier.shared.error.domain`, then `gabier
diagnostics modules/protocol/src/main/java/fr/craft/gabier/protocol/ChangedFiles.java`. Start from the JDT LS
documentation (how the Gradle import of a multi-module build links the projects) before any workaround. Leads from the
issues of eclipse.jdt.ls (02-11-2026, none on this exact case): #3924 (open), with Gradle 9.7 and later the
annotation-processor model build aborts the Gradle import, and the build uses Gradle 9.8.2 with Error Prone and NullAway
on the annotation processor path (workaround verified there: `java.import.gradle.annotationProcessing.enabled=false`);
the change after `./gradlew :shared:jar` hints that the other modules see `shared` as a jar, not as a project; the
moved package is no longer the one the `exports` of the module-info names. Check the jdtls log first.

Done when:
- a move of classes of one module rewrites the imports and qualified references in every module that depends on it, or
  refuses before writing anything and says which modules it cannot reach; a scenario and its replay show it;
- `bin/ci` passes.

## Resolution

Fixed in `b5b2324`. The cause was in the build, not in jdtls: the Gradle plugin `org.gradlex.extra-java-module-info`
(default `skipLocalJars = false`) transforms the jars of the build's own modules, so the Eclipse model that Buildship
imports for jdtls has no project link between the modules, and jdtls searches and refactors each module alone (only
`architecture/`, which does not apply the plugin, was rewritten). Shown on a two-module Gradle 9.8.2 build: `references`
finds the uses without the plugin, none with it, and finds them again with `skipLocalJars = true`; on `a2310c2` the
dry-run move of `shared.error.domain` then rewrites 31 protocol, 79 core, 26 daemon and 10 cli files. The leads written
in this issue were ruled out: the jdtls log shows no import abort (not eclipse.jdt.ls #3924), and a plain JPMS build works
(not the `exports`). The fix is `skipLocalJars = true` in `gabier.java-module-conventions`, with its reason.

The first point is met by that build fix plus the scenario `move-a-class-that-another-module-uses`
(`PackageMoveScenarios`): two Eclipse projects with a module-info, `app` requiring `demo.shared`; the move of `Money`
rewrites the import in `Till` and the qualified names in `Purse`, replayed by `ScenarioReplayTest` against jdtls (the
replay learned linked projects: `EclipseProject.describeProjects`, `Effect.sourceFolders` over every project, a compile
check with `--module-source-path`). Its "or refuses" half is the issue `gabier-says-when-a-project-is-not-linked`
(decision of the supervisor). `docs/commands/move.md` gains "Several modules".

Checked by the worker's full `bin/ci` on the last commit of the batch: format 10s, build 3s, test 179s, native 163s, jdtls 413s, all passed; cli 598, core 693, daemon 866, protocol 366, shared 236, architecture 19 tests, 0 failures; jdtls coupling counts unchanged (4/4, 4/4, 3/3, 26/26, 130 scenarios).

Reviewed once with `move-removes-the-folders-it-empties`: nothing blocking. Suggestion: the replay writes the project
links by hand, so removing `skipLocalJars` leaves every gate green, issue
`02-11-2026-2025-the-gradle-import-of-gabier-links-its-modules` (on hold).

Met on the way and opened: `move-updates-the-exports-of-the-module-info`, `gabier-says-when-a-project-is-not-linked`,
`references-refuses-a-symbol-that-does-not-resolve`, `rename-takes-its-position-as-line-colon-column`.

Tokens:
- worker: 389 759 (two notifications, shared with `move-removes-the-folders-it-empties`, batch of one worktree)
- reviewer: 74 068 (one review of the batch)
- supervisor: 38 calls, 28 336 output, 55 629 input + cache write (4 397 623 cache read apart); the supervisor session from the launch of the worker to the close, the other work of the period included (issues opened, the usage statistics issue); exchanges with the user: 5 messages
