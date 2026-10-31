# A request after a timed-out sync still waits for jdtls to take the changes in

Opened: 31-10-2026 09:11

Found in the review of `jdtls-learns-of-the-changes-made-on-disk` (all in `modules/daemon`). `WatchedFiles.changes()` sets
`known = now` before jdtls acknowledges the events: if `rename` times out waiting (jdtls busy), the immediate retry finds
no change, skips the wait and asks jdtls while the earlier events may still be queued, so it can answer for the old text.
Also, three tests in `JdtlsLanguageServerTest` stay green when the code is wrong.

Done when:
- [x] A retry after a timed-out wait still waits for the acknowledgement of the earlier events before it asks jdtls; a test
      (edit, timeout, retry) shows it, red first.
- [x] `ChangesOnDisk.waitsForJdtlsToTakeTheChangesInBeforeItAsksForTheDiagnostics` fails when the `watch.await` of
      `takeInChangesOnDisk` is removed (the fake delays its acknowledgement, or one ordered log of the received messages
      is asserted).
- [x] `tellsJdtlsOfAFileEditedSinceTheLastRequestBeforeOpeningIt` and
      `tellsJdtlsOfTheFilesEditedSinceTheLastRequestBeforeAskingWhetherTheSymbolCanBeRenamed` fail when
      `takeInChangesOnDisk` moves after `open(...)`, or after `prepareRename`.
- [x] `bin/ci` green, coverage 100%.

Depends on: 30-10-2026-1717-jdtls-learns-of-the-changes-made-on-disk (closed).

## Resolution

Merged as `a9fecc5`. A retry after a timed-out wait now waits again before it asks jdtls, so it cannot answer for the old
text.

- `JdtlsLanguageServer` keeps the events jdtls has not acknowledged (`untaken`), sends them again with the next batch and
  clears them only after the wait succeeded. `WatchedFiles` is unchanged.
- Added beyond the issue: each batch ends with its own barrier file (`.gabier-changes-taken-<n>`). With one shared name, a
  late acknowledgement of the first batch could release the retry's wait, because `Diagnostics.expect` replaces the pending
  future of a path. The tests that named the file were updated to the new names; no assertion was weakened.
- Proofs: `ChangesOnDisk.aRetryAfterATimedOutWaitStillWaitsForJdtlsToTakeTheEarlierChangesIn` (edit, timeout, retry; red
  first: no second `didChangeWatchedFiles` was sent). The fake now delays its acknowledgement and one ordered log
  (`FakeJdtls.received()`) is asserted: removing `watch.await` fails four tests, among them
  `waitsForJdtlsToTakeTheChangesInBeforeItAsksForTheDiagnostics`; moving `takeInChangesOnDisk` after `open` fails three, after
  `prepareRename` fails one (mutations run by the worker).
- Checked by the worker's `bin/ci` on the last commit of the batch (`gabier-renames-a-symbol`): format 7s, build 4s, test
  114s, native 133s, jdtls 215s; no stage skipped (run in two passes after a Checkstyle fix, `test` alone, then the other
  stages). This commit alone was checked with `:daemon:check`.
- Review: one pass over the batch, nothing to report.

Tokens (the batch, one worker and one reviewer for two issues):
- worker: 292660 (one notification, for both issues)
- reviewer: 84302 (both issues)
- supervisor: 10 calls, output 6072, input + cache_write 17809 (cache read 786465), over the period of the batch, the
  `ship` step included; exchanges with the user: 0 messages
