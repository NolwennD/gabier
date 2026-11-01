# move: the transient folder survives a failure, a second request and an empty line

Opened: 01-11-2026 19:51
On hold: findings of the review of `01-11-2026-1835-gabier-moves-classes-to-a-package.md`, no `Done when` point broken and no real use yet
(`.claude/rules/workflow.md`, "Value first"); the user lifts the hold.

Done when:
- [ ] An empty line in the `files` parameter of the daemon (`a\n\nb`) is refused as `invalid-parameter` and not sent to the adapter as `Path.of("")`
      (`ChangeRequest.files()`); a test.
- [ ] Two requests that move to the same new package do not disturb each other: the whole move (the question to jdtls and the apply) is serialised, or a second
      dry run to `demo.archive` while a real move has made the folder is answered like the first (today it is refused as `nothing-to-move`, and the reverse order
      fails the `MakeFolder` step with `WRITE_FAILED`); a test for each order, or the page says so.
- [ ] `removeFolders` in the `finally` of `JdtlsLanguageServer.moveToPackage` (lines 609-615) does not replace the original exception (`addSuppressed`), and the
      `Deleted` event is sent even when a folder cannot be removed; a test on the failure path of jdtls with a non-empty folder.
- [ ] `docs/commands/move.md` says that a dry run makes the missing folders for the time of the question, that a kill between make and remove leaves an empty
      folder, and what a watching tool sees; or a way is found that makes none. `bin/ci` green, coverage 100%.
