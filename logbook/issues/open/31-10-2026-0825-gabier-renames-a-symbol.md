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
- [ ] `gabier rename` by position and by `--token` renames on disk; `--dry-run` writes nothing; shown on the fake
      language server (`GabierTest` against `ScriptedDaemon`, daemon tests on a real socket).
- [ ] The files changed and the diff are in the answer, in text and in `--json`; the README ("Command line") and the
      usage text document them with an example.
- [ ] A refusal of the server and a change set that cannot be applied come out as problem objects, nothing written.
- [ ] The installed daemon renames a symbol of a small project on the real jdtls (the `selfCheck` or the integration
      test), and `bin/ci` is green, coverage 100%.

Depends on: 30-10-2026-1717-jdtls-learns-of-the-changes-made-on-disk (same files; a second rename must see the first).
