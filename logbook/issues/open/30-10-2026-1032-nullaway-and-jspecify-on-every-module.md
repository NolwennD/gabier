# Null safety is checked at compile time on every module

Opened: 30-10-2026 10:32

Adopt what seed4j does: JSpecify annotations and NullAway, run by Error Prone at compile time, so that a null where
none is allowed does not compile. See `../seed4j/pom.xml` (the compiler arguments: `NullAway:ERROR`,
`JSpecifyMode=true`, `OnlyNullMarked=true`; the dependencies `org.jspecify:jspecify` and
`com.uber.nullaway:nullaway-annotations`; the `package-info.java` carrying `@NullMarked`). The translation to
Gradle (a plugin such as `net.ltgt.errorprone`) goes in the convention plugin `gabier.java-module-conventions`, the
versions in the catalog, and the third-party modules the way the other jars are declared. The domain may depend on
these annotations: the user decided it on 30-10-2026, so the allow-list of the architecture tests and the quality
rule say so (`org.jspecify..` and `com.uber.nullaway.annotations..`). If Error Prone does not support the JDK that
compiles the modules, stop and report.

Done when:
- every package of the existing modules has a `package-info.java` annotated `@NullMarked` (the user's decision: a
  package-info per package, not the module-level annotation), an architecture test fails on any package of the code that
  lacks it, and `./gradlew check` fails on a deliberate null passed where none is allowed in a main class (try it,
  then undo);
- the check is configured once, in the convention plugin, not module by module;
- the jdtls probe stays green: jdtls reports no error on the annotated code (otherwise report what it says and stop);
- `.claude/rules/quality.md` describes the rule (nullness by default, `@Nullable` as the exception) and the
  architecture allow-list is updated; `bin/ci` is green, native image included.
