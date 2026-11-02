# gabier remove-workspace

`gabier remove-workspace [<project folder>]` removes the jdtls workspace that the daemon kept for a project (see "The
data folder of jdtls" in [the daemon](../daemon.md)): everything jdtls wrote in `<folder>/<hash of the root>`, where the
folder is the one `GABIER_JDTLS_WORKSPACES` names. It is for the end of a worktree, **before** `git worktree remove`: a
kept workspace is hundreds of megabytes.

It **talks to no daemon and starts none**: it needs only the folder name, which it computes as the daemon does (a hash
of the root of the project: the real path of the folder, or its absolute path when the folder is already gone), so it
works when the daemon of the project is stopped. The project is the folder given, else `GABIER_PROJECT`, else the
working directory, as for `gabier-daemon`. A daemon that runs jdtls on the workspace holds a lock on it
(`.gabier-lock`), which the command tries before it deletes anything: while it is held the command refuses
(`workspace-in-use`) and leaves the workspace as it is, so stop the daemon first (`gabier stop`).

It is idempotent: with nothing to remove (already removed, never kept, the folder of the project gone) it says so and
exits 0, and so it does when `GABIER_JDTLS_WORKSPACES` is not set, since then nothing is kept. The workspaces of projects
that no longer exist are also removed by the next daemon that starts jdtls, which is the safety net for a worktree
removed without this command.

```text
$ gabier remove-workspace /work/demo-worktree
Removed the jdtls workspace /home/me/.cache/gabier/jdtls-workspaces/6f1c0d4e5a7b4c8d9e0f1a2b3c4d5e6f of /work/demo-worktree
$ gabier remove-workspace /work/demo-worktree
No jdtls workspace is kept for /work/demo-worktree: nothing to remove
$ gabier --json remove-workspace /work/demo-worktree
{"removed":false,"project":"/work/demo-worktree","workspace":"/home/me/.cache/gabier/jdtls-workspaces/6f1c0d4e5a7b4c8d9e0f1a2b3c4d5e6f"}
```

Without the variable: `No jdtls workspace is kept: GABIER_JDTLS_WORKSPACES is not set` (`{"removed":false,"project":"..."}`
with `--json`, with no `workspace` member). It is not a daemon method, so it has no row in [the
protocol](../protocol.md#methods).

## Refusals

A refusal is the problem object on the standard error, exit code 2. The keys the command can answer, listed by `gabier
help remove-workspace`:

- `usage-error`: the arguments are not those of the command (`gabier help <command>` gives its usage).
- `workspace-in-use`: a daemon runs jdtls on the workspace, which is left as it is.
