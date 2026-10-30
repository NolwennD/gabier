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
