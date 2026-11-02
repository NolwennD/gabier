# gabier says when jdtls does not link a project it depends on

Opened: 02-11-2026 19:34

Met while diagnosing `move-rewrites-references-in-other-modules`: with the Gradle plugin `org.gradlex.extra-java-module-info`
(without `skipLocalJars = true`), the Eclipse model jdtls imports has no project dependency between the modules. Then,
with exit 0 and no warning, `gabier move` rewrites only the moved module, `gabier references` lists no use in the other
modules, and `gabier diagnostics` reports no error on a `requires` of a module that is not on the classpath. Any user
build with that plugin meets it. Reproduce: two Gradle 9.8.2 JPMS modules `a` and `b` (b requires a and uses a type of
a) with the plugin applied; `gabier references` on the type answers `no references`.

Done when:
- a command whose answer depends on the projects that jdtls links (`move`, `references`, `rename`) says, in its answer
  or as a refusal with a key of its own, which projects of the build jdtls does not link, instead of a silent partial
  answer; the help and `docs/` name the setting `skipLocalJars`;
- `bin/ci` passes.

Depends on: move-rewrites-references-in-other-modules.
