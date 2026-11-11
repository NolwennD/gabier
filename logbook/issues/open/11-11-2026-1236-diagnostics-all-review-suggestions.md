# The whole-project diagnostics holds on edge cases of the file walk

Opened: 11-11-2026 12:36
On hold: findings of the review of `diagnostics --all` (PR 46) that break no `Done when` point and no use met yet; lifted
when a project shows one of them (a symlinked source folder is the likeliest).

The review of the ticket `diagnostics-can-cover-the-whole-project` found cases of the walk of the source folders that
`JdtlsLanguageServer` (`projectDiagnostics`, `javaFilesOfTheProject`, `published`, `textInProject`) does not settle or
document. The cases are checked on a project of the daemon tests (`JdtlsProjectDiagnosticsTest`).

Done when:
- A file deleted between the walk and its reading is handled as the port Javadoc (`JavaLanguageServer.projectDiagnostics`)
  says, or the Javadoc and `docs/commands/diagnostics.md` are corrected to what the code does (today it refuses the whole
  call with `fileMissing`); a test covers the case.
- A `.java` file that is not valid UTF-8 does not make the whole call fail without a name: the problem names the file, or
  the file is read as jdtls reads it; a test covers it.
- A source folder that is a symbolic link to a folder is walked, or the call says it left it out; it is never reported
  as clean in silence; a test covers it.
- A subfolder that cannot be read (permission denied) fails with a problem that names the folder, not a bare
  `UncheckedIOException`; a test covers it.
- The cost on a large project (all the texts held in memory, every `didOpen` sent before the wait) is measured on a
  project of thousands of files and stated in the page, or bounded; the choice is written in the resolution.
