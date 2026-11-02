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
