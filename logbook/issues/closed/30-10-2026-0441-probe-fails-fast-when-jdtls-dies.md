# The jdtls probe fails fast when jdtls dies

Opened: 30-10-2026 04:41

The review of the jdtls probe found that nothing watches the jdtls process. If jdtls dies or stays silent, each of the
three waits (the answer to `initialize`, `ServiceReady`, the project list) lasts the full ten minutes, up to thirty in
all, and the message does not say that the process exited. A `language/status` of type `Error` is ignored. The
`integrationTest` source set is compiled by no stage, so it can rot unnoticed.

Done when:
- the probe fails at once, with the exit code in the message, when the jdtls process exits, and fails at once on a
  `language/status` of type `Error`;
- the three waits share one deadline, and a timeout always says what was awaited (the bare `get(1, MINUTES)` too);
- `integrationTest` is compiled by `./gradlew check` (or by a stage of `bin/ci`) without starting jdtls;
- killing the jdtls process during a run (try it, then undo) makes the probe fail in seconds, and nothing is left behind.

Depends on: use-assertj-for-assertions (both edit `JdtlsProbeTest`).

## Resolution

Done in a08b47b. `ProbeWatch` races each wait of the probe against `Process.onExit()` and against a future the client
completes on a `language/status` of type `Error`; the three waits share the deadline it computes once, and every timeout
says what was awaited. `check` now depends on `integrationTestClasses`, so `integrationTest` is compiled without
starting jdtls (the lighter of the two options the issue allowed).

Checked by the subagent: `bin/ci` green (format, build, test, native; jdtls skipped in the first run for lack of
`GABIER_JDTLS`, then `test` and `jdtls` green with it: 15 new tests and the real probe in 62 s). Kill proof on the real
jdtls: `kill -9` of the pid printed by the probe, by pid only; the probe failed 3 seconds later with `jdtls exited with
code 137 after 16.2 seconds, while waiting for the language/status notification of type ServiceReady`, and no process or
`/tmp/gabier-jdtls-data-*` folder was left.

Review (one neutral reviewer, nothing blocking). Fixed by the supervisor right after the merge: the timing-flaky
`after 0.3 seconds` assertion, `onExit()` called once instead of at each wait, no shutdown request to a jdtls that is
already dead, a line over 120 columns in the `verify` skill. The quick tests (`ProbeWatchTest`, `JdtlsClientTest`) run
only under `jdtlsProbe`, never in `check`: opened as the issue `quick-probe-tests-run-in-check`. Left as matters of
taste: an `Error` status and an exit arriving together report only the `Error`; unused catch variables could be `_`.
Unrelated, left: the Kotlin warning for JDK 27 in `build-logic`, and LSP4J warning about the unhandled jdtls
notification `language/eventNotification`.
