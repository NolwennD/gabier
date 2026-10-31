# JdtlsSessionTest does not depend on timing

Opened: 30-10-2026 14:02
Depends on: 30-10-2026-1402-closing-a-session-ends-an-orphaned-jdtls (same test class)

Three weak spots in `JdtlsSessionTest`, found by the review of the adapter:
- `SHORT` (500 ms) is the one deadline for initialize, ServiceReady and the project polling: on a slow machine
  `aTimeoutNamesTheProjectsStillMissing` times out on initialize and reports the wrong message.
- `fake.process().descendants()` is read right after `start()`, before the `sh` child may have forked: `isNotEmpty()`
  is racy.
- `session.watch().pause("a short pause", ...)` asserts nothing; it only covers the accessor.

Done when:
- [ ] Each timeout test fixes the step it times out on, so its message does not depend on the speed of the machine.
- [ ] The test waits for the child before reading `descendants()`.
- [ ] The pause test asserts something, or the accessor is covered by a test that does.
- [ ] `bin/ci` green.

## Resolution

Merged as `d9b61f7` (first commit of the batch).

Checked by the worker's final `bin/ci --all` on the branch of the batch (log kept by the worker): format 5s, build 2s, test 74s (cli 71, core 259, daemon 366, protocol 320, shared 236, architecture 19, 0 failures), native 7s (cached build), jdtls 184s (real jdtls). Test hardening, so no test was red first. `SHORT` (500 ms) stays only for the initialize timeout, where the fake never answers; the later-step timeouts use a 2 s deadline; `FakeJdtls.whoseLauncherStarts(n)` makes `start()` return once the children have exec'd; `theWatchOfTheSessionPausesForTheIntervalAndSeesJdtlsDie` covers the pause.

Review (one pass): suggestion that `aTimeoutNamesTheServiceReadyStatus` still shares its 2 s deadline with initialize: issue `the-jdtls-session-tests-pin-what-they-claim`.

Tokens:
- worker: 804170 for the whole batch (notifications 345758 and 458412, the second run after the review; the second figure may already include the first), shared by the five issues
- reviewer: 106756 (the first reviewer was stopped on the user's order and gave no figure; its work was redone), shared by the five issues
- supervisor: shared across the lifecycle batch, see the first issue (20:53 to 07:07 UTC, with a night in between, the period overlaps the terminal-text batch so the figures are not additive: 121 calls, 120532 output, 371076 input + cache write (33982006 cache read); exchanges with the user: 12 messages)
