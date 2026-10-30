# A 100% coverage gate with an exclusion annotation

Opened: 30-10-2026 01:22

Add JaCoCo to the convention plugin so the build fails when any class has a missed line or a missed
branch, as `chatbot` does in its `pom.xml`. Add the `@ExcludeFromGeneratedCodeCoverage(reason)`
annotation (same shape as in `chatbot` and seed4j: runtime retention, on methods, types and
constructors) in a module the others can require. Its name must keep "Generated" so JaCoCo ignores
what it marks. See `.claude/rules/quality.md` for when it may be used.

Done when:
- `./gradlew check` runs the gate, and it fails when a line or a branch is left uncovered (try by
  removing a test, then put it back);
- the `protocol` module passes at 100%;
- a method marked with the annotation is not counted, shown once on a throwaway example that is
  not kept.
