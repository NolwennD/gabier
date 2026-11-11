# gabier lists the tests that a change affects

Opened: 11-11-2026 16:16

An agent that changed Java files can ask which tests to run again, with no Maven or Gradle and whatever the build tool of
the project (Infinitest does it for a developer in an IDE: a test class that changes, or an application class that a test
depends on). The command answers a list of test classes; running them is not in this ticket. This ticket may end in a dead
end: if jdtls does not give what is needed for a kind of project, the worker stops, says so, and that is the result.

Done when:
- Observed first on the real jdtls, with the settings of gabier (`JdtlsSettings`), on a small Maven project, a Gradle
  project and a project with no build tool, and written in the resolution: where jdtls writes the `.class` files, the
  classpath it gives (`java.project.getClasspaths` is not asked today), how fresh the classes are after an edit made on
  disk (a measured delay) and what happens when the compilation fails. If for a kind of project jdtls writes no up-to-date
  `.class`, the worker stops and reports to the supervisor: no workaround is built (`.claude/rules/workflow.md`, "jdtls
  is never fought").
- For given source files the command answers the test classes affected: a changed test class is affected itself; a changed
  application class affects the tests that depend on it, directly or not (a graph of the dependencies read in the
  bytecode, with `java.lang.classfile` of the JDK, no third-party library in the daemon); a test whose dependencies
  cannot be computed is handled as Infinitest handles it (`ClassFileIndex.findDynamicTestClasses` in the clone of
  Infinitest the user gives); JUnit 4, JUnit 5 and TestNG tests are recognised.
- The filters of an `infinitest.filters` file of the project (the `include` and `exclude` lines, regular expressions on
  the whole class name) leave out the slow tests, such as the ones named `*IT`; the groups lines are left out of the
  scope, and the page says so.
- A file whose compilation failed is not answered as if nothing were affected: the refusal or the warning says why, in the
  shape of the other refusals.
- The daemon method, the cli command, the MCP tool, the page of the command, its help and the guide (`gabier help
  --for`) say it (skills `add-daemon-method`, `extend-guide`); a contract scenario is scripted into the fake first and
  replayed on the real jdtls (skill `add-scenario`).
- The native image of the cli stays built (`bin/ci --stage native` green); the selection and the filters live in the
  daemon, not in the cli.

## Resolution

Done in eb6cd56 (PR 48): `gabier affected-tests <file>...`, daemon method `affected-tests`, MCP tool `affected-tests`, a new
context `eu.gabier.testing` in the core. Not a dead end: jdtls gives what is needed.

- **Observed on the real jdtls (1.63.0), before any code, no `java.*` setting needed (`JdtlsSettings` unchanged):**
  - output folders: Eclipse `bin` and `test-bin`, Gradle `bin/main` and `bin/test`, Maven `target/classes` and
    `target/test-classes`; the source and output folders come from `java.project.getSettings`, the jars and output folders of
    the test scope from `java.project.getClasspaths` with the scope `test`;
  - freshness: after an edit made on disk, about 1.1 s per call (1.9 to 3.4 s the first), the class already rewritten;
  - a compilation that fails: jdtls still writes a reduced class (a `Greeter.class` of 908 bytes became 513) and publishes the
    errors, so the refusal `testing-compilation-failed` comes from the published ERROR diagnostics, not from the class files.
- **What the tests cover and what they do not:** the observation of Maven and Gradle layouts was made with a throwaway probe,
  deleted; the replay `AffectedTestsReplayTest` (real jdtls) imports hand-written Eclipse projects only, whose `test-bin` is set
  by the test's own `.classpath`. No test exercises a Maven or a Gradle project. The constant read through a static import,
  which the compiler of jdtls leaves without a reference, was observed by hand with ecj and `javap`, not by a test.
- **Selection, as Infinitest does it, with `java.lang.classfile` and no library:** a changed test is selected itself; a changed
  class selects its dependents, transitively; the tests that load their classes by themselves (`@ArchTest` or
  `@AnalyzeClasses` on a field or a method) are always selected once something changed; the filters of `infinitest.filters`
  (`include`, `exclude`, regular expressions on the whole class name) leave tests out, the groups lines are ignored and the page
  says so. gabier's own `HexagonalArchTest` (`ClassFileImporter` with a plain `@Test`) is not "dynamic", as in Infinitest.
- **Checked:** `bin/ci --all` on the amended tree, green: format, build, test (326 s, coverage 100 %, architecture,
  Checkstyle), release-install, release-notes, native (76 s, with a new check against the fake daemon), jdtls (845 s).
  The coupling ceilings did not rise (6 / 5 / 11 / 26). Check of the pull request `bin-ci`: pass (12 m 16 s). The architecture
  test `HexagonalArchTest` now lists `.testing` among the domains the daemon implements (`IMPLEMENTED_DOMAINS`).
- **Observed on this checkout** (580 Java files, Gradle): `gabier start --wait` 23 s; the first call 42 s (the build wait
  included), then about 2 s per call.
- **Review:** one blocking finding, fixed in the same commit (the paths were not normalized: `./src/...` was refused and a
  `.` or `..` segment on a file that does not compile gave `no affected tests`; tests red before, green after). Suggestions
  (the description of the dynamic tests, a Java file with no class written, `affected-tests /` ending in a
  `NullPointerException`, two calls at the same time, a runner with private constructors, the static-import wording, the Maven
  and Gradle replay) are in one issue on hold, `11-11-2026-1751-affected-tests-review-suggestions`.
- **Next:** `11-11-2026-1814-affected-tests-needs-no-file-list` (the call with no file list). A note `docs/releases/<version>.md`
  is needed before the next tag: a new command `gabier affected-tests` and an MCP tool `affected-tests`.
- **Frictions reported by the worker, no issue opened:** `gabier move` refuses files of two source folders in one call; `gabier
  affected-tests --token A.java` takes `--token` as a file name (exit 1, not the usage error 2), as `gabier diagnostics` does;
  nested classes are listed as `Outer$Inner`, and a runner given that name may need the outer class (not verified).

Tokens:
- worker: 197306 (`subagent_tokens` of its last notification; the earlier ones, 174725 and 181775, were lower, the agent was
  resumed once for the blocking finding)
- reviewer: 152062 (`subagent_tokens`)
- supervisor: 41 calls, output 29374, input + cache write 472318 (cache read 9871871 apart); covers the supervisor session
  from the launch of the worker to the merge, other work included (the discussion of the next step and the plan of the
  feature); exchanges with the user: 3 messages
