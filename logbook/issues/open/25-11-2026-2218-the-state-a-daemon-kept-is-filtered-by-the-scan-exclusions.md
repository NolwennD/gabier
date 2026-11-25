# The state a daemon kept is filtered by the scan exclusions

Opened: 25-11-2026 22:18
Depends on: 25-11-2026-2135-the-first-call-does-not-walk-nested-worktrees-and-dependency-folders.md

Review of that ticket. A daemon that starts on the file state a previous daemon wrote (`WatchedFiles.of(root, remembered)`)
takes it as it is: the files below a folder the scan now leaves out are in it, so the first `changes()` reports each of
them as deleted. On the main checkout of the project that met the problem, whose state was kept, the first call after
the upgrade still tells jdtls of a huge batch, once.

Done when:
- A state that lists a file below a `node_modules` folder or a folder that has its own `.git` produces no event at the
  first `changes()`, with a test (`of(root, remembered)` with such a path).
- The assertions on `fingerprint()` of `WatchedFilesTest` that compare it with the fingerprint of the same code on the same
  disk (they cannot fail) check the exclusion in a way that can fail, or are dropped.
- A `.git` that is a link (dangling, or to a folder) is either decided and tested, or the page of `docs/daemon.md` says what
  happens; the page stays true on every point it makes.
