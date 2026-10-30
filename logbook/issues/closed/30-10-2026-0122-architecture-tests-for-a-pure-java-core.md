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

## Resolution

Done in 475833f, by a subagent. The test-only Gradle project `:architecture` holds `HexagonalArchTest` (ArchUnit 1.4.2),
adapted from the seed4j-generated test minus its Spring parts, the `wire` package rules, `@UnitTest` and the
annotation-based discovery of contexts. A context is a direct sub-package of the root package other than `shared`. 11
tests: no cycles between packages, the contexts off each other's domain, each context a hexagon, the domain on `java..`
and our own `shared` code only, the application off the infrastructure, primary and secondary adapters apart, and a
non-blank reason on every `@ExcludeFromGeneratedCodeCoverage`. Every rule tolerates the absence of matching classes,
since no hexagonal package exists yet. `:architecture` adds every project under `modules/` by itself, so a new module
needs no line.

Decisions of the user, applied here: `reason()` of the annotation has no default any more (omitting it does not
compile); `shared` is our own code, so the domain may use it; the allow-list only keeps third-party code out, and adding
one is the user's decision; the test follows the seed4j and Theano versions.

Checked by the subagent, `bin/ci`: format passed, build passed, test passed, native and jdtls skipped (no `cli` module,
no `jdtls` task yet); `protocol tests=4 failures=0`, architecture `tests=11 failures=0`. Proofs with throwaway classes
that were removed: a domain class on a third-party-like class fails; a blank reason fails; an omitted reason does not
compile; a cycle between two packages, nested or not, fails; each broken layer direction fails; a context's domain using
another context's domain fails; a secondary adapter of one context calling a primary adapter of another passes, as in
seed4j.

Neutral review, done once. What became of each finding:
- the allow-list matched any package with a `domain` segment, third-party ones included: it showed a `Done when` point
  was not met, sent back once, fixed (it is now scoped to our root package), checked by `bin/ci`;
- the cycle rule only saw first-level packages: same, now one slice per package;
- the toolchain of `:architecture` is fixed at Java 25, and ArchUnit 1.4.2 would silently skip Java 27 classes: no
  ticket, the user decided that every module stays on `--release 25`, the daemon included;
- the sanity test does not check that every module contributes classes: left, same reason;
- the primary, secondary and application rules are not scoped to a context, one rule repeats the layered one, and the
  static field `classes` hides the imported method: inherited from the seed4j template, left;
- `bin/ci` did not count the architecture tests: fixed by the supervisor, without a ticket (a very small task);
- two lines of the subagent's own docs (a 147-column line, a wrong results path): fixed by the subagent.

To remember:
- the root package is one constant in the test (`ROOT_PACKAGE`): the package rename, on hold, changes it there;
- ArchUnit is pinned to 1.4.2 while 1.5.1 exists;
- `slices().matching(ROOT_PACKAGE + ".(**)")` makes one slice per package, so a parent and its sub-package using each
  other is a cycle.
