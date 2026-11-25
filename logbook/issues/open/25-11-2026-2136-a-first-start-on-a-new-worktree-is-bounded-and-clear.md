# A first start on a new worktree finishes, or fails with its cause

Opened: 25-11-2026 21:36

Seen on a large Maven project, with three daemons of ticket worktrees started cold on a loaded machine (jdtls about
3.8 GB, 9.5 GB of swap in use, load about 4): each log ends with `Timed out after 300.0 seconds … waiting for the JDK
(java.project.getSettings)`, the budget of `ProjectServer.TIMEOUT`. On one of them the first `diagnostics` then failed
with `waiting for jdtls to register the files it watches` (`Builds.awaitInitialized`). The workspace of a worktree is
removed with the worktree, so every ticket pays the cold import again; the kept workspace of the main checkout
answered `diagnostics --all` in 3 s.

An agent that opens a new worktree of a big project gets either a ready jdtls, or a failure that says what to do, instead
of a start that times out and a first call that fails for a different reason.

Done when:
- The cause is established first, on a real run of such a project: where the 300 s go (the import, the Maven resolution,
  the machine), with the figures in the resolution; the documentation of jdtls is read for a setting before any code.
- A start that cannot finish within its budget says so in a way that tells the agent what to do (wait longer, which
  setting or variable moves the budget, call `status`); the budget of a start can be raised without a rebuild, if it
  cannot be today.
- The first call after a start that timed out does not fail with the wait for the watchers: it says that the start
  failed and why, or it waits for the retry (a test with the fake server that replays this order).
- Whether a new worktree can start from the workspace kept for the main checkout is decided and written in the
  resolution, with the reason (`docs/daemon.md` stays true).
