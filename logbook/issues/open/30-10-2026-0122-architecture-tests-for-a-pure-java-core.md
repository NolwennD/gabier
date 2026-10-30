# Architecture tests that keep the core pure Java

Opened: 30-10-2026 01:22

Add ArchUnit tests inspired by `chatbot`'s `HexagonalArchTest` and seed4j: the domain depends only
on `java..` (a short allow-list stays empty unless the user decides otherwise), the application does
not depend on infrastructure, and primary and secondary adapters do not depend on each other. JPMS
already enforces the edges between modules at compile time, so these tests cover the package rules
inside a module and the purity of the domain. One more rule: every use of
`@ExcludeFromGeneratedCodeCoverage` has a non-blank reason.

Done when:
- the tests run in `./gradlew check` and pass on the current modules;
- adding a forbidden import to a domain class, or an annotation with an empty reason, makes a test
  fail (try both, then undo).

Depends on: coverage-gate-with-exclusion-annotation.
