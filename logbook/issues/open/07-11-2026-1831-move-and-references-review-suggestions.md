# move and references: the module-info edit is proven on more shapes, and the docs claim only what a scenario shows (review suggestions)

Opened: 07-11-2026 18:31
On hold: findings of the review of the batch `move-keeps-nested-and-qualified-names`, `move-updates-the-exports-of-the-module-info`
and `references-refuses-a-symbol-that-does-not-resolve`; none breaks a `Done when` point or a real use met so far.

- `docs/commands/references.md:60-64`: claims more than the scenario shows. Only an import of a package that does not
  exist (`demo.missing.Gone`) is replayed; "a module that is not required, a package that is not exported" and "a fully
  qualified name that does not resolve is answered `no references`" have no scenario. Also, the scenario of the issue
  (a type of a module the project does not see) differs from `demo.missing`, and `FindReferencesTest` only echoes the
  scripted fake.
- `ModuleExports.java:26`: no test for `open module`, a BOM, or `exports a.b` beside `exports a.b.c` with `a.b` emptied.
- `JdtlsLanguageServer.java:762`: if jdtls ever edits `module-info.java` itself (`provides`/`uses` of a moved class), the
  change set holds two `EditFile` for one path; nothing merges them or checks the overlap. The docs say "jdtls leaves it
  as is" from one observation.
- `JdtlsLanguageServer.java:783`: a package is empty when its folder holds no `.java` file, not when it holds no class: a
  package left with a `package-info.java` only keeps its `exports`, which javac may reject; no replay (the scenario
  `MOVE_THE_LAST_CLASS_OF_A_PACKAGE_THAT_HAS_A_PACKAGE_INFO` has no descriptor).
- `JdtlsLanguageServer.java:774`: `textOnDisk` reads UTF-8 only; a descriptor in another charset makes the whole move end
  in `UncheckedIOException` after jdtls answered. The tests cover an unreadable descriptor, not a malformed one.
- `JdtlsMoveToPackageTest` `failsWhenTheFolderOfAMovedFileCannotBeListed`: `--x------` does not deny root, so the test
  fails when the build runs as root.
- The reason of `ModuleExports` cites no documentation of jdtls (`workflow.md`, "jdtls is configured, not fought"): the
  `java.*` settings and the move options read first, in the issue's resolution or in the class.

Done when:
- [ ] each point above is either fixed with a test (or a scenario and its replay), or answered in this issue's resolution
      with the reason; `bin/ci` passes.
