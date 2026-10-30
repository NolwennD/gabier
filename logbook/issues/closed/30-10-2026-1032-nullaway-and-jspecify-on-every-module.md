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
- whatever accepts or may return null says so with `@Nullable` (a parameter, a return type, a field, a type argument:
  JSpecify annotates the type usage, not the method or the class), `.claude/rules/quality.md` states it, and the existing
  code complies;
- the jdtls probe stays green: jdtls reports no error on the annotated code (otherwise report what it says and stop);
- `.claude/rules/quality.md` describes the rule (nullness by default, `@Nullable` as the exception) and the
  architecture allow-list is updated; `bin/ci` is green, native image included.

## Resolution

Done in 1798356. Error Prone 2.51.0 and NullAway 0.14.3 (one patch newer than seed4j's) run on `compileJava` of every
module through the convention plugin (`net.ltgt.errorprone` 5.1.1, `NullAway:ERROR`, `JSpecifyMode`, `OnlyNullMarked`),
on the Java 25 toolchain; the JDK exports are handled by the plugin. No Error Prone check had to be disabled. Each of the
four packages has a `package-info.java` annotated `@NullMarked` (the user's decision, not the module-level annotation),
and a new architecture test (`NullSafety.packagesShouldBeNullMarked`, matching the annotation by name since
`org.jspecify` is `requires static`) fails for a package that lacks it. JSpecify 1.0.1 and `nullaway-annotations`
0.14.3 are declared like the other jars; `DOMAIN_ALLOWED_PACKAGES` and the quality rule say that the domain may use
the annotations (the user's decision of 30-10-2026). Only `SelfCheck` needed `@Nullable`.

Why `extraJavaModuleInfo` is deactivated for the annotation-processor configurations only: they are class paths, and
the guard against automatic modules rejected every Error Prone jar; the guard stays on for the compile and runtime
classpaths of every source set.

Checked by the subagent: `bin/ci` green with `GABIER_JDTLS` set (format 2s, build 12s, test 29s, native 92s, jdtls 57s);
a null passed where none is allowed made `check` fail with `[NullAway] passing @Nullable parameter 'null' where @NonNull
is required` in each of the four modules (reverted); with the marker removed the same code compiled. jdtls reports no
error on the annotated code, the `package-info.java` and the `requires static org.jspecify` descriptors included. The
branch was rebased by the supervisor on a `main` that had moved only in `logbook/` and `workflow.md`.

Review (one neutral reviewer, nothing blocking). Fixed by the supervisor: a comment of the `cli` build that said the
annotations are "only read at compile time" (they have runtime retention: "not needed at run time"), and two lines of
`quality.md` that were over 120 columns or dangling. Left: `com.uber.nullaway.annotations` is declared but required by no
module (nothing uses `@Contract` yet; the first ticket that does adds the `requires static`, and the next one, the shared
error package, will); its version entry is separate from `nullaway` on purpose, the catalog names a version after the
Java module. Error Prone now runs its default ERROR-level checks on the main code too, as in seed4j. Tests are not
checked by Error Prone (two `FutureReturnValueIgnored` warnings would appear in the daemon integration tests, out of
scope).
Unrelated and left: `val selfCheck by tasks.registering(Exec::class)` is deprecated in Gradle 9.6+ (breaks on Gradle
10); Kotlin in `build-logic` has no JDK 27 target (warning only).
