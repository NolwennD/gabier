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
  one. The module Gradle projects are named after their folder under `modules/`: `:shared`, `:protocol`, `:core`, `:cli`, `:daemon`.
- The tools of the pre-commit hook are installed with `mise install` (hk, open-java-format); in a new worktree run
  `mise trust` first. The same command installs the pinned jdtls in `~/.local/share/jdtls/<version>` (shared by all
  worktrees, version in `mise.toml`); inside mise, `GABIER_JDTLS` already points to its launcher.
- The native CLI (`:cli:nativeCompile`, about a minute) needs a GraalVM 25 with `native-image`, which Gradle finds among
  the toolchains (the SDKMAN! candidates included); it is not part of `check`. The binary lands in
  `modules/cli/build/native/nativeCompile/gabier`. The `native` stage of `bin/ci` also installs the daemon
  (`:daemon:installDist`, `modules/daemon/build/install/gabier-daemon/bin/gabier-daemon`) and runs the native `gabier`
  against it.
- The test fixtures of `core` (`modules/core/src/testFixtures`: the fake language server and the contract scenarios, in
  the package `fr.craft.gabier.editing.fixtures`) are shared by the tests of every module: another module uses them with
  `testImplementation(testFixtures(project(":core")))` in its `build.gradle.kts`. They are test code (no coverage gate,
  not seen by the architecture tests) and they have no `module-info.java` (jdtls reports an error on a second descriptor
  in the same project): their jar gets a descriptor from a `module(...)` rule in `gabier.java-module-conventions`, which
  the build needs for any jar on the test runtime path, so the fixtures of another module need a rule of their own. The
  tests of the fixtures themselves are in `modules/core/src/test`.
- The jdtls adapter is in the main code of the daemon (`fr.craft.gabier.daemon`). `JdtlsSession` starts jdtls, waits
  until it is ready and ends its process tree on close (`JdtlsWatch`, `JdtlsClient`, `JdtlsLauncher`).
  `JdtlsLanguageServer` implements the port `JavaLanguageServer` on top of it (with `Diagnostics`, the store of what
  jdtls publishes, and `QuietWait`): it starts jdtls in the background and answers "not ready" until the start is over;
  it implements `diagnostics` and `rename` (`RenameChanges` turns the `WorkspaceEdit` of jdtls into a change set;
  `JdtlsRequests` tells a request jdtls declines from one that fails). jdtls does not watch the disk: it registers
  the globs it wants watched (`client/registerCapability`, only for a client that declares
  `workspace.didChangeWatchedFiles.dynamicRegistration`, `JdtlsCapabilities`), `WatchedFiles` keeps them with the state
  of the project folder, and before each request the adapter sends `workspace/didChangeWatchedFiles` for what changed
  and waits for jdtls to take it in (`FakeJdtls.registersWatchersFor`, `watched()`). Its unit tests (`modules/daemon/src/test`, run by `:daemon:test`)
  use `FakeJdtls`: a language server in the test process over pipes, with a `sleep` or `sh` child process standing in
  for the jdtls process. No jdtls is started. The daemon depends on the domain of the core (`requires
  fr.craft.gabier.core`): it is the composition root, and the architecture rule on the contexts exempts it only for the
  `editing` domain it implements (`IMPLEMENTED_DOMAINS` in `HexagonalArchTest`), not for the domain of any other context.
