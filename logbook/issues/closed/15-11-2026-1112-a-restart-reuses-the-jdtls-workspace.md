# A restart of the daemon on a project it already opened is ready without a new import and build

Opened: 15-11-2026 11:12

An agent that starts gabier on a project waits for jdtls to import and build it, at every start, because the jdtls
workspace is removed at each stop. The need: a restart on a project already opened is ready in a few seconds. The
worker chooses the mechanism, implements it, and says in its report why that one.

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

## Resolution

Done in 0e1c31d (pull request 59, check `bin-ci` green). A restart of the daemon on a project it already opened reuses the
jdtls workspace and is ready in 12 to 14 s with no full build, against 68 to 69 s for a first start (jdtls 1.63.0, this
checkout, JDK 27 with several JDK 25 installed, 15-11-2026). Decision `02-11-2026-1223` (remove the workspace at each stop,
because a kept one measured slower than a new one) is reversed. Why it measured slower: with several JDKs of the same Java
version on the machine (SDKMAN!, mise), jdtls binds the execution environment JavaSE-25 to a different one at each start and
rebuilds every project (36 to 45 s, 6 full builds). The workspace is now kept per project in the gabier cache with a state
written at a clean end, and once jdtls is ready the daemon sends it `java.configuration.runtimes` with the JDKs it bound the
projects to; jdtls keeps them as the default of the environment, so the next start binds the same JDK and builds nothing
(measured: 0 full builds on every restart). Declaring the runtimes in `initialize` on a restart does not work, because jdtls
builds before it reads client settings, hence the declaration after ready, which costs 7 more builds (about 12 s) in the
background after a first start is ready. Files changed while the daemon was stopped are told to jdtls from the manifest of
the state, so `diagnostics` answers as after a first start (checked: same 1409 lines). A start never depends on what a
previous one left: unreadable state, other jdtls or JDK, a held workspace, an orphan jdtls whose daemon was killed, and a
start that fails on the kept workspace all fall back to a first start (empty or temporary workspace, with a log line).
Risks accepted: about 210 MB of disk per project and worktree until its folder disappears; kept workspaces of another jdtls
version stay until the project folder is gone; a retry can double a failing start's time-out; a JDK upgraded in place is
detected only through its `release` file.

Checked by the worker: full `bin/ci` green (format, build, test 340 s with the 100% coverage gate, Checkstyle and ArchUnit,
native, jdtls; release-install and release-notes skipped, no changed file reaches them). Each `Done when` point proven in its
report (real-daemon runs: 0 full builds on 4 restarts; same diagnostics after changes on disk; kill -9 fallback).

Review (neutral, once): no blocking finding. Fixed by the supervisor after the merge: the contradiction between "builds
nothing" and the 7 builds after a first start, the "seconds" wording, the "nothing to kill" wording (`docs/daemon.md`,
`dogfooding.md`). The other suggestions are in `logbook/issues/open/15-11-2026-1335-kept-workspace-review-suggestions.md`,
on hold. `agents.md` still says "a few seconds" (resource of the cli, in that issue). A worker's `kill` of another session's
jdtls during its cleanup is reported to the user. Unrelated, not opened: `~/.cache/gabier/jdtls-workspaces/<hash>` left by
the feature removed in 7dd40a9 (the user's cache).

Tokens:
- worker: 148498 (`subagent_tokens`)
- reviewer: 109858 (`subagent_tokens`)
- supervisor: covers the supervisor session over the period of the ticket, other work included: 25 calls, output 11701,
  input + cache write 50563 (cache reads 1941412); exchanges with the user: 2 messages
