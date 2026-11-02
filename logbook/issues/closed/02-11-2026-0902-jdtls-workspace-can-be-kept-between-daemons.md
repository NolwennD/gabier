# The jdtls workspace of a project can be kept between daemons

Opened: 02-11-2026 09:02
Depends on: 02-11-2026-0901-orphaned-jdtls-data-folders-are-removed.md

An agent restarts the daemon of a project (after `gabier stop` or a reinstall) without redoing the whole import. Today
`-data` is a temporary folder, removed at stop. By default nothing changes: nothing is left behind.

**jdtls is configured, not fought**: a stable `-data` per root lets jdtls reuse the valid projects of its workspace
(`ProjectsManager.initializeProjects`, `cleanInvalidProjects`). Projects outside its root are still deleted and
imported again (`StandardProjectsManager.deleteInvalidProjects`): the gain is to be measured, not assumed.

Done when:
- [ ] By default, `-data` stays temporary and is removed at stop (a test keeps it so).
- [ ] A documented variable keeps it: `-data` is `<folder>/<hash of the root>`, kept at stop.
- [ ] A second daemon on the same root while the first runs gets a refusal that says why (the lock of the Eclipse
      workspace), not an opaque jdtls failure.
- [ ] The time of a restart is measured with and without the option and written in the `## Resolution`.
- [ ] The kept workspace of a worktree is deleted when the worktree is (decision of the user, 02-11-2026): a command that takes
      the root of a project and removes its kept workspace, with no daemon running (it needs only the name of the folder, computed
      as the daemon does), refused while a live jdtls holds the lock, idempotent when there is nothing to remove (tested red first);
      the skill `ship` (step 5, before `git worktree remove`) runs it for the worktree it removes, and the rules say so. The command
      is documented in `docs/daemon.md` and listed by `help`.
- [ ] The option is on for this project (decision of the user, 02-11-2026: a warm cache): `mise.toml` sets the variable in its
      `[env]`, so every worktree of gabier keeps its workspace. A kept workspace whose root no longer exists (a deleted
      worktree) is removed, by the daemon at start or by `scripts/stop-stray-daemons.sh` (which the skills `ticket` and `ship`
      already run), tested red first; the resolution gives the size of one kept workspace (about 227 Mo live) and says how many
      stay on disk with the worktrees of a day.
- [ ] `docs/daemon.md` documents the option, the place and how to clean it. `bin/ci` passes.

## Resolution

Commit d329b9c. `bin/ci` (worker, full, on the final tree, 71b3b68): format 11s, build 20s, test 181s, native 161s, jdtls 297s, all passed; tests: cli 559, core 638, daemon 817, protocol 369, shared 236, architecture 19, 0 failures, coverage 100%. Reviewed once: one blocking finding (below), fixed by the worker; the suggestions are in `02-11-2026-1219-jdtls-workspace-lock-review-suggestions.md` (on hold). The tree `bin/ci` ran on is the final tree; commit 3 was not built on its own.

- Behaviour: by default `-data` stays temporary and is removed at stop (`JdtlsWorkspaceTest.Temporary`, `ProjectServerTest.startsJdtlsOnATemporaryDataFolderByDefault`). `GABIER_JDTLS_WORKSPACES=<folder>` gives `-data <folder>/<hash of the root>`, kept at stop, with `.gabier-root` inside; a second daemon on the same root gets the problem `workspace-in-use` (exit 2) with the reason and the variable. A kept workspace whose root is gone is removed at daemon start (`StaleWorkspacesTest.OfProjectsThatAreGone`).
- New command, the worker's choice of name: `gabier remove-workspace [<project folder>]`: needs no daemon (`KeptWorkspaces`, in `protocol`, computes the folder as the daemon does), refused while a daemon holds the lock, idempotent (`nothing to remove` also when the variable is unset or the folder is gone); the skill `ship` step 5 runs it before `git worktree remove`.
- **Decision of the user (02-11-2026), reversing the one of the same morning: the option is off for this project.** The issue's point asking `mise.toml` to set it was done and then removed. Measure: the first `gabier diagnostics` after a restart takes 45 to 51 s with the kept workspace and about 30 s with a temporary one (shared indexes warm, loaded machine): jdtls deletes and imports again the projects outside its root (`StandardProjectsManager.deleteInvalidProjects`), so nothing is reused. Size of a kept workspace: 2.8 Mo at rest after a stop, 58 to 62 Mo while jdtls runs (about 214 Mo when the indexes are not shared). The option, the variable, the command and the cleanup stay delivered and documented, off by default.
- Scope extension, kept by the user: the default project served by the daemon is the nearest `.git` ancestor of its working directory, as for the socket.

Tokens (the batch of four issues shares these figures):
- worker: 482246 (`subagent_tokens` of its last notification; the worker was resumed once, after the review, and the first notification said 446626).
- reviewer: 92192 (`subagent_tokens`).
- supervisor: 20 calls, output 27027, input + cache write 595597 (cache read 5591166); covers the supervisor session over the period of the batch (launch of the worker to now), other work included (the issue of the orphaned caches, the questions to the user); exchanges with the user: 2 messages typed, and two questions answered.
