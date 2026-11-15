# gabier runs the tests a change affects and reports the failures

Opened: 15-11-2026 14:34

Today an agent asks `gabier affected-tests` which test classes a change affects, then runs them itself with the build tool
of the project and reads the failures in its output. Once this ticket is closed, one gabier command runs those tests, in a
JVM of its own, and gives back what failed, with no Maven or Gradle: the agent edits, calls the command, reads the failures.
This is the step towards Infinitest that `gabier affected-tests` does not take. The worker chooses the mechanism (the JVM, the
way the tests are launched, the name of the command; `gabier test` is the working name), implements it, and says in its report
why that one. The AOT cache of the test JVM is the work of the ticket `14-11-2026-0125-aot-cache-speeds-up-a-test-jvm`, out
of scope here.

Done when:
- With no file, the command runs the tests that `affected-tests` with no file gives (same selection, same classpath of
  the test classes that jdtls compiled); given test classes, it runs those. No build tool is called.
- The answer says, for each failed test, enough for the agent to act without reading a log: its class and method, the failure
  message, and where it failed; and says how many tests ran, passed, failed and were skipped. A `--json` form exists, as for
  the other commands.
- A failing test makes the command end with a failure status that the agent can tell from a refusal; a test class whose
  own file does not compile (amended by the supervisor at the review, see the resolution), and a test that cannot be run (no
  engine for it), are refusals with a key each, not a failure of the tests.
- A test that never ends does not hang the command: it ends with a refusal after a time limit that the agent can change.
- Proven on the real jdtls (`integrationTest` or the scenario replay) with a project that holds one passing and one failing JUnit
  test, and in the core against the fake (`.claude/rules/workflow.md`, "Fake it until you run it").
- The time to a result on this repository (one small test class) is measured and written in `docs/daemon.md`.
- The help of the command, the guide (skill `extend-guide`), the MCP tool and the daemon method follow the skills
  `add-daemon-method` and `extend-guide`; the pages that list the commands stay true.

## Resolution

Done in the commit `gabier test` (pull request 61, check `bin-ci` green; rebased on `main` by the supervisor over the AOT
guide commit, with no conflict and no new `bin/ci` of the worker: the check of the pull request is the full `bin/ci --all` on
the rebased tree, an exception to the rule that only covers `.md` commits).

Added `gabier test [--timeout=<seconds>] [<test-class>...]` (daemon methods `test` and `test-changed`, MCP tools, exit code 3):
it runs test classes, or the tests a change affects, in a JVM of its own on the class path jdtls gives, through the JUnit
Platform of the project (launcher and engines by reflection, no new gabier dependency: `TestRunnerMain`, JDK only), and reports
counts and, for each failure, class, method, exception, message and the frame in the project (text and `--json`). Refusals with
a key each: no launcher, no engine, no such class, timeout, run failed, test file does not compile. Default limit 300 s.
Measured on this repository: 3.1 s warm for a 6-test class, 35 s for the first call after the start.

Why this mechanism: a separate JVM keeps the tests (`System.exit`, endless tests) out of the daemon and the JUnit versions of
the project apart from gabier's; reflection avoids a launcher dependency (decision of the user, 15-11-2026: use the test
dependencies of the project). A project that does not declare `junit-platform-launcher` (Maven or Gradle with only
`junit-jupiter`) gets `testing-no-test-launcher`, which names the dependency; seen only with a class loader that hides the
launcher, not on a real Maven project.

Why a `Done when` point is amended: the review found that a test class whose main code does not compile runs and fails with
"Unresolved compilation problem" instead of a refusal. The first design refused on any file with an error; on this repository
that refused every call, because jdtls flags about thirty files that Gradle compiles (test fixtures not accessible from
`:daemon`). The point now asks for a refusal when the file of the test class does not compile, and `test-changed` still refuses
on a changed file with an error. jdtls is not fought (`.claude/rules/workflow.md`): the false errors are a ticket of their
own (`15-11-2026-1638-jdtls-reports-fixtures-as-not-accessible`).

Checked by the worker: full `bin/ci --all` green (test 317 s: cli 1694, core 954, daemon 1355, architecture 24, coverage 100%;
release-install, release-notes, native 244 s, jdtls 1011 s). Review (neutral, once): one blocking finding, the one above,
settled by the amendment; the replacement of the abstention row of `GuideQuestionsTest` is a legitimate change of meaning, not
a weakened test. The other findings are in `15-11-2026-1638-the-test-jvm-of-gabier-test-is-hardened.md` (on hold).

Tokens:
- worker: 318084 (`subagent_tokens`)
- reviewer: 132934 (`subagent_tokens`)
- supervisor: shared with ticket `14-11-2026-0125` (see its resolution for the figure of the period); exchanges with the user:
  2 messages in the period of this ticket after the launch
