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
  `mise trust` first. The same command installs the pinned jdtls in `~/.local/share/jdtls/<version>` (shared by all
  worktrees, version in `mise.toml`); inside mise, `GABIER_JDTLS` already points to its launcher.
- The native CLI (`:cli:nativeCompile`, about a minute) needs a GraalVM 25 with `native-image`, which Gradle finds among
  the toolchains (the SDKMAN! candidates included); it is not part of `check`. The binary lands in
  `modules/cli/build/native/nativeCompile/gabier`.
- The jdtls probe, `./gradlew jdtlsProbe` (the integration test in `modules/daemon/src/integrationTest`), is not run by
  `check` (which only compiles that source set, so that it cannot rot): it starts a real jdtls on the checkout in a
  fresh temporary `-data` directory and waits, up to ten minutes in all for its three waits together (about a minute
  and a half here), until it has imported every module. It fails at once, with the exit code in the message, when jdtls
  exits, and at once on a `language/status` of type `Error`; a timeout says what was awaited and for how long. The
  source set also holds quick tests of that watching (`ProbeWatchTest`, `JdtlsClientTest`, with a `sleep` or `sh` child
  process standing in for jdtls); `./gradlew jdtlsProbe` runs them too, and `--tests` picks one class. It needs
  `GABIER_JDTLS`, the path of the `jdtls` launcher (`mise exec -- ./gradlew jdtlsProbe` outside an activated mise), and
  fails at once, never skips, without it. It stops jdtls and deletes the data directory in every case. jdtls leaves
  its Eclipse files (`.project`, `.classpath`, `.settings/`, `bin/`) in the checkout: they are git-ignored.

## 1. Choose the scope

| Modified files                                                    | Command                                                                                                               |
| ----------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `modules/<m>/src/**` (main or `*Test.java`)                       | `./gradlew :<m>:cleanTest :<m>:test --tests 'fr.craft.gabier.<m>.<Class>*'`                                           |
| one method                                                        | `--tests 'fr.craft.gabier.<m>.<Class>.<method>'`; inside a `@Nested` class: `'…<Class>$<Nested>.<method>'`           |
| `module-info.java`, `build.gradle.kts`, `build-logic/**`          | `./gradlew build`: the modules are compiled as modules, so a forbidden dependency fails here                          |
| `settings.gradle.kts`, `gradle/libs.versions.toml`                | `./gradlew projects` to see the modules, then `./gradlew build`                                                       |
| packages created or moved                                         | the architecture tests: `./gradlew :architecture:cleanTest :architecture:test`                                        |
| `modules/cli/**`, `libs.versions.toml` (the native plugin)        | `./gradlew :cli:nativeCompile`, then run the binary alone, in an empty directory with an empty environment            |
| any `.java` file                                                  | formatting: `mise exec -- hk check --all`; `hk fix --all` fixes it. Never bypass the hook                             |

`*` after the class name also selects its `@Nested` classes. Always write `cleanTest` before `test`: see below.

## 2. Read the output

- **A test task that ended `UP-TO-DATE` or `FROM-CACHE` did not run.** Its green is the previous one. Run `cleanTest`
  first, as in the table.
- Gradle: the `FAILED` line names `Class > Nested > method()`, then the `expected:` / `but was:` block and the first
  `at fr.craft.gabier…` line. The HTML report is `modules/<m>/build/reports/tests/test/index.html`.
- The numbers, per test class:
  `grep -ho 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*"' modules/<m>/build/test-results/test/*.xml`.
  The architecture tests are not under `modules/`: `architecture/build/test-results/test/*.xml`, and the report
  `architecture/build/reports/tests/test/index.html`.
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

The stages are the executable files of `bin/ci.d/`, named `NN-name` and run in that order: `10-format`, `20-build`,
`30-test`, `40-native`, `50-jdtls`. Each one is its own JEP 330 program, like the dev scripts of the sibling projects.
Adding a stage is adding a file; the contract (run in the current directory, exit 77 and a `skip: <reason>` line to say
it does not apply yet, `--describe` for its description) is in the header of `bin/ci`.

## 4. Report

Give the exact command that was run, the count (`tests="N" … failures="0"`), the worktree it ran in, and what was
**not** run. A red test is reported red, with the failing assertion.
