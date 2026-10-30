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

## Resolution

Done in b1b6a54. A `quickProbeTest` task, over the same `integrationTest` source set, runs every class but
`JdtlsProbeTest` (excluded by its class name) and is wired into `check`; `jdtlsProbe` includes only `JdtlsProbeTest`
and alone needs `GABIER_JDTLS`. A class-name filter was chosen over a JUnit tag so that `JdtlsProbeTest` stays
untouched. JaCoCo stays disabled for both tasks, so the coverage gates are unchanged. The stage `bin/ci.d/30-test` now
cleans `quickProbeTest` too (`cleanTest` does not) and counts its results; `jdtlsProbe`'s are left to the jdtls stage.

Checked by the subagent: `bin/ci` green with `GABIER_JDTLS` set (format 2s, build 3s, test 29s, native 84s, jdtls 46s;
29 quick tests, cli 9, daemon 22, protocol 4, architecture 12, no failure). A deliberately broken assertion of
`ProbeWatchTest` made `check` fail (`expected: "broken-on-purpose" but was: "ready"`), and undoing it made it green.
`jdtlsProbe` without `GABIER_JDTLS` runs the real probe only and fails with its message.

Review (one neutral reviewer, nothing blocking). Fixed by the supervisor after the merge: the wording about adding a
test that starts jdtls (`realProbe` is one class name), and the sentence of the `verify` skill that said `check` does
not run the integration source set. Left: `realProbe` goes stale without a warning if `JdtlsProbeTest` is renamed
(`quickProbeTest` would then run the real probe, loudly when `GABIER_JDTLS` is unset); the stage counts are print-only.
