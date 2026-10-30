# Architecture tests that keep the core pure Java

Opened: 30-10-2026 01:22

Add ArchUnit tests inspired by `chatbot`'s `HexagonalArchTest`, by the `HexagonalArchTest` of the user's Theano project
(pasted by the user: cycles, layers, contexts) and by seed4j: the domain depends only on `java..` and on our own code,
never on a third-party library or framework (adding one to the allow-list is the user's decision), the application does
not depend on infrastructure, primary and secondary adapters do not depend on each other, the layers follow the
hexagonal direction, and the packages are free of cycles. JPMS already enforces the edges between modules at compile
time, so these tests cover the package rules inside a module and the purity of the domain. One more rule: every use of
`@ExcludeFromGeneratedCodeCoverage` has a non-blank reason, and the annotation has no default reason. That annotation
lives in the `shared` module (`fr.craft.gabier.shared..`), which is our own code: the domain may use it.

Done when:
- the tests run in `./gradlew check` and pass on the current modules;
- adding a forbidden import to a domain class, or an annotation with an empty reason, makes a test
  fail (try both, then undo);
- a dependency cycle between two packages, or a layer that breaks the hexagonal direction, makes a test fail (throwaway
  examples, then undo).

Depends on: coverage-gate-with-exclusion-annotation.
