# The kept jdtls workspace: suggestions of the review

Opened: 15-11-2026 13:35
On hold: findings of the review of 0e1c31d that break no `Done when` point; the user lifts the hold.

Hardening of the kept jdtls workspace (`modules/daemon`), one point per finding.

Done when:

- [ ] The orphan check of `JdtlsWorkspace` (`contains(folder)`) recognises a jdtls by its `-data <folder>` arguments, not by
      any process that has the folder as an argument (`du -sh <folder>` made the folder look used by a jdtls); a test shows it.
- [ ] The fingerprint of `JdtlsLauncher` changes when gabier's settings or capabilities of jdtls change, so a new build does
      not reuse a workspace made under the old ones; a test shows it.
- [ ] A start on a kept workspace that fails by a ready time-out does not empty the workspace; a death or a refusal still
      does; a test shows each.
- [ ] A restart is replayed against the real jdtls in `integrationTest` (no full build, a change made while stopped, the
      `java.configuration.runtimes` pin), or the resolution says why it cannot be.
- [ ] The comment of `JdtlsSession` (line of "builds nothing"), `agents.md` of the cli ("a few seconds") and the disk cost of
      a workspace per project and per worktree (`docs/daemon.md`) say what was measured.
