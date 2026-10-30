# The shared error package of seed4j is in gabier

Opened: 30-10-2026 10:33

Copy the package `../seed4j/src/main/java/com/seed4j/shared/error/domain/` as it is, with its tests, into the `shared`
module as `fr.craft.gabier.shared.error.domain` (`Assert`, `AssertionException`, the `...Exception` classes,
`ErrorKey`, `StandardErrorKey`, `ErrorStatus`...). The user's decision: the code is meant to be copied, seed4j generates
it into every application it creates. Only what the Spring and web side of seed4j adds (the `infrastructure` packages)
is left out. The domain of gabier then validates its values with `Assert` in the compact constructors of its records.
Adapt only what cannot compile here (packages, test annotations of seed4j that gabier does not have, AssertJ in the
tests); keep the rest unchanged so that a later diff with seed4j stays readable, and say in the resolution what changed.

Done when:
- the package and its tests are in `shared`, with the coverage at 100% on the classes kept, and any exclusion
  annotated with its reason (or the class removed, if nothing in gabier can use it, saying why);
- the module `shared` exports the package, the architecture tests accept it, and the native image still builds;
- one existing place of gabier that validates a value by hand, if there is one, uses `Assert`; otherwise none is
  invented.

Depends on: nullaway-and-jspecify-on-every-module (the code uses `@Nullable` and `@Contract`).

## Resolution

Done in 85e1fa9. The 19 classes of `com.seed4j.shared.error.domain` are in `shared` as `fr.craft.gabier.shared.error.domain`
(with a `@NullMarked` package-info), exported by the module, and `shared` has `requires static
com.uber.nullaway.annotations` (the jar is a real module, no `extraJavaModuleInfo` entry). The neutral reviewer checked
the copy against seed4j: identical once whitespace is stripped and the package renamed. `ProtocolVersion` validates with
`Assert.field("major"|"minor", ...).positive()` (the only place that validated by hand); its exception is now
`NumberValueTooLowException` and `protocol` really requires `shared`, so the cli reaches `shared` at run time through
`protocol`. `ProtocolVersionTest` is stricter than before (type, field name, parameters).

What differs from seed4j: package `fr.craft.gabier` and the formatting of open-java-format; `GeneratorException.inProject()`
tests the prefix `fr.craft.gabier` (a behaviour-bearing change: the status heuristic looks for the first project class
of the stack, and a `.primary` package gives BAD_REQUEST); `@UnitTest` removed from the three copied tests (already
AssertJ, unchanged); `GeneratorExceptionFactory` of the left-out `infrastructure.primary` recreated in the test sources of
`shared` only; `ErrorKeyTest` moved to `:architecture` and rewritten with ArchUnit instead of the Reflections library
(same two assertions, an explicit type witness because jdtls rejected the inferred generics, and a guard test
`shouldFindTheKeys`); two test classes of ours (`AssertionExceptionTest`, `GeneratorExceptionBuildersTest`) cover what
seed4j's tests leave open, written after the copied code and guided by the JaCoCo report, not test first. The Error Prone
warnings of the copied code (`UnnecessaryStringBuilder`, `UnnecessaryLambda`) are left to keep the diff with seed4j
readable. No exclusion annotation, no threshold changed.

Checked by the subagent: `bin/ci` green after the `ff1c4af` fix, jdtls stage run (format 2s, build 3s, test 31s,
native 3s up to date after a 98s build in a first run, jdtls 46s); counts: cli 9, daemon 22, daemon quick probe tests
43, protocol 4, shared 236, architecture 16, no failure; the native binary prints `gabier 0.1` in an empty environment;
jdtls reports nothing on the new files.

Review (one neutral reviewer, nothing blocking). Fixed by the supervisor: `java.util.Arrays` imported in
`AssertionExceptionTest`. Left: `shouldFindTheKeys` is satisfied by `StandardErrorKey` alone (it proves the scan is not
empty, not that every module is scanned), and keys defined in test code are no longer checked, unlike in seed4j.
