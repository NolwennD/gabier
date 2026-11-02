# Quality gates

Every ticket leaves these gates green. The subagent that does the ticket checks them with `bin/ci` before it asks for
the review (see the `ticket` skill). The formatting hook, the coverage gate, the architecture tests and Checkstyle are in
place.

- **Formatting is automatic**: Java files are formatted by
  [open-java-format](https://github.com/openjavaformat/open-java-format) (one style, 120 columns)
  from a pre-commit hook managed with [hk](https://github.com/jdx/hk). Never format by hand and
  never bypass the hook (`--no-verify`).

- **No agent attribution in the history** (decision of the user, 02-11-2026): no commit message names Claude or
  Anthropic (`Co-Authored-By: Claude …`, `Generated with Claude Code`). The project `.claude/settings.json` turns
  the attribution of Claude Code off, and the `commit-msg` hook of `hk.pkl` refuses such a line.

- **Checkstyle keeps the conventions the formatter cannot**: names, unused and star imports, modifier and declaration
  order (fields, constructors, methods; overloads together), no `TODO`, Javadoc form (summary sentence, order of the tags),
  and the bug-prone constructs. One configuration, `config/checkstyle/checkstyle.xml` (the Google style of Checkstyle,
  trimmed: the file says, rule by rule, what is left out and why, the layout above all, which is the formatter's), and
  `config/checkstyle/suppressions.xml` (the only suppressions: a file or a rule that is scoped out says why there). The
  Gradle tasks `checkstyleMain`, `checkstyleTest`, `checkstyleIntegrationTest` and `checkstyleTestFixtures` are part of
  `check` (so of the `test` stage of `bin/ci`), on the main and test code of every module and of `:architecture`, wired
  once in `gabier.checkstyle-conventions`; `build-logic` (Kotlin) and the Java scripts of `bin/ci.d` are not covered. Zero violation and zero warning. Checkstyle runs in the Gradle build, not on a
  module path; its version is a plain entry of the catalog (`checkstyle`). Fix the code, never the rule: a rule is
  dropped, or a file scoped out, only with the reason written in the configuration and challenged in the review. A
  `@SuppressWarnings("checkstyle:...")` needs a reason too.
- **Coverage is 100%**: no missed line and no missed branch, per class (JaCoCo, as in the
  `chatbot` project). Never lower the threshold to make a ticket pass.
- **Exclusion from coverage is the exception**: only for code too complicated to test, or very
  unlikely to ever happen. Annotate it with `@ExcludeFromGeneratedCodeCoverage(reason = "...")`.
  The reason is mandatory and says why: omitting it does not compile, and a blank one fails the architecture test.
  Code that is merely tedious to test is tested. The annotation's name must keep the word `Generated`: JaCoCo ignores
  what is annotated with such an annotation. Every use is challenged in the review.
- **The core stays almost pure Java**: the domain depends on `java..`, on our own
  `shared` code (`eu.gabier.shared..`) and on the null-safety annotations (`org.jspecify..`,
  `com.uber.nullaway.annotations..`, the user's decision of 30-10-2026), never on another third-party library or
  framework. Adding a third-party dependency to the domain is the user's decision, not an agent's: the allow-list is the
  constant `DOMAIN_ALLOWED_PACKAGES` in
  `architecture/src/test/java/eu/gabier/architecture/HexagonalArchTest.java`. These architecture tests (ArchUnit,
  adapted from the `HexagonalArchTest` that [seed4j](https://github.com/seed4j/seed4j) generates, without its Spring
  parts; the project `:architecture` sees the classes of every module) also keep the packages free of cycles, the
  contexts (the sub-packages of `eu.gabier` other than `shared`) off each other's domain (the `daemon`, composition
  root and home of the adapters of the real servers, is held to it too, except for the domains whose ports it
  implements: `editing` today, the constant `IMPLEMENTED_DOMAINS` of the same test), each
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
- **Unused code fails the compilation**: Error Prone's `UnusedMethod` (private methods) and `UnusedVariable` (private
  fields, local variables, and the parameters of private methods or of methods that nothing overrides) are errors on the
  main code, next to NullAway in `gabier.java-module-conventions`. Unused code is removed. A real exception is a
  `@SuppressWarnings` of one of these checks, under any of its names (`UnusedMethod`, `UnusedVariable`, and the aliases
  `UnusedParameters` and `unused`), with a comment that says why, challenged in the review. Naming a variable `unused...`
  (which Error Prone exempts) to get past the check is a suppression without a reason, so it is not done.
- **LSP4J stays in the daemon**: Eclipse LSP4J (`org.eclipse.lsp4j..`) is used by `eu.gabier.daemon..` only; an
  architecture test fails on any class outside it that depends on an LSP4J type. Its jars are automatic modules, given
  real descriptors by `extraJavaModuleInfo` in `gabier.java-module-conventions` (which fails the build on any other
  automatic module): a new third-party jar that is not a module is declared there, with its Maven coordinates in
  `gradle/modules.properties` and its version in the catalog under the name of its Java module. The task
  `:daemon:selfCheck` (part of `check`) runs the installed daemon on the module path.
- **Assertions are AssertJ's**: tests assert with `org.assertj.core.api.Assertions` (`assertThat(...)`,
  `assertThatThrownBy(...)`, ...), in idiomatic form. `org.junit.jupiter.api.Assertions` is not used, in the unit
  tests, the architecture tests and the `integrationTest` source set. JUnit stays the engine (`@Test`, `@Nested`).
- **Review before closing**: a neutral Java reviewer (a subagent; the supervisor does not read the code) reviews the change once
  before the ticket is closed, starting from the business cases the tests cover. A finding that
  shows a `Done when` point is not met is fixed in the ticket; any other problem becomes a ticket opened on the fly.
