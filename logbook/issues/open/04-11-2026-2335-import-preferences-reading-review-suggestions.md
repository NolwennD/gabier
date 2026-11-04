# The reading of the import preferences reports what it ignores and checks the ranges (review suggestions)

Opened: 04-11-2026 23:35
On hold: findings of the review of the ticket 04-11-2026-2157 that break no `Done when` point and no real use met so far (decision of 31-10-2026: held by default). The user, or the supervisor when a use makes one matter, lifts the hold.

Goal: a project that writes its import preferences wrongly is told so, in the same way whatever the file and the command.

Done when:
- `org.eclipse.jdt.ui.importorder=` (empty or blank) gets an "ignored" warning, as an empty `ondemandthreshold=` does (`ImportPreferences`, the order).
- A threshold of 0 or below (`ondemandthreshold=-5`, `"starThreshold": 0`) is refused with a warning and not sent to jdtls.
- The contradiction warning does not fire for `importorder=java;javax;org;com;` (Eclipse's trailing `;`, parsed as a trailing `""`) against the `.vscode` list `["java","javax","org","com"]`; a test compares the two.
- The `IllegalArgumentException` branch of `Properties.load` (`importorder=\uZZZZ`) has a test; the encoding of the `.prefs` file (UTF-8 today, ISO-8859-1 for `Properties`) is decided and tested with one non-ASCII byte.
- `gabier status` says when a file is ignored (a syntax error in `.vscode/settings.json`), as `organize-imports` does.
- The files are read once per request, so that the warnings describe the values sent to jdtls (`OrganizeImportsMethod`, `JdtlsLanguageServer.followTheImportPreferences`).
