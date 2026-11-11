# The diagnostics stream holds in the races and the long reads

Opened: 12-11-2026 00:26
On hold: findings of the review of `gabier diagnostics --watch` (PR 49) that break no `Done when` point; lifted when a use
shows one (a `gabier rename` that waits behind a watcher's read is the likeliest, a second watcher arriving during a read the
second).

The review found races and slow cases of the stream (`ProblemFeed`, `Watching`, `FolderWatch`, `JdtlsLanguageServer`) that no
test covers.

Done when:
- A watcher that arrives while a read is running starts from the state the read is about to publish, not from an older one: it
  never prints a state and then the previous one, so an error that went away does not come back; a test of `ProblemFeed` covers a
  `latest` older than what the first reader told.
- A watcher that arrives just as the feed ends because its read failed does not start a second pump: one reader and one disk
  source serve all the watchers, and no state is offered twice; a test covers it.
- A folder moved out of the project or removed while watched makes the daemon read again, so the problems of the files that
  left do not stay on screen; a test of `FolderWatch` covers a deleted or moved-out folder.
- A `gabier rename`, a `diagnostics` or any other call does not wait for the length of a read of the feed (11 to 15 s on this
  checkout, according to `docs/daemon.md`), or the page says that it does and why; the choice is written in the resolution.
- The first notification of `gabier diagnostics --watch <file>` is not spurious: a replay on the real jdtls shows that the list
  of a file from `diagnostics(files)` and from the last publication of the whole project are equal, or the watch with files
  starts from the same source as the one it is then compared with.
- `diagnostics --watch --dry-run`, `--watch --all <file>` and `--watch` with several files are tested in `WatchingDiagnosticsTest`.
- The debounce of the disk source (100 ms) does not delay the read for as long as events keep coming less than 100 ms apart (a
  build writing under the project), or `docs/daemon.md` stops presenting it as a bound on the delay; a test covers it.
