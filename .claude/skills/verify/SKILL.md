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
  the package `eu.gabier.editing.fixtures`) are shared by the tests of every module: another module uses them with
  `testImplementation(testFixtures(project(":core")))` in its `build.gradle.kts`. They are test code (no coverage gate,
  not seen by the architecture tests) and they have no `module-info.java` (jdtls reports an error on a second descriptor
  in the same project): their jar gets a descriptor from a `module(...)` rule in `gabier.java-module-conventions`, which
  the build needs for any jar on the test runtime path, so the fixtures of another module need a rule of their own. The
  tests of the fixtures themselves are in `modules/core/src/test`.
- The jdtls adapter is in the main code of the daemon (`eu.gabier.daemon`). `JdtlsSession` starts jdtls, waits
  until it is ready and ends its process tree on close (`JdtlsWatch`, `JdtlsClient`, `JdtlsLauncher`).
  `JdtlsLanguageServer` implements the port `JavaLanguageServer` on top of it (with `Diagnostics`, the store of what
  jdtls publishes, and `QuietWait`): it starts jdtls in the background and answers "not ready" until the start is over;
  it implements `diagnostics`, `rename`, `organizeImports` (the code action `source.organizeImports`, resolved when
  jdtls does not give its edit; `WorkspaceEdits` turns the `WorkspaceEdit` of jdtls, as `documentChanges` or as `changes`,
  into a change set; `JdtlsRequests` tells a request jdtls declines from one that fails) and `extractVariable` (jdtls
  cannot take the name: `java/getRefactorEdit`, from the interface `JdtlsServer`, gives the edit with a name of its own and
  where it is; the adapter applies the edit in memory with `SourceText`, opens that text in jdtls for the time of a
  `textDocument/rename` to the name asked for, and answers one edit on the file as it is on disk) and `extractMethod` (the
  same flow with the command `extractMethod`; jdtls lets a name through that another method of the class has with the same
  parameters, so the adapter asks `textDocument/definition` for where the new method is declared, lists the methods of the opened text with `textDocument/documentSymbol` (the symbol that holds that place, the labels `name(parameters)`) and refuses it) and `extractConstant` (the same flow with the command `extractConstant`; jdtls answers nothing, and no message, for an expression that cannot be a constant) and `extractField` (the same flow with the command `extractField`, jdtls initialising the field where it chooses by default) and `moveTypeToFile` (`java/move` with the move kind `moveTypeToNewFile`, from the interface `JdtlsServer`: the edit comes back as `documentChanges` that create the file and give its text; jdtls refuses with a message and no edit, which the adapter maps to `not-movable`), and `inline` (the code action `refactor.inline` at the position, resolved for its edit; jdtls gives "Make static" the same kind, so the adapter takes the action titled "Inline ..."; no action, or one with no edit, is `not-inlinable`), and `cleanUp` (the setting `java.cleanup.actions` set with `workspace/didChangeConfiguration` to the identifiers asked for, in order, then `java/cleanup` from the interface `JdtlsServer`, then the setting set back to none; jdtls answers one edit of the whole text, none when nothing changes, and only logs an identifier it does not know, so `CleanUp` of the core refuses it before) and `createType` (the file opened in jdtls with no text, never written, and the snippet of the kind that `textDocument/completion` at 0:0 gives, with the `package` line jdtls derives from the path; the adapter asks `java.project.listSourcePaths` and refuses a file under no source folder as `not-creatable`) and `codeActions` and `codeActionEdit` (`textDocument/codeAction` with the diagnostics of the range, which the adapter takes from the publication of the file it opens, as the context, `only` set to `quickfix`, `refactor` and `quickassist` (jdtls's own kind, declared by the client in `JdtlsCapabilities`, without which jdtls drops the rewrites) so that the `source` actions of a whole file are left out; the edit of the action of the same kind and title, resolved with `codeAction/resolve`; no edit once resolved is `not-applicable`) and `references` (`textDocument/prepareRename` first, as the gate that tells a symbol from a blank or a comment, for which `textDocument/references` would answer the references of the enclosing member; then `textDocument/references`, asked a second time with the declaration when it is wanted, the places that are new being the declaration; `java.references.includeAccessors` and `includeDecompiledSources` are false). jdtls is started with the
  `java.*` preferences of `JdtlsSettings.javaPreferences` (`initializationOptions.settings`: the types it never offers,
  no comment on a created type, no accessors in the references). The imports are not among them: jdtls reads no file of
  the project for them, so before each request that may write imports the adapter reads `.settings/org.eclipse.jdt.ui.prefs`
  and `.vscode/settings.json` (`ImportPreferences`, `.settings` first) and gives jdtls the three settings that differ from
  what it has (`workspace/didChangeConfiguration`, `JdtlsSettings.imports`; a key that is missing leaves jdtls the value it
  has, so all three are sent, the default of jdtls for what no file sets); `status` shows them, with their source. jdtls does not watch the disk: it registers
  the globs it wants watched (`client/registerCapability`, only for a client that declares
  `workspace.didChangeWatchedFiles.dynamicRegistration`, `JdtlsCapabilities`), `WatchedFiles` keeps them with the state
  of the project folder, and before each request the adapter sends `workspace/didChangeWatchedFiles` for what changed
  and waits for jdtls to take it in (`FakeJdtls.registersWatchersFor`, `watched()`). Its unit tests (`modules/daemon/src/test`, run by `:daemon:test`)
  use `FakeJdtls`: a language server in the test process over pipes, with a `sleep` or `sh` child process standing in
  for the jdtls process. No jdtls is started. The daemon depends on the domain of the core (`requires
  eu.gabier.core`): it is the composition root, and the architecture rule on the contexts exempts it only for the
  `editing` domain it implements (`IMPLEMENTED_DOMAINS` in `HexagonalArchTest`), not for the domain of any other context.
- The daemon listens on a Unix socket (`gabier-daemon`, no argument): `Main` resolves the path
  (`SocketLocation`, in `protocol`: `GABIER_SOCKET`, else `$XDG_RUNTIME_DIR/gabier/<project>-<hash>.sock`, else
  `/tmp/gabier-<uid>/<project>-<hash>.sock`, the project being `GABIER_PROJECT` or the nearest folder with a `.git` above
  the working directory, at most 107 bytes), `DaemonServer` takes the socket (`SocketGuard`: private folder,
  stale socket removed, another daemon refused) and serves the clients with `Connection` (lines in, lines out) and
  `RequestHandler` (the method table: `StatusMethod`, `StopMethod`, `DiagnosticsMethod`, `RenameMethod`,
  `RenameTokenMethod`, `OrganizeImportsMethod`, `CleanUpMethod`, `ExtractVariableMethod`, `ExtractMethodMethod`, `ExtractConstantMethod`, `ExtractFieldMethod`, `MoveTypeToFileMethod`, `InlineMethod`, `CreateTypeMethod`, `ReferencesMethod`, `CodeActionsMethod`, `ApplyCodeActionMethod`; adding one is
  `.claude/skills/add-daemon-method`). The daemon serves one project (`ServedProject`: its root, its language server and
  where that is): `Main` takes the folder from its argument, `GABIER_PROJECT` or the working directory, and builds
  `ProjectServer.jdtls`, which starts the real jdtls (`JdtlsLanguageServer`; launcher found by `JdtlsLocation`: `GABIER_JDTLS`, else `~/.local/share/jdtls/<version>/bin/jdtls` with the version of `mise.toml` written by the build in `jdtls.properties`, else `jdtls` on the `PATH`) at the first request
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
  tested the same way against `ScriptedDaemon` (`modules/cli/src/test`; the command line, its commands and their tests are in
  `eu.gabier.cli.infrastructure.primary`, only `Gabier` is in `eu.gabier.cli`). The cli reaches the daemon through the port
  `eu.gabier.cli.domain.Daemon`, whose adapter `SocketDaemon` is in `eu.gabier.cli.infrastructure.secondary` (with
  `DaemonClient`, `DaemonLauncher`, `DaemonException` and `WaitNotice`). The cli starting the daemon (`DaemonLauncher`:
  next to its own binary, else on the `PATH`; log next to the socket; 20 seconds to listen) is tested with real
  processes: shell scripts named `gabier-daemon` (`FakeDaemonScripts`), one of which runs `FakeDaemonMain`, a daemon of
  the test classes on a real socket (`DaemonLauncherTest`, in the package of the adapter, and `StartingTheDaemonTest`). Only the `native` stage runs the real cli
  against the real daemon.
- The integration tests of the daemon (`modules/daemon/src/integrationTest`) all start a real jdtls and need
  `GABIER_JDTLS`, the path of the `jdtls` launcher (`mise exec -- ./gradlew jdtlsProbe` outside an activated mise); they
  fail at once, never skip, without it. `check` only compiles them. `./gradlew jdtlsProbe` runs them (`--tests` picks a
  class), and shows their output, which is the evidence:
  - `ScenarioReplayTest` replays the contract scenarios of the core (`Scenarios.all()`) that the adapter implements
    against a real jdtls started on each scenario's project (written in a temporary folder, with an Eclipse `.project`
    and `.classpath` that jdtls imports without network; a scenario whose files are under several folders that each hold
    a `src` is several projects, linked as their `module-info.java` require each other, `EclipseProject.describeProjects`),
    and fails on any difference. The answer that is a change
    set is compared by its effect (`Effect`: the sources of the project once it is applied to a copy, equal to the ones
    the scenario's set leaves, and compiled by javac; the edits themselves are not compared), a diagnostic by its
    severity and its position (not its message), the titles of the offered actions exactly, and no request may change the
    sources. The exact answer of jdtls is tested in `modules/daemon/src/test` against `FakeJdtls`; what no replay can show
    is `@FakeOnly(reason = "...")` there (`FakeOnlyGuard` fails an unlabelled test that makes `FakeJdtls` play a failure,
    `FakeOnlyReasonTest` a blank reason); `JdtlsCouplingTest` (core) counts what still couples the
    scenarios to jdtls against ceilings that only go down, and `bin/ci` prints the figures (stage `test`). The organization of the imports is replayed
    on a project that includes `Wide`, which imports 100 types of one package and 100 constants of one class, and whose
    `.vscode/settings.json` is the convention of this repository (the scenarios that need other settings bring their own
    files, `.settings/org.eclipse.jdt.ui.prefs` or another `.vscode/settings.json`): with the default preferences of jdtls
    the replay fails (a star import, groups apart), which is what proves that the files are followed. The sequel
    `organize-imports-after-a-setting-is-removed` changes `.vscode/settings.json` on the running jdtls, and shows that a
    setting a file no longer gives goes back to the default of jdtls. The small cases
    of the adapter's answers are scenarios too: not ready while jdtls imports (`diagnostics-while-the-server-imports`, asked
    before the import is over), a file without a diagnostic and a file with an error (`diagnostics-of-a-clean-file`,
    `diagnostics-of-a-file-with-an-error`), a file outside the project (`diagnostics-of-a-file-outside-the-project`), the
    imports removed or refused (`organize-imports-*`); an organization that loses no code, asked a second time, takes less
    than the five seconds of the diagnostics. jdtls is not made to import this repository: that it works on gabier is
    proven by using gabier on gabier (`.claude/rules/dogfooding.md`). How to add one: `.claude/skills/add-scenario/SKILL.md`.
  - `ExtractedMethodSymbolsTest` asks a real jdtls what the name check of `extract-method` reads (the introduced name at the call, its definition at the declaration of the new method, the labels `name(parameters)` of `textDocument/documentSymbol`) and fails if that changes.
  - `InstalledDaemonTest` runs the installed daemon (`jdtlsProbe` depends on `:daemon:installDist`) on a small project
    (the scenario `FILE_WITH_AN_ERROR` and an Eclipse `.project`, `EclipseProject`), as a client on its socket: `status`
    is `not-started` and starts nothing, the first `diagnostics` starts jdtls and answers the error, `status` is `ready`,
    `stop` ends the daemon and every process jdtls started (watched by handle); a second test stops the daemon while
    jdtls still starts.
  - `SharedIndexesReuseTest` starts two jdtls on projects that use one jar and one shared folder of indexes
    (`-Djdt.core.sharedIndexLocation`, `SharedIndexes`): the second builds none of the indexes of the first.
    `WorkspaceLockOfJdtlsTest` shows that a real jdtls takes no lock on its data folder, and that the daemon's own
    (`WorkspaceLock`, `.gabier-lock`) is held while it runs.
  `JdtlsSession` stops jdtls (the whole process tree) and deletes the data directory in every case (`JdtlsWorkspace`). jdtls leaves its
  Eclipse files (`.project`, `.classpath`, `.settings/`, `bin/`) in a folder it is started on: when that is the checkout,
  they are git-ignored.

## 1. Choose the scope

| Modified files                                                    | Command                                                                                                               |
| ----------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `modules/<m>/src/**` (main, `*Test.java`, `testFixtures`)         | `./gradlew :<m>:cleanTest :<m>:test --tests 'eu.gabier.<package>.<Class>*'`                                       |
| `modules/daemon/src/integrationTest/**`, a scenario of the core   | `./gradlew :daemon:jdtlsProbe --tests 'eu.gabier.daemon.<Class>'` (real jdtls, `GABIER_JDTLS`)                  |
| one method                                                        | `--tests 'eu.gabier.<package>.<Class>.<method>'`; inside a `@Nested` class: `'…<Class>$<Nested>.<method>'`           |
| `module-info.java`, `build.gradle.kts`, `build-logic/**`          | `./gradlew build`: the modules are compiled as modules, so a forbidden dependency fails here                          |
| `settings.gradle.kts`, `gradle/libs.versions.toml`                | `./gradlew projects` to see the modules, then `./gradlew build`                                                       |
| packages created or moved                                         | the architecture tests: `./gradlew :architecture:cleanTest :architecture:test`                                        |
| `modules/cli/**`, `modules/daemon/**`, `libs.versions.toml` (the native plugin) | `./gradlew :cli:nativeCompile :daemon:installDist :daemon:installFakeServed`, then run the binary alone, in an empty directory with an empty environment: `--version`, a usage error as text and as JSON (`--json`), exit code 2, on the standard error, `gabier mcp` over a pipe (`server/discover` with the instructions, `tools/list`, the `guide` tool; the instructions are a resource, so this is what proves it is in the image), then `status` and `stop` against the installed daemon on a socket in a temporary directory, and `diagnostics` against the installed daemon (refused) and against `:daemon:installFakeServed` (the `native` stage of `bin/ci` does it) |
| a convention (name, Javadoc, declaration order, import)           | `./gradlew :<m>:checkstyleMain :<m>:checkstyleTest` (`:architecture:checkstyleTest`; also `checkstyleIntegrationTest`, `checkstyleTestFixtures`); the rules and what is left out: `config/checkstyle/checkstyle.xml` |
| any `.java` file                                                  | formatting: `mise exec -- hk check --all`; `hk fix --all` fixes it. Never bypass the hook                             |

`<package>` is the package of the class, which is not always the folder name: `protocol` for `:protocol`,
`editing.domain` for the classes of `:core`. `*` after the class name also selects its `@Nested` classes. Always write
`cleanTest` before `test`: see below.

## 2. Read the output

- **A test task that ended `UP-TO-DATE` or `FROM-CACHE` did not run.** Its green is the previous one. Run `cleanTest`
  first, as in the table.
- Gradle: the `FAILED` line names `Class > Nested > method()`, then the `expected:` / `but was:` block and the first
  `at eu.gabier…` line. The HTML report is `modules/<m>/build/reports/tests/test/index.html`.
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
`30-test`, `35-release-install` (the `install.sh` of a release on fake files), `36-release-notes` (the notes of a release,
and the refusal of a version with no `docs/releases/<version>.md`, with no tag), `40-native`, `50-jdtls`. Each one is its
own JEP 330 program, like the dev scripts of the sibling projects.
Adding a stage is adding a file; the contract (run in the current directory, exit 77 and a `skip: <reason>` line to say
it does not apply yet, `--describe` for its description) is in the header of `bin/ci`.

## 4. Report

Give the exact command that was run, the count (`tests="N" … failures="0"`), the worktree it ran in, and what was
**not** run. A red test is reported red, with the failing assertion.
