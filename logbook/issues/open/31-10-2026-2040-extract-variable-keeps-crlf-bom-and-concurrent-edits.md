# `gabier extract-variable` keeps CRLF, BOM and concurrent edits

Opened: 31-10-2026 20:40
On hold: no file with CRLF, a BOM or a concurrent save met while using gabier yet; lifted if one is (31-10-2026 refocus).
Depends on: 31-10-2026-2040-extract-variable-refuses-what-jdtls-refuses.md (same file, `JdtlsLanguageServer`).

Found in the review of `gabier-extracts-a-variable`; the cases are untested, not shown wrong on a real file yet.
- CRLF: `TextFile.edit` rewrites the newlines of the edit to the file's terminator, then `introducedName` reads
  `where.offset()` of jdtls inside `extracted`: a bare `\n` in jdtls's `newText` shifts the offset, the name read and the
  rename position by one per inserted line (`JdtlsLanguageServer.java:272-277`).
- BOM: `original` is read with `Files.readString` (U+FEFF stays), `ChangeApplier` decodes without it: on line 0 the
  columns are off by one (`:261,286`).
- Stale file: nothing checks the file between the read and the write; `SourceText.replacing()` gives one edit from the first
  to the last changed line, so a save inside that span is overwritten.

Done when:
- [ ] A CRLF file, a BOM file (extraction on line 0) and a file with a non-BMP character before the selection are extracted
      right: scenarios replayed on the real jdtls, red first.
- [ ] A file saved between the read and the write is refused as stale, nothing written (a test that fails without the check).
- [ ] `bin/ci` green, coverage 100%.
