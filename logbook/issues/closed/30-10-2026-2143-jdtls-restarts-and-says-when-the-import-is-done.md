# The daemon restarts a jdtls that died and waits for the end of the import

Opened: 30-10-2026 21:43

Found in the review of `the-daemon-serves-a-project-through-jdtls`:
- `JdtlsLanguageServer.state()` depends only on the start future (`JdtlsLanguageServer.java:96`) and `ProjectServer` retries
  only on `FAILED`: after `kill -9` of a ready jdtls, `status` says `ready` and every `gabier diagnostics` fails until the
  daemon is restarted;
- "ready" is a project listed plus the same list two seconds later (`JdtlsSession.java:156-166`), and the list stays fixed:
  jdtls that lists module A and imports module B three seconds later makes the session ready, and `diagnostics` of a file
  of B is refused as outside the project for good; a folder with no Gradle, Maven or Eclipse build lists nothing, so the
  first request waits the whole five minutes;
- a start interrupted by `close()` ends with a null message: a request waiting during `stop` gets "jdtls did not start:
  null" (`JdtlsLanguageServer.java:127`);
- when the launcher script exits at once, the message does not give its exit code, which `JdtlsWatch` is meant to report.

Done when:
- a jdtls that died while ready is seen as dead: `status` says so (`failed`, with the reason) and the next request
  starts it again; a test with a fake that dies, then the stage `jdtls` of `bin/ci` with a real `kill -9`;
- readiness comes from jdtls's own end-of-import signal (its status notification or the end of the import job), not from
  a list that is stable twice; a test on the fake with a module listed late, red first; a folder with nothing to import
  is refused at once with a problem object that says there is no build to import, not after five minutes;
- a request waiting while the daemon stops gets a message that says the daemon is stopping;
- a launcher that exits at once is reported with its exit code;
- `bin/ci` green, coverage 100%, nothing in `check` needs jdtls.

## Resolution

Merged as `3400209`.

Checked by the worker's final `bin/ci --all` on the branch of the batch (log kept by the worker): format 5s, build 2s, test 74s (cli 71, core 259, daemon 366, protocol 320, shared 236, architecture 19, 0 failures), native 7s (cached build), jdtls 184s (real jdtls). Choice, and what it replaced: readiness is jdtls's own `ServiceReady` (traced on jdtls 1.63.0: it is sent only after the import jobs, `ProjectsImported` events come first), and the list of projects is asked once; the "list stable twice" polling, `awaitSettledProjects` and `JdtlsSettings.ofWhatJdtlsImports` are removed, with four tests of that behaviour (replaced). A jdtls that died while ready is `FAILED`, ended and started again at the next request; a folder with no build is refused at once as `no-build-to-import` (real jdtls test); a request that waits while the daemon stops gets `jdtls did not start: the daemon is stopping`.

Review (one pass), two blocking findings, fixed in this commit: (1) the answer of a waiting request lost the race against the closing of the connections: `DaemonServer.close()` now half-closes the inputs and waits at most 5 s (`DISCONNECT_GRACE`) for the connections to end (`DaemonServerTest` red 3 times out of 3 with the old order; the real daemon test now asserts the answer); (2) `status` carries the reason: `DaemonStatus.jdtlsFailure` (optional member, escaped in the text of the cli, exact in `--json`), asserted after a kill -9 and for the no-build folder. `bin/ci.d/40-native` follows the new status line. Suggestions left: tests that do not pin their claim and the monitor of `ProjectServer.started()` held while a failed jdtls is ended: issue `the-jdtls-session-tests-pin-what-they-claim`. The new `@ExcludeFromGeneratedCodeCoverage` on `DaemonServer.stopReading` was added after the review, so it was not challenged: a point of the same issue. The worker did not build commits 3 and 4 on their own (one tree, tested at the end).

Tokens:
- worker: 804170 for the whole batch (notifications 345758 and 458412, the second run after the review; the second figure may already include the first), shared by the five issues
- reviewer: 106756 (the first reviewer was stopped on the user's order and gave no figure; its work was redone), shared by the five issues
- supervisor: shared across the lifecycle batch, see the first issue
