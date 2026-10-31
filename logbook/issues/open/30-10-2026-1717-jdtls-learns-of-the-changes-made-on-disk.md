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
