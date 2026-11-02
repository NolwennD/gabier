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
