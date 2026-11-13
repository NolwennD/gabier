# The installed-daemon rename test is stable

Opened: 13-11-2026 20:32

`InstalledDaemonTest.renamesASymbolOfAProjectOnTheRealJdtlsAndAnotherRenameSeesTheFirst` (`modules/daemon`,
`integrationTest`, real jdtls) failed once on GitHub, in the `jdtls` stage of `bin/ci` of the tag `v0.6.0` (run
43468410891): a rename answered `language-server-no-such-symbol` at 5:18 of `demo/Greeter.java`, a position that holds the
method. The same commit passed on the re-run, and the same code had passed in the pull request. The release was delayed
by it, and a flaky gate teaches to re-run instead of read.

Find why the request reaches jdtls before it knows the symbol, in the test or in the daemon, and fix the cause.

Done when:
- The cause is found and written in the `## Resolution`: which call fails, what jdtls has not finished at that moment, and
  whether a real client can meet it too (then the daemon waits for what it should, and the help says what it needs to).
- The fix keeps every assertion of the test and adds none that weakens it: no sleep, no retry loop around the assertion.
- The test passes in 20 consecutive runs on a loaded machine (run it next to a build), and the count is in the resolution.
- `bin/ci` passes.

## Resolution

Fixed in 2fb53d4 (pull request 58, check `bin-ci` green in 16 min 58 s).

- **The failing call**: line 376 of the test at `v0.6.0`, the first call (the dry-run rename at 5:18), read from the log of
  the CI run. No file had changed on disk before it.
- **Cause**: jdtls sends `ServiceReady` at the end of its initialization job, builds the project, and only then registers
  the files it watches. The daemon saw `ready` about 0.1 s after the job and the registration came about 2 s later
  (measured on 4 runs). `diagnostics` waited for the registration (`Builds.awaitInitialized`); the refactorings, the
  references, the moves, the creations and the imports did not, so the daemon told jdtls nothing of the files changed on
  disk and jdtls refactored from stale state. A real client meets it too: `gabier start` followed at once by a rename.
- **Fix**: `JdtlsLanguageServer.takeInChangesOnDisk` starts with `builds.awaitInitialized(session, watch)`, so every
  request that touches files waits for the registration; the call in `JdtlsDiagnostics.afterTheBuild` is removed as
  redundant. One sentence added to the help of `status` (`Operations`). `FakeJdtls` registers `**/*.java` when a test
  scripts no watcher, as a real jdtls does. The test is untouched: no assertion changed, no sleep, no retry.
- **Proof**: a test first, `JdtlsLanguageServerTest.Renaming.asksJdtlsNothingBeforeItHasRegisteredTheFilesItWatchesThenRenames`,
  red before the fix. On the real jdtls, with 400 generated files added to the test project, the test failed 6 of 6 times
  before the fix and passed 6 of 6 after (the 2500-file variant failed 5 of 5 before). 20 consecutive passes of the
  unmodified test, one Gradle invocation each, with a load average of about 17 on 16 cores.
- **Not shown**: the exact refusal of CI (`no-such-symbol` on call 1) did not reproduce locally in about 70 runs of the
  original test. The window is real, it is on the failing call, and it is closed; the 400-file variant shows the same
  cause with another symptom (`language-server-invalid-name` on the third call).
- `bin/ci --all` of the worker: format 20 s, build 4 s, test 303 s (cli 1650, core 927, daemon 1272, architecture 24; 0
  failures), release-install 2 s, release-notes 2 s, native 216 s, jdtls 1173 s, all passed.
- **Review** (one, neutral): nothing blocking. Three suggestions, opened as one issue on hold
  (`13-11-2026-2258-the-wait-for-the-watchers-is-tested-and-described`): no test of a jdtls that never registers its
  watchers; the default registration of `FakeJdtls` covers `*.java` only; the new sentence of the help of `status` says
  "built the project" and "a few seconds" where the code waits for the registration.
- **Worth remembering**: `rename`, `references` and the others answer `language-server-no-such-symbol` (and `rename-token`
  `language-server-invalid-name`) when jdtls works from stale state, in the same shape as for a wrong position, so a
  transient failure reads like a user error. Not changed here; no issue opened, no real use has asked.

Tokens:
- worker: 267053
- reviewer: 78888
- supervisor: 23 calls, 13722 output, 285 k input + cache write (2.43 M cache read), over the period of the ticket
  (19:32 to 22:16 UTC) and including the 0.6.0 release follow-up; exchanges with the user: 1 message.
