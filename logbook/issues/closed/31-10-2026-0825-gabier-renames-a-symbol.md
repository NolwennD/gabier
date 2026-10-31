# gabier renames a symbol

Opened: 31-10-2026 08:25

An agent renames a Java symbol and everything that refers to it with one command, instead of editing text. The core
(`Rename`, `RenameTarget`, `ChangeApplier`) and the jdtls adapter exist; the daemon and the cli do not expose them. Add
the daemon method (skill `add-daemon-method`) and the cli command: `gabier rename <file> <line> <column> <new-name>`
(positions from 1, as `diagnostics` shows them) or `gabier rename --token <file> <identifier> <new-name>`, with
`--dry-run`. The answer lists the files that changed (created, edited, moved) and the unified diff; text and `--json`
follow the conventions of `diagnostics`. A refusal (no symbol, invalid name, file outside the project, jdtls not ready)
is the problem object.

Done when:
- [x] `gabier rename` by position and by `--token` renames on disk; `--dry-run` writes nothing; shown on the fake
      language server (`GabierTest` against `ScriptedDaemon`, daemon tests on a real socket).
- [x] The files changed and the diff are in the answer, in text and in `--json`; the README ("Command line") and the
      usage text document them with an example.
- [x] A refusal of the server and a change set that cannot be applied come out as problem objects, nothing written.
- [x] The installed daemon renames a symbol of a small project on the real jdtls (the `selfCheck` or the integration
      test), and `bin/ci` is green, coverage 100%.

Depends on: 30-10-2026-1717-jdtls-learns-of-the-changes-made-on-disk (same files; a second rename must see the first).

## Resolution

Merged as `78f5495`. An agent renames a Java symbol and its references with `gabier rename`, by position or by `--token`,
with `--dry-run`, and sees the files that changed and the diff. It is the first refactoring that gabier exposes.

- Protocol: two daemon methods, `rename` (`file`, `line`, `column`, `newName`, `dryRun`) and `rename-token` (`file`, `token`,
  `newName`, `dryRun`), because the parameters of a `DaemonMethod` are all required; `RenameRequest` holds the shared parsing.
  The answer is the new protocol type `ChangedFiles` (`created`, `renamed`, `deleted`, `modified`, `diff`). Positions are
  1-based on the command line and 0-based on the wire; a line or column that is not a number or is below 1 is a usage error.
- Text output: `modified`, `created`, `renamed a -> b` and `deleted` lines, a blank line, then the diff; a dry run starts with
  `dry run, nothing written`. `--json` is the exact result.
- jdtls: no setting or capability was needed beyond the ones of `jdtls-learns-of-the-changes-made-on-disk`
  (`workspace.didChangeWatchedFiles.dynamicRegistration`). The adapter's `prepareRename`/`rename` are used as they were.
- Proofs: `RenameMethodsTest` (15 tests, fake server scripted by `Scenarios.RENAME_METHOD` and `RENAME_TYPE`),
  `DaemonServerTest` (real socket), `GabierTest.Renaming` (request sent to `ScriptedDaemon`, refusals in text and JSON),
  `ChangedFilesTest`, the native stage (`gabier rename` against `gabier-daemon-fake`: dry run, by position, a type rename that
  moves its file, refusals with exit 2) and `InstalledDaemonTest.renamesASymbolOfAProjectOnTheRealJdtlsAndAnotherRenameSeesTheFirst`
  on the real jdtls (dry run, rename by position, rename back by token, no-symbol refusal, file unchanged). A change set that
  cannot be applied: `aChangeSetThatCannotBeAppliedIsRefusedWithItsProblemObjectAndNoFileIsWritten` (`editing-stale-edit`, both
  files unchanged). README ("Command line") and usage text updated; skills `verify` and `add-daemon-method` updated.
- Checked by the worker's `bin/ci`: format 7s, build 4s, test 114s, native 133s, jdtls 215s; no stage skipped (two passes,
  see the other issue of the batch).
- Review: one pass, nothing to report.
- Found, left open: the text diff goes through `TerminalText.neutralise`, so a tab shows as `\u0009` and a backslash is
  doubled; the text diff of tab-indented or backslash-heavy source is lossy (`--json` is exact). Opened `On hold` (a choice
  about the neutraliser). `Main.java:74` has an Error Prone `OperatorPrecedence` warning that predates the batch.

Tokens: see `31-10-2026-0911-a-request-after-a-timed-out-sync-still-waits-for-jdtls.md`, same batch (worker 292660, reviewer
84302, supervisor 10 calls / output 6072 / input + cache_write 17809 over the batch, 0 messages from the user).
