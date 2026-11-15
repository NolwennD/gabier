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
- A failing test makes the command end with a failure status that the agent can tell from a refusal; code that does not
  compile, and a test that cannot be run (no engine for it), are refusals with a key each, not a failure of the tests.
- A test that never ends does not hang the command: it ends with a refusal after a time limit that the agent can change.
- Proven on the real jdtls (`integrationTest` or the scenario replay) with a project that holds one passing and one failing JUnit
  test, and in the core against the fake (`.claude/rules/workflow.md`, "Fake it until you run it").
- The time to a result on this repository (one small test class) is measured and written in `docs/daemon.md`.
- The help of the command, the guide (skill `extend-guide`), the MCP tool and the daemon method follow the skills
  `add-daemon-method` and `extend-guide`; the pages that list the commands stay true.
