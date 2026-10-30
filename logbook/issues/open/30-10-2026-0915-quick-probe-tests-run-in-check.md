# The quick probe tests run in `check`

Opened: 30-10-2026 09:15

`ProbeWatchTest` and `JdtlsClientTest` (in `modules/daemon/src/integrationTest`) need no jdtls, yet they run only
under `./gradlew jdtlsProbe`, so `check` and the `test` stage of `bin/ci` never run them and their assertions can go
stale unnoticed. Give them their own cheap test task, wired into `check`, and keep `jdtlsProbe` for the test that
starts the real jdtls.

Done when:
- `./gradlew check` runs the tests that need no jdtls, and shows their results;
- `./gradlew jdtlsProbe` still runs the real probe, without requiring `GABIER_JDTLS` for the quick tests;
- breaking one assertion of `ProbeWatchTest` on purpose (then undoing it) turns `check` red.

Depends on: probe-reports-diagnostics (both edit the `integrationTest` source set).
