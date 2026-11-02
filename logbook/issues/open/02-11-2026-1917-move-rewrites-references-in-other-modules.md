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
