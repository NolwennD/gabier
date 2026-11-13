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
