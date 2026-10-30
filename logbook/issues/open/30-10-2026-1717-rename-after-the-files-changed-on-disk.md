# A rename after the files changed on disk is tested

Opened: 30-10-2026 17:17

`JdtlsLanguageServer.rename` reads the file but does not open it in jdtls (as `diagnostics` does) nor tell it of changes
on disk. Nothing, unit test or replay, asks for a rename after the files changed: rename `greet` to `welcome`, apply the
change set, then rename `welcome` again. Found by the review of the rename adapter.

Done when:
- [ ] A replay on the real jdtls (a scenario added with the skill `add-scenario`, or a test of its own if the catalogue
      cannot hold it) renames a symbol, applies the change set to the project on disk, then renames it again and gets the
      answer for the new text; or shows what jdtls answers when it is not told, and the adapter tells it of the changes
      (`didChangeWatchedFiles` or `didOpen`).
- [ ] A unit test on the fake covers what the adapter sends.
- [ ] `bin/ci` green.
