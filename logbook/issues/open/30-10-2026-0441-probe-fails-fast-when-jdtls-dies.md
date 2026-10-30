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
