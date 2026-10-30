# Tidy the JaCoCo setup in the convention plugin

Opened: 30-10-2026 02:14

Three small points the review of the coverage gate raised, to settle in the convention plugin once:
- `finalizedBy(jacocoTestReport)` is set on every `Test` task, so a later integration suite would be reported with the
  unit tests' data: scope it to the unit `test` task;
- the two `dependsOn(tasks.test)` on the report and the verification are redundant, the jacoco plugin already wires
  that dependency through the execution data;
- the toolchain block is written in both `protocol` and `shared`, and the Java 27 daemon will make a third: move it into
  the convention plugin, with the release chosen per module.

Done when:
- `bin/ci` passes;
- the gate still fails when a module has code and no test, and still ignores a method marked with the annotation (same
  two proofs as the coverage ticket, on throwaway examples that are not kept);
- the module build files no longer repeat the toolchain block.

Depends on: architecture-tests-for-a-pure-java-core, which also edits the build files.
