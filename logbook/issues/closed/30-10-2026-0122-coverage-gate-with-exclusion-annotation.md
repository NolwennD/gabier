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

## Resolution

Done in 6b8603d, by a subagent. JaCoCo 0.8.15 is applied in the convention plugin: per class, no missed line and no
missed branch, wired into `check`. The verification always runs, on an empty execution file when a module has no test,
so a module with code and no test fails and an annotation-only module (`shared`) passes. The annotation lives in the new
module `fr.craft.gabier.shared`, package `fr.craft.gabier.shared.generation`, Java 25.

Checked by the subagent, `bin/ci`: format passed, build passed, test passed (`protocol tests=4 skipped=0 failures=0`),
native and jdtls skipped (no `cli` module, no `jdtlsProbe` task yet). It also showed the gate failing without tests
(`branches missed count is 4`, `lines missed count is 6`) and a marked method not counted.

Neutral review, done once. What became of each finding:
- a module with code and no test passed the gate: it showed a `Done when` point was not met, sent back once and fixed
  in this ticket, checked by `bin/ci`, not reviewed again;
- `reason() default ""` makes the reason optional at compile time: not changed, the annotation keeps the shape the user
  gave; the non-blank check is the job of the architecture tests; to decide: drop the default so that forgetting the
  reason does not compile?
- `finalizedBy(jacocoTestReport)` on every `Test` task would mix a later integration suite into the unit report, and two
  `dependsOn` are redundant: opened as an issue on the fly;
- the toolchain block is duplicated in `protocol` and `shared`: in the same issue;
- the architecture tests will have to admit `fr.craft.gabier.shared..` in the domain's allow-list: noted in that issue;
- Javadoc on the annotation: a matter of taste, left.

To remember:
- the version catalog is not registered yet when the convention plugin is applied from `settings.gradle.kts`, hence the
  `afterEvaluate` to read the JaCoCo version;
- a module compiled for JDK 27 cannot be required by a Java 25 one, so each module sets its own toolchain and release;
- `build-logic` prints "Kotlin does not yet support 27 JDK target" at every build: only a warning.
