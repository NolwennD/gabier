# jdtls learns of the changes made on disk

Opened: 30-10-2026 17:17

An agent edits files between two calls to gabier (its own edits, or the change set of a previous `rename`). After that,
`rename` and `diagnostics` must answer for the text on disk, not for what jdtls read earlier. Today `JdtlsLanguageServer.rename`
reads the file but neither opens it nor tells jdtls of changes on disk (`diagnostics` opens the file), and nothing, unit
test or replay, asks for a rename after the files changed. First step, before any code: read the Eclipse JDT LS
documentation on file changes (the client capability `workspace.didChangeWatchedFiles`, the watchers jdtls registers,
what it does on `didOpen`, `didChange` and `didClose`), and write in the resolution which setting or notification gives
the behaviour; configure jdtls with it, do not work around it.

Done when:
- [ ] The resolution says what the JDT LS documentation prescribes for files changed outside the editor, and which
      capability or notification the adapter now uses for it.
- [ ] A replay on the real jdtls (a scenario of the catalogue, skill `add-scenario`) renames a symbol, applies the change
      set to the project on disk, then renames it again and gets the answer for the new text; and a file edited on disk
      between two `diagnostics` gets the diagnostics of its new text.
- [ ] A unit test on the fake covers what the adapter sends.
- [ ] `bin/ci` green.

## Resolution

Merged as `4ea4b70`. An agent can now edit files between two gabier calls: `rename` and `diagnostics` answer for the text
on disk, shown on the real jdtls (a second rename after the first change set was applied, which answered "There is no
symbol at 5:18" before).

What the Eclipse JDT LS documentation and source prescribe, and what the adapter does about it:
- jdtls does not watch the disk. It registers its watchers with a `client/registerCapability` request for
  `workspace/didChangeWatchedFiles`, after `ServiceReady`, and only if the client declares
  `workspace.didChangeWatchedFiles.dynamicRegistration` (`ClientPreferences.isWorkspaceChangeWatchedFilesDynamicRegistered`;
  it also needs `textDocument`). The globs seen on the real jdtls: `**/*.java`, `**/.project`, `**/.classpath`,
  `**/.settings/*.prefs`, `**/src/**`, `**/*.gradle`, `**/*.gradle.kts`, `**/gradle.properties`, `**/pom.xml`.
- The client then sends `workspace/didChangeWatchedFiles`; jdtls queues the events and handles them on its own thread.
  It has no acknowledgement, so a request sent right after is racy: the adapter appends a Deleted event for a file that
  does not exist (`.gabier-changes-taken`) and waits for jdtls to publish empty diagnostics for it, which proves the
  earlier events were handled in order.
- Code: `JdtlsCapabilities` declares the capability, `JdtlsClient` answers `registerCapability`/`unregisterCapability`,
  the new `WatchedFiles` keeps the registered globs and a snapshot of the project folder (mtime, size) taken before
  `initialize`, and `JdtlsLanguageServer.takeInChangesOnDisk` runs before each `diagnostics` and `rename`. `JdtlsSettings`
  was not needed.

Checked by the worker's full `bin/ci` on the branch: format 5s, build 2s, test 79s (cli 71, core 264, daemon 392,
protocol 320, shared 236, architecture 19, 0 failures; JaCoCo 100%, Checkstyle), native 123s, jdtls 182s; no stage
skipped. Each `Done when` point: the contract sequels `rename-after-a-rename` and `diagnostics-after-an-edit` replayed on
the real jdtls (`:daemon:jdtlsProbe --tests '*ScenarioReplayTest'`, red with `takeInChangesOnDisk` disabled: `"Welcomer"
is not a valid name for the symbol at 2:13`), `SequelsTest` in `:core`, and `JdtlsLanguageServerTest.ChangesOnDisk` on the
fake (changed, created and deleted file, told once, nothing told when nothing changed, edit during the import, no glob
matches, timeout without acknowledgement). Skills `add-scenario` (sequels) and `verify` (`WatchedFiles`, the fake) updated.

Review (one pass, no blocking finding). Suggestions, all in one issue,
`a-request-after-a-timed-out-sync-still-waits-for-jdtls`: the two ordering tests cannot fail if `takeInChangesOnDisk` moves
after `open`/`prepareRename`, the wait test cannot fail without `watch.await`, and `changes()` sets `known = now` before the
acknowledgement, so a retry after a timeout skips the wait and may get the answer for the old text.

Known limits, left as they are (the reviewer showed no case that breaks a `Done when` point, no issue opened): each
request walks the whole project folder, `.git` and `build` included; a same-size rewrite within the same millisecond is
missed; a request between `ServiceReady` and the registration of the watchers gets no events (edits made before are
reported afterwards). The first one is worth measuring if requests become slow on a large checkout.

Tokens:
- worker: 272555 (one notification)
- reviewer: 72474
- supervisor: 17 calls, output 11461, input + cache_write 35943 (cache read 2122422), over the period of the ticket, the
  `ship` step and the pause request included; exchanges with the user: 2 messages
