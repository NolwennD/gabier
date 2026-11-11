# The whole-project diagnostics say what an empty answer does not prove

Opened: 11-11-2026 14:39
On hold: findings of the review of `diagnostics --all` from the jdtls stream (PR 47) that break no `Done when` point and no
use met yet; lifted when a project shows one of them (an unbuildable project answered as clean is the likeliest).

The review found cases of `JdtlsLanguageServer.projectDiagnostics`, `Builds` and `WatchedFiles` that no test settles, and
statements in the page and the Javadoc that read as guarantees.

Done when:
- A file deleted on disk after jdtls published an error for it no longer appears in the answer; a fake test (error
  published, file deleted, jdtls publishes an empty list) and a step of the replay on the real jdtls cover it.
- A project that jdtls cannot build, where the error is published on the project and not on a file (see the note of
  `EclipseProject`), is not read as clean: the answer or the page says that an empty answer does not prove the build is
  healthy; a test covers the case.
- The page `docs/commands/diagnostics.md` and the Javadoc of `Builds` say what the mechanism gives (jdtls answered the
  build, then published nothing for the settle time) and not more: a publication that comes later is missed, as the
  Javadoc of `Builds` already says of the order of publications.
- A jdtls that never registers the files it watches, or unregisters them and does not register again, is covered by a
  test: the call waits until its deadline and says what it ended on, and later calls do not each pay the whole deadline.
