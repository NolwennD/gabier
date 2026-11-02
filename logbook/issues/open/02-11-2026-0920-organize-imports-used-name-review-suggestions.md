# organize-imports used-name check: review suggestions

Opened: 02-11-2026 09:20
On hold: findings of the review of b1ce7f3; the first one loses code in a rare case (an import line with a trailing comment holding a `;`), so the user lifts the hold when it matters, or when it is met; the others break no `Done when` point.

Done when:
- [ ] `JdtlsLanguageServer.java:1174-1177` (`importedName`): the name is cut at the first `;`, not the last: `import java.sql.Date; // legacy;` with `Date when;` in the body is seen as used (diagnostics asked, refused), a test with that line.
- [ ] The scenario `ORGANIZE_IMPORTS_REFUSES_A_USED_IMPORT_JDTLS_CANNOT_RESOLVE` goes through the star import shortcut only, so the whole-word scan is not shown on the real jdtls; either a replayed scenario shows a single-type used import refused (if jdtls ever deletes one), or the issue's point and the Javadoc say that only star imports of an inaccessible package are deleted by jdtls. The Javadoc claims (jdtls keeps an import of a missing class the file uses, keeps an inaccessible single-type import) are shown by a replayed scenario or removed.
- [ ] Tests keep deliberate the over-detection of a name inside a comment or javadoc and of a `$` in a name (`Outer$Inner`): they cost a wait, never a loss.
