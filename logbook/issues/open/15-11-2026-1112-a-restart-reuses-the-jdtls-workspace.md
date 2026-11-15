# A restart of the daemon reuses the jdtls workspace and is ready without a rebuild

Opened: 15-11-2026 11:12

An agent that starts gabier on a project it already opened waits for a whole import and build of the project again,
because the jdtls workspace is removed at each stop. A workspace kept between starts lets jdtls skip both, when the JDK
bound to the projects does not change from one start to the next. The measures, the cause of the rebuilds and the
condition that stops them are in `docs/jdtls-startup-runbook.md`. This reverses the closed issue
`02-11-2026-1223-the-kept-jdtls-workspace-feature-is-removed`: the resolution says why, and what changed since.

Done when:

- [ ] A second start of the daemon on the same project reuses the workspace of the first, and jdtls rebuilds nothing
      (the trace of the Java builder of the runbook), on a machine that has several JDKs of the Java version of the project.
- [ ] The time to ready of such a restart is measured with the pinned jdtls and written in `docs/daemon.md`, next to the
      time of a first start.
- [ ] A restart after Java files changed on disk while the daemon was stopped answers `gabier diagnostics` as a first
      start does.
- [ ] A workspace that jdtls cannot reuse (another jdtls version, a damaged folder, one held by a running daemon) falls
      back to a new one, and the workspaces nobody uses any more are removed.
- [ ] `docs/daemon.md`, `.claude/rules/dogfooding.md` and the help that describe the workspace as temporary are true again.
