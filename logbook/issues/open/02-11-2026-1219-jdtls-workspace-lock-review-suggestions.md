# The jdtls workspace and its lock: review suggestions

Opened: 02-11-2026 12:19
On hold: findings of the review of the batch 763aeae..71b3b68, none breaks a `Done when` point; the user lifts the hold, or a use makes one matter (the fourth costs flaky test runs when two worktrees run their tests at once).

Done when:
- [ ] `RemoveWorkspace.java:62-66`: with no operand, `gabier remove-workspace` takes the nearest `.git` ancestor of the working directory as the daemon does (`Main.projectRoot`, `SocketLocation.projectRoot`), not the working directory: run from `<worktree>/modules/cli` it must remove the workspace of the worktree (`RemovingAWorkspaceTest:113` pins the old behaviour and changes with it).
- [ ] `StaleWorkspaces.sweep`: checking the lock and deleting are not separate steps for a folder another daemon is creating (A has made the folder and its lock file but has not locked yet: B's probe takes the lock, A gets `WorkspaceInUseException` on its fresh folder, or B deletes it after releasing); a test with the two interleaved.
- [ ] `JdtlsWorkspace.open`: when reading the owner or writing `.gabier-root` fails after the lock was taken (an unreadable file, a full disk), the lock is released and the temporary folder removed; a test.
- [ ] The unit tests that use `JdtlsWorkspace.temporary()` or `JdtlsSettings.of` on the real system `/tmp` (`JdtlsSessionTest:74`, `JdtlsCleanUpTest:77`, `JdtlsLanguageServerTest:127`, `JdtlsMoveToPackageTest:100`, `JdtlsWorkspaceTest:80`) get a temporary folder of their own, so that two runs in two worktrees never sweep each other's folders.
