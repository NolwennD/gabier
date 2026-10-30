# Quality gates

Every ticket leaves these gates green. The subagent that does the ticket checks them with `bin/ci` before it asks for
the review (see the `ticket` skill). The formatting hook, the coverage gate and the architecture tests are in place.

- **Formatting is automatic**: Java files are formatted by
  [open-java-format](https://github.com/openjavaformat/open-java-format) (one style, 120 columns)
  from a pre-commit hook managed with [hk](https://github.com/jdx/hk). Never format by hand and
  never bypass the hook (`--no-verify`).

- **Coverage is 100%**: no missed line and no missed branch, per class (JaCoCo, as in the
  `chatbot` project). Never lower the threshold to make a ticket pass.
- **Exclusion from coverage is the exception**: only for code too complicated to test, or very
  unlikely to ever happen. Annotate it with `@ExcludeFromGeneratedCodeCoverage(reason = "...")`.
  The reason is mandatory and says why: omitting it does not compile, and a blank one fails the architecture test.
  Code that is merely tedious to test is tested. The annotation's name must keep the word `Generated`: JaCoCo ignores
  what is annotated with such an annotation. Every use is challenged in the review.
- **The core stays almost pure Java**: the domain depends on `java..`, on our own
  `shared` code (`fr.craft.gabier.shared..`) and on the null-safety annotations (`org.jspecify..`,
  `com.uber.nullaway.annotations..`, the user's decision of 30-10-2026), never on another third-party library or
  framework. Adding a third-party dependency to the domain is the user's decision, not an agent's: the allow-list is the
  constant `DOMAIN_ALLOWED_PACKAGES` in
  `architecture/src/test/java/fr/craft/gabier/architecture/HexagonalArchTest.java`. These architecture tests (ArchUnit,
  adapted from the `HexagonalArchTest` that [seed4j](https://github.com/seed4j/seed4j) generates, without its Spring
  parts; the project `:architecture` sees the classes of every module) also keep the packages free of cycles, the
  contexts (the sub-packages of `fr.craft.gabier` other than `shared`) off each other's domain (the `daemon`, composition
  root and home of the adapters of the real servers, is the one that may depend on the domain of the others), each
  context a hexagon (domain, application, primary and secondary adapters, pointing towards the domain), the application
  off the infrastructure and the primary and secondary adapters apart, next to the module boundaries that JPMS already
  enforces at compile time.
- **Null safety is checked at compile time**: JSpecify annotations, checked by NullAway through Error Prone (configured
  once, in `gabier.java-module-conventions`, on the main code; `NullAway:ERROR`, `JSpecifyMode`, `OnlyNullMarked`).
  Everything is non-null by default: every package has a `package-info.java` annotated
  `@org.jspecify.annotations.NullMarked` (an architecture test fails on a package that has classes and none, since
  NullAway silently skips what is not marked), and the module-info requires `org.jspecify` with `requires static`.
  Whatever accepts or may return null says so with `@org.jspecify.annotations.Nullable`, on the type usage (a
  parameter, a return type, a field, a type argument): JSpecify does not annotate a method or a class as such. No
  suppression (`@SuppressWarnings("NullAway")`) without a reason that says why, challenged in the review. A new
  third-party jar follows the same declaration as the others (catalog, `gradle/modules.properties`).
- **LSP4J stays in the daemon**: Eclipse LSP4J (`org.eclipse.lsp4j..`) is used by `fr.craft.gabier.daemon..` only; an
  architecture test fails on any class outside it that depends on an LSP4J type. Its jars are automatic modules, given
  real descriptors by `extraJavaModuleInfo` in `gabier.java-module-conventions` (which fails the build on any other
  automatic module): a new third-party jar that is not a module is declared there, with its Maven coordinates in
  `gradle/modules.properties` and its version in the catalog under the name of its Java module. The task
  `:daemon:selfCheck` (part of `check`) runs the installed daemon on the module path.
- **Assertions are AssertJ's**: tests assert with `org.assertj.core.api.Assertions` (`assertThat(...)`,
  `assertThatThrownBy(...)`, ...), in idiomatic form. `org.junit.jupiter.api.Assertions` is not used, in the unit
  tests, the architecture tests and the `integrationTest` source set. JUnit stays the engine (`@Test`, `@Nested`).
- **Modern Java**: write idiomatic code for the release of the module (records, sealed types,
  pattern matching, sequenced collections, and what the release adds), not older idioms.
- **Review before closing**: a neutral Java reviewer (a subagent; the supervisor does not read the code) reviews the change once
  before the ticket is closed, starting from the business cases the tests cover. A finding that
  shows a `Done when` point is not met is fixed in the ticket; any other problem becomes a ticket opened on the fly.
