---
name: verify
description: Use when validating a change in this repo before claiming it works — after editing Java, a module-info or a Gradle file, when a test is red, or when the user asks to "run the tests", "check", "verify", "lance les tests".
argument-hint: '[<module>|all|<TestClass>|<TestClass.method>]'
---

# Verify a change

Principle: **the targeted test first, read to the end, then widen**. Never announce "green" without having seen the
number of tests run, the number of failures and `BUILD SUCCESSFUL` in the output. Do not use `-q`.

## Prerequisites

- JDK 27 (`.sdkmanrc`) and the wrapper `./gradlew` (Gradle 9.8.2). Run it from the worktree of the ticket when you are in
  one. The module Gradle projects are named after their folder under `modules/`: `:protocol`, `:cli`, `:daemon`.
- The tools of the pre-commit hook are installed with `mise install` (hk, open-java-format); in a new worktree run
  `mise trust` first.
- The native CLI (`:cli:nativeCompile`) needs a GraalVM 25, which Gradle finds in the SDKMAN! candidates.
- Integration tests against a real jdtls, once they exist, are not part of `check`: they need `GABIER_JDTLS`, the path of
  the `jdtls` launcher, and fail loudly without it.

## 1. Choose the scope

| Modified files                                                    | Command                                                                                                               |
| ----------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `modules/<m>/src/**` (main or `*Test.java`)                       | `./gradlew :<m>:cleanTest :<m>:test --tests 'fr.craft.gabier.<m>.<Class>*'`                                           |
| one method                                                        | `--tests 'fr.craft.gabier.<m>.<Class>.<method>'`; inside a `@Nested` class: `'…<Class>$<Nested>.<method>'`           |
| `module-info.java`, `build.gradle.kts`, `build-logic/**`          | `./gradlew build`: the modules are compiled as modules, so a forbidden dependency fails here                          |
| `settings.gradle.kts`, `gradle/libs.versions.toml`                | `./gradlew projects` to see the modules, then `./gradlew build`                                                       |
| packages created or moved                                         | the architecture tests, once they exist: `./gradlew :<m>:test --tests '*ArchTest'`                                    |
| `modules/cli/**`, once it exists                                  | `./gradlew :cli:nativeCompile`, then run the binary alone, in an empty directory with an empty environment            |
| any `.java` file                                                  | formatting: `mise exec -- hk check --all`; `hk fix --all` fixes it. Never bypass the hook                             |

`*` after the class name also selects its `@Nested` classes. Always write `cleanTest` before `test`: see below.

## 2. Read the output

- **A test task that ended `UP-TO-DATE` or `FROM-CACHE` did not run.** Its green is the previous one. Run `cleanTest`
  first, as in the table.
- Gradle: the `FAILED` line names `Class > Nested > method()`, then the `expected:` / `but was:` block and the first
  `at fr.craft.gabier…` line. The HTML report is `modules/<m>/build/reports/tests/test/index.html`.
- The numbers, per test class:
  `grep -ho 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*"' modules/<m>/build/test-results/test/*.xml`.
- `No tests found for given includes: [...]` is a wrong class or method name, not a green build.
- JPMS: `package … is not visible` or `module not found` names the module to look at. Fix `module-info.java` or the
  dependency; never reach for `--add-exports` or `--add-opens`.
- A JaCoCo failure reads `Rule violated for class …`: open the report under `modules/<m>/build/reports/jacoco/` or read
  the class to find the branch. An architecture test lists the classes that break the rule. Fix the code, never the rule.
- hk: a file listed as changed or modified means the formatting is off: `hk fix --all`.

## 3. Widen before concluding

The targeted test never closes a task: it misses what a changed class reaches without naming it. When you think the work
is ready, run the local CI on your worktree:

```bash
bin/ci                      # formatting, build, tests and gates, native cli, jdtls probe: the stages that apply
bin/ci --list               # the stages, and why one is skipped
bin/ci --stage test         # one stage
```

While mise does not trust the repository, a plain `java` fails (the shim of mise refuses `mise.toml`): run
`"$JAVA_HOME/bin/java" --source 25 bin/ci` instead. A skipped stage is reported as skipped, never as green. The summary
it prints goes in your report.

## 4. Report

Give the exact command that was run, the count (`tests="N" … failures="0"`), the worktree it ran in, and what was
**not** run. A red test is reported red, with the failing assertion.
