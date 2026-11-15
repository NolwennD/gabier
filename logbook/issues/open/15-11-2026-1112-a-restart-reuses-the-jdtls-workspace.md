# A restart of the daemon on a project it already opened is ready without a new import and build

Opened: 15-11-2026 11:12

An agent that starts gabier on a project waits for jdtls to import and build it, at every start, because the jdtls
workspace is removed at each stop. The need: a restart on a project already opened is ready in a few seconds. The
worker proposes how, and returns to the supervisor with its proposal before it writes the code.

What was measured on gabier itself (15-11-2026, jdtls built from its `main` branch, quiet machine):

- a new workspace is ready (file watchers registered) in about 48 s: 5 s to start, 7 to 12 s of Gradle import, 26 to
  30 s of build, then indexing;
- a workspace kept from the previous start is ready in about 8 s when jdtls rebuilds nothing, and in about 30 s or more
  when it rebuilds everything, which is what the closed issue
  `02-11-2026-1223-the-kept-jdtls-workspace-feature-is-removed` met;
- it rebuilds everything when the JDK bound to the projects is not the one of the previous start. On a machine with
  several JDKs of the Java version of the project, jdtls picks one of them in an order that changes at each start,
  unless the JDK that runs jdtls is itself of that version: then it picks that one, and four restarts of four rebuilt
  nothing. Declaring the JDK in `java.configuration.runtimes`, or turning the detection of the JDKs off, did not stop
  the rebuilds;
- the Java builder says why it rebuilds: start jdtls with `-debug <file>`, the file holding
  `org.eclipse.jdt.core/debug=true` and `org.eclipse.jdt.core/debug/builder=true`, and read
  `<workspace>/.metadata/trace.log` (`Performing full build ...`).

Not measured: a restart after Java files changed on disk while the daemon was stopped, and the pinned jdtls.

Done when:

- [ ] A restart of the daemon on a project it already opened makes jdtls rebuild nothing (the trace above), on a machine
      that has several JDKs of the Java version of the project.
- [ ] The time to ready of a first start and of a restart, measured with the pinned jdtls, is in `docs/daemon.md`.
- [ ] After Java files changed on disk while the daemon was stopped, `gabier diagnostics` answers as after a first start.
- [ ] A start never fails or answers wrongly because of what a previous start left: it falls back to a first start.
- [ ] The pages, rules and helps that describe the jdtls workspace are true, and the resolution says why the choice of
      `02-11-2026-1223` is reversed.
