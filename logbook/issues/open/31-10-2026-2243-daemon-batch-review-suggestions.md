# Review suggestions of the daemon batch

Opened: 31-10-2026 22:43
On hold: review suggestions that break no `Done when` point and no use met yet; lifted if one of them is met while using
gabier (31-10-2026 refocus).

Found in the review of the batch `missing-file-is-not-outside-the-project`, `extract-variable-refuses-what-jdtls-refuses`,
`organize-imports-deletes-what-jdtls-cannot-resolve`. All in the module `daemon` and its scenarios.

Done when:
- [ ] The scenario of the ambiguous imports no longer depends on the JDK: it uses a type declared in two packages of the
      scenario project (`demo.a.Thing`, `demo.b.Thing`), not `sun.util.calendar.*` (`Scenarios.java:372-385`,
      `OrganizeImportsTest.java:178`). Setting `java.completion.filteredTypes` in `JdtlsSettings` is checked first: jdtls lists
      the `sun.*` candidates although the default filter names them (`Preferences.java:811-817`).
- [ ] The check of `extract-variable` compares the whole text after the rename with the text before, the identifier
      replaced, and not only the first line after the name (`JdtlsLanguageServer.java:373-384`): a merged edit
      `name = "Hello, " + name;\n        retur nam` is refused.
- [ ] `gabier help extract-variable` says "not tested", not "is not caught", of the collisions that jdtls renames
      cleanly; a collision with a local variable declared earlier has a scenario.
- [ ] `failsWhenTheFileCannotBeReadAtAll` (`JdtlsLanguageServerTest`) is skipped (`assumeThat`) as root and on a file system
      without POSIX permissions.
- [ ] A path through a file (`Main.java/x`) is refused as a missing file, not as an internal error: `textInProject` meets
      a `FileSystemException` ("Not a directory"), not a `NoSuchFileException`.
- [ ] `bin/ci` green, coverage 100%.
