# A request after a timed-out sync still waits for jdtls to take the changes in

Opened: 31-10-2026 09:11

Found in the review of `jdtls-learns-of-the-changes-made-on-disk` (all in `modules/daemon`). `WatchedFiles.changes()` sets
`known = now` before jdtls acknowledges the events: if `rename` times out waiting (jdtls busy), the immediate retry finds
no change, skips the wait and asks jdtls while the earlier events may still be queued, so it can answer for the old text.
Also, three tests in `JdtlsLanguageServerTest` stay green when the code is wrong.

Done when:
- [ ] A retry after a timed-out wait still waits for the acknowledgement of the earlier events before it asks jdtls; a test
      (edit, timeout, retry) shows it, red first.
- [ ] `ChangesOnDisk.waitsForJdtlsToTakeTheChangesInBeforeItAsksForTheDiagnostics` fails when the `watch.await` of
      `takeInChangesOnDisk` is removed (the fake delays its acknowledgement, or one ordered log of the received messages
      is asserted).
- [ ] `tellsJdtlsOfAFileEditedSinceTheLastRequestBeforeOpeningIt` and
      `tellsJdtlsOfTheFilesEditedSinceTheLastRequestBeforeAskingWhetherTheSymbolCanBeRenamed` fail when
      `takeInChangesOnDisk` moves after `open(...)`, or after `prepareRename`.
- [ ] `bin/ci` green, coverage 100%.

Depends on: 30-10-2026-1717-jdtls-learns-of-the-changes-made-on-disk (closed).