- The daemon listens on a Unix socket (`gabier-daemon`, no argument): `Main` resolves the path
  (`SocketLocation`, in `protocol`: `GABIER_SOCKET`, else `$XDG_RUNTIME_DIR/gabier/gabier.sock`, else
  `/tmp/gabier-<uid>/gabier.sock`, at most 107 bytes), `DaemonServer` takes the socket (`SocketGuard`: private folder,
  stale socket removed, another daemon refused) and serves the clients with `Connection` (lines in, lines out) and
  `RequestHandler` (the method table: `StatusMethod`, `StopMethod`, `DiagnosticsMethod`, `RenameMethod`,
  `RenameTokenMethod`; adding one is
  `.claude/skills/add-daemon-method`). The daemon serves one project (`ServedProject`: its root, its language server and
  where that is): `Main` takes the folder from its argument, `GABIER_PROJECT` or the working directory, and builds
  `ProjectServer.jdtls`, which starts the real jdtls (`JdtlsLanguageServer`, launcher `GABIER_JDTLS`) at the first request
  that needs it, once whatever the number of requests, waits for it (5 minutes at most, `jdtls-start-failed` otherwise,
  and the next request tries again, as it does for a jdtls that died while ready; a folder with no build is refused at
  once as `no-build-to-import`) and ends it when the daemon stops (`DaemonServer.close`: `stop`, SIGTERM). `status`
  shows the project and `not-started`, `starting`, `ready` or `failed`, and starts nothing. The `native` stage runs the cli
  against the installed daemon with `GABIER_JDTLS` pointing nowhere (a jdtls that cannot start, `status` shows `failed`)
  and against the installed daemon served by the fake language server instead (`./gradlew :daemon:installFakeServed`,
  `bin/gabier-daemon-fake <project folder>`, main class `FakeServedMain` in the source set `fakeServed`: a distribution of
  its own, on the class path, not under the coverage gate).
  Its unit tests need no jdtls: the lines are tested over streams (`RequestHandlerTest`, `ConnectionTest`), the socket
  on a real Unix socket in a temporary directory (`DaemonServerTest`, `SocketGuardTest`, `MainTest`), always through
  `@TempDir` and an explicit path: never the default path of the user, and keep the path short (107 bytes). The lazy
  start, its concurrency, its failure and retry and the stop are tested in `ProjectServerTest` with a counting starter and
  a hand-written language server (`ScriptedProject` stands for the project in `DaemonServerTest`). The cli is
  tested the same way against `ScriptedDaemon` (`modules/cli/src/test`). Only the `native` stage runs the real cli
  against the real daemon.
- The integration tests of the daemon (`modules/daemon/src/integrationTest`) all start a real jdtls and need
  `GABIER_JDTLS`, the path of the `jdtls` launcher (`mise exec -- ./gradlew jdtlsProbe` outside an activated mise); they
  fail at once, never skip, without it. `check` only compiles them. `./gradlew jdtlsProbe` runs them (`--tests` picks a
  class), and shows their output, which is the evidence:
  - `JdtlsProbeTest` starts jdtls on the checkout through `JdtlsLanguageServer`, in a fresh temporary `-data` directory,
    which waits, up to ten minutes in all for its waits together (about a minute and a half here), until jdtls has
    imported every module. Asked at once, it must refuse as not ready. It then asks the diagnostics of the
    `module-info.java` and the classes of each module (and the sources of `architecture`) in one request: jdtls
    publishes in stages, so the adapter answers the latest publication of each file once jdtls has published nothing
    for five seconds. No file may have an error; a file without any diagnostic, asked for alone, gets an empty list. A
    temporary broken source (`GabierProbeBroken.java`, deleted whatever happens) next to it must get its error, and a
    file outside the checkout is refused. It fails at once, with the exit code in the message, when jdtls exits, and at
    once on a `language/status` of type `Error`; a timeout says what was awaited and for how long.
  - `ScenarioReplayTest` replays the contract scenarios of the core (`Scenarios.all()`) that the adapter implements
    against a real jdtls started on each scenario's project (written in a temporary folder, with an Eclipse `.project`
    and `.classpath` that jdtls imports without network), and fails on any difference. The answer of a rename is
    compared exactly (jdtls merges the edits of a file, and the scenarios hold what it answered), must compile once
    applied to a copy of the project, and no request may change the sources. How to add one:
    `.claude/skills/add-scenario/SKILL.md`.
  - `InstalledDaemonTest` runs the installed daemon (`jdtlsProbe` depends on `:daemon:installDist`) on a small project
    (the scenario `FILE_WITH_AN_ERROR` and an Eclipse `.project`, `EclipseProject`), as a client on its socket: `status`
    is `not-started` and starts nothing, the first `diagnostics` starts jdtls and answers the error, `status` is `ready`,
    `stop` ends the daemon and every process jdtls started (watched by handle); a second test stops the daemon while
    jdtls still starts.
  `JdtlsSession` stops jdtls (the whole process tree) and deletes the data directory in every case. jdtls leaves its
  Eclipse files (`.project`, `.classpath`, `.settings/`, `bin/`) in the checkout: they are git-ignored.

