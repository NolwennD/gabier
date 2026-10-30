# A probe that starts jdtls and waits for the import

Opened: 30-10-2026 02:46

Write an integration test in the daemon module that starts a real jdtls (the path of the launcher in `GABIER_JDTLS`, a
fresh `-data` directory) on this project with LSP4J, then waits until the import is really done: the server says it is
ready, and it lists the modules of the project. It always stops jdtls and deletes its data directory, even on failure,
and fails loudly, never skips, when `GABIER_JDTLS` is missing. It is a Gradle task `jdtlsProbe`, not part of `check`,
which switches on the `jdtls` stage of `bin/ci`. It is the seed of the future LSP client: keep it clean, do not
generalise it.

Done when:
- `./gradlew jdtlsProbe` ends green on the current project, and the `jdtls` stage of `bin/ci` runs it, not skipped;
- no jdtls process is left running afterwards, and no data directory;
- without `GABIER_JDTLS`, it fails at once with a clear message.

Depends on: daemon-module-with-lsp4j, pin-jdtls-with-mise.

## Resolution

Done in b523211, by a subagent, then tidied by the supervisor. `JdtlsProbeTest` and `JdtlsClient`, in the
`integrationTest` source set of the daemon module, start `$GABIER_JDTLS -data <fresh temporary directory>` over stdio with
LSP4J, send `initialize` and `initialized`, wait for the `language/status` notification of type `ServiceReady`, then poll
`java.project.getAll` every 2 seconds until every folder of `modules/` that holds a `module-info.java` is listed. The task
`jdtlsProbe` (never up to date, not part of `check`, JaCoCo off on it) switches on the `jdtls` stage of `bin/ci`. A
missing or non-executable `GABIER_JDTLS` fails at once with a hint to run `mise install`, never skips. The launcher gets
the `JAVA_HOME` of the test JVM, the Java 25 of the build, so jdtls does not depend on the shell.

Checked by the subagent, `bin/ci` with `GABIER_JDTLS` set: format, build, test, native and jdtls passed (the jdtls stage
ran, 48 s; without the variable it is skipped with its reason); `daemon 22`, `cli 9`, `protocol 4`, `architecture 12`,
probe 1, no failure. A probe run takes 52 s warm and 1 min 27 s cold. jdtls reports the projects `architecture`,
`build-logic`, `modules/cli`, `modules/daemon`, `modules/protocol` and `modules/shared`, as URIs; the root project is not
listed. After a run no jdtls process of the probe and no data directory are left. The wait is real: waiting for a module
that does not exist ends in a timeout message that names what is missing.

Neutral review, done once: nothing blocking. What became of each finding:
- a dead jdtls makes `shutdown()` throw an unchecked exception that skipped the kill of the process tree: fixed by the
  supervisor, without a ticket (the exception is caught, the kill always happens);
- an empty list of modules passed vacuously, and `endsWith` matched loosely: fixed by the supervisor, the probe fails
  when there is no module and compares the exact folder of each module with the URI jdtls gives;
- `"Starting".equals(...)` null-safety and an over-wide README line: fixed by the supervisor;
- nothing watches the process (a dead or silent jdtls costs up to three waits of ten minutes), a status of type `Error`
  is ignored, and `integrationTest` is compiled by no stage: opened as an issue on the fly;
- an exception while deleting the data directory would hide the failure of the test, and the LSP4J descriptors are
  ignored on this classpath-mode test, so the probe proves nothing about JPMS (the `selfCheck` does): left.

To remember for the diagnostics: `ServiceReady` arrives only after the Gradle import has finished, so
`java.project.getAll` answers at once; jdtls compiles in the checkout with its own compiler and writes `.project`,
`.classpath`, `.settings` and `bin/` there (ignored by git), so its diagnostics can differ from Gradle's; `build-logic` is
imported as a project; Buildship uses the tooling API 9.7.1 against the 9.8.2 wrapper; `language/eventNotification` is
not handled, so LSP4J logs a warning.
