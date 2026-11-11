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