## 1. Choose the scope

| Modified files                                                    | Command                                                                                                               |
| ----------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `modules/<m>/src/**` (main, `*Test.java`, `testFixtures`)         | `./gradlew :<m>:cleanTest :<m>:test --tests 'fr.craft.gabier.<package>.<Class>*'`                                       |
| `modules/daemon/src/integrationTest/**`, a scenario of the core   | `./gradlew :daemon:jdtlsProbe --tests 'fr.craft.gabier.daemon.<Class>'` (real jdtls, `GABIER_JDTLS`)                  |
| one method                                                        | `--tests 'fr.craft.gabier.<package>.<Class>.<method>'`; inside a `@Nested` class: `'…<Class>$<Nested>.<method>'`           |
| `module-info.java`, `build.gradle.kts`, `build-logic/**`          | `./gradlew build`: the modules are compiled as modules, so a forbidden dependency fails here                          |
| `settings.gradle.kts`, `gradle/libs.versions.toml`                | `./gradlew projects` to see the modules, then `./gradlew build`                                                       |
| packages created or moved                                         | the architecture tests: `./gradlew :architecture:cleanTest :architecture:test`                                        |
| `modules/cli/**`, `modules/daemon/**`, `libs.versions.toml` (the native plugin) | `./gradlew :cli:nativeCompile :daemon:installDist :daemon:installFakeServed`, then run the binary alone, in an empty directory with an empty environment: `--version`, a usage error as text and as JSON (`--json`), exit code 2, on the standard error, then `status` and `stop` against the installed daemon on a socket in a temporary directory, and `diagnostics` against the installed daemon (refused) and against `:daemon:installFakeServed` (the `native` stage of `bin/ci` does it) |
| a convention (name, Javadoc, declaration order, import)           | `./gradlew :<m>:checkstyleMain :<m>:checkstyleTest` (`:architecture:checkstyleTest`; also `checkstyleIntegrationTest`, `checkstyleTestFixtures`); the rules and what is left out: `config/checkstyle/checkstyle.xml` |
| any `.java` file                                                  | formatting: `mise exec -- hk check --all`; `hk fix --all` fixes it. Never bypass the hook                             |

`<package>` is the package of the class, which is not always the folder name: `protocol` for `:protocol`,
`editing.domain` for the classes of `:core`. `*` after the class name also selects its `@Nested` classes. Always write
`cleanTest` before `test`: see below.

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
- Checkstyle: the failure prints `[ant:checkstyle] [ERROR] <file>:<line>:<col>: <message> [<RuleName>]`; the report is
  `modules/<m>/build/reports/checkstyle/<main|test|integrationTest|testFixtures>.html` (`architecture/build/reports/checkstyle/`
  for `:architecture`). The messages follow the language of the JVM. Fix the code;
  a rule is dropped only with its reason written in `config/checkstyle/checkstyle.xml`.
- hk: a file listed as changed or modified means the formatting is off: `hk fix --all`.

## 3. Widen before concluding

The targeted test never closes a task: it misses what a changed class reaches without naming it. When you think the work
is ready, run the local CI on your worktree:

```bash
bin/ci                      # formatting, build, tests and gates, native cli, jdtls probe and scenario replay
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
