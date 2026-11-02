# `gabier organize-imports` removes a stale import without waiting or refusing

Opened: 31-10-2026 22:43

Found in the review of the daemon batch (`organize-imports-deletes-what-jdtls-cannot-resolve`). Since that ticket, an
`organize-imports` whose edit removes an import waits for the diagnostics of the file (the 5 s `QUIET` period) and refuses
it as `language-server-unresolved-imports` when jdtls reports an ERROR on a removed line (`JdtlsLanguageServer.java:482-505`).
Two cases hurt an agent: removing an unused import is the common case and now costs 5 s (`Untidy.java` of the
`ORGANIZE_IMPORTS` scenario); and a file with an unused import of a class that was deleted (`import demo.Gone;`) is refused,
where jdtls's deletion is right. An agent that deletes a class and fixes the files that imported it meets it at once.

Done when:
- [ ] The diagnostics are asked only when a removed import's simple name still appears in the file outside the import lines
      (only then can code that is used be lost); a scenario replayed on the real jdtls shows that removing an unused import
      does not wait.
- [ ] A file with an unused import of a class that does not exist is organized (the import removed), a scenario replayed on
      the real jdtls; an import that jdtls cannot resolve and whose name the file still uses is still refused, with the imports named, the file untouched,
      a scenario replayed on the real jdtls (the probe of the project, which checked it on `FakeServedMain`, goes: ticket
      `02-11-2026-0826`).
- [ ] `docs/commands/organize-imports.md` says what the command waits for and when it refuses. `bin/ci` green, coverage 100%.
