# The kept jdtls workspace feature is removed

Opened: 02-11-2026 12:23
Depends on: 02-11-2026-0902-jdtls-workspace-can-be-kept-between-daemons.md

Decision of the user (02-11-2026): the "warm cache" brings nothing significant (measured slower: 45 to 51 s against about 30 s for the first
`diagnostics` after a restart), so the feature is removed, not only left off. Less code to keep: the option, its folder and the command that
cleans it.

**Care**: this is a removal, with no change of behaviour for the default (temporary `-data`). Keep what ticket `0901` brought and the
default needs: the temporary data folders, their lock (`WorkspaceLock`, with the registry that keeps the lock of the JVM) and the sweep of the
folders of killed daemons. Keep the shared indexes (`0900`) and the socket of the project. What goes: `GABIER_JDTLS_WORKSPACES`, the kept
`-data` (`JdtlsWorkspace` kept mode, `.gabier-root`), `KeptWorkspaces`, the command `gabier remove-workspace` (cli, daemon docs,
`help`, the native stage check), the cleanup of the kept workspaces of gone roots, and the problem `workspace-in-use` if only the kept mode
uses it (check `WorkspaceInUseException`: a temporary folder locked by another daemon is a case of its own, say what happens there).
The skill `ship` step 5 loses its `remove-workspace` call, and the rules, skills and docs that name the feature are updated. Tests that
only showed the kept mode go; no test of what stays is weakened. The two issues on hold that mention it are updated by this ticket
(`02-11-2026-1042-gabier-warns-of-orphaned-caches.md`: only the temporary data folders are watched;
`02-11-2026-1219-jdtls-workspace-lock-review-suggestions.md`: its first point, `remove-workspace`, goes).

Done when:
- [ ] No code, test, doc, rule or skill names `GABIER_JDTLS_WORKSPACES`, `remove-workspace`, `KeptWorkspaces` or the kept mode; the default
      behaviour is unchanged (`-data` temporary, removed at stop, the folders of killed daemons swept).
- [ ] The two on-hold issues above no longer mention the kept workspace. `bin/ci` passes (coverage 100%, architecture tests).

## Resolution

Commit 7dd40a9 (rebased over text-only commits of `main`, no new `bin/ci`). `bin/ci` (worker, full, on the worktree): format 15s, build 3s, test 211s, native 217s, jdtls 553s, all passed; tests: cli 595, core 675, daemon 856, protocol 366, shared 236, architecture 19, 0 failures, coverage 100%. Reviewed once: nothing to report (every deleted test covered only the kept mode; the guards of the sweep, the lock of the JVM and the symlink case are kept and tested).

- Why: decision of the user (02-11-2026): the warm cache brings nothing significant (the first `diagnostics` after a restart measured at 45 to 51 s with the kept workspace against about 30 s with a temporary one), so the feature delivered by d329b9c is removed and not only left off.
- Removed: `GABIER_JDTLS_WORKSPACES`, the kept mode of `JdtlsWorkspace`, `JdtlsSettings.withWorkspace`, `KeptWorkspaces`, `gabier remove-workspace` (cli, `help`, README, `docs/commands/remove-workspace.md`, the native stage check), `StaleWorkspaces.removeOfMissingRoots`, `.gabier-root`, the problem `workspace-in-use` and `WorkspaceInUseException` (only the kept mode used them: a temporary folder has a random name that no other daemon can hold, and `open` is `WorkspaceLock.acquire(data).orElseThrow()`), the step of the skill `ship` that ran `remove-workspace`, and the lines of `dogfooding.md`, `workflow.md`, `verify` and `docs/daemon.md`. `git grep` of these names, outside the logbook, finds nothing.
- Kept, as the ticket says: the temporary data folders, `WorkspaceLock` with the registry of the locks of the JVM, the sweep of the folders of killed daemons (`StaleWorkspaces.removeKilled`, back to its simple form), the shared indexes, the socket of the project. `SocketLocation.projectRoot` called `KeptWorkspaces.rootOf`: inlined as a private `toRealPath` with the fallback to `toAbsolutePath().normalize()`, with `SocketLocationTest.takesTheAbsoluteNormalizedPathOfAProjectFolderThatDoesNotExist` to keep the case covered.
- Not done: `WorkspaceLock` and `Directories` stay public in `protocol` though only the daemon uses them (a tidying ticket, not opened). The two on-hold issues that named the kept workspace were corrected by the supervisor on `main` before the merge (the orphaned-caches warning, the review suggestions of the lock).

Tokens:
- worker: 157159 (`subagent_tokens`).
- reviewer: 73008 (`subagent_tokens`).
- supervisor: 38 calls, output 47857, input + cache write 97632 (cache read 17479129); covers the supervisor session over the period of the ticket (launch of the worker to now), other work included (the shipping of the move ticket, the study of the release of gabier with JReleaser and GraalVM, two issues corrected); exchanges with the user: 2 messages.
