# gabier start: two starts at once, a silent daemon and the other small points of the review (review suggestions)

Opened: 08-11-2026 14:09
On hold: findings of the review of `gabier-start-warms-up-the-daemon`; none breaks a `Done when` point; the first two matter only when two agents start at once or the daemon hangs.

- `Invocation.java` (askStarting) and `Start.java:~64`: `daemonStarted` is set before `launcher.connect` and nothing stops two cli processes from starting a daemon. Two `gabier start` at the same time with no daemon both print "started" and a log, though only one daemon owns the socket; `DaemonLauncher.createPrivately` also deletes the log the winner is writing. Related: `concurrent-first-requests-start-one-daemon`.
- `Start.java:~100` (statusOf): `DaemonClient` has no read timeout; a daemon that accepts the connection and never answers `status` makes `start --wait --timeout=5` hang past 5 s (the check is made between polls only).
- `Start.java:~78` (pause): an interruption of the local wait is reported as `daemon-connection-failed` while the daemon is fine; a test pins that message.
- `Start.java:~66`: `untilReady` polls `status` every 250 ms and `WaitNotice` polls it again on its own thread: two calls per round.
- `bin/ci.d/40-native:~1500`: the Javadoc of `startsTheDaemonOnDemand` now sits above `startWithTheFakeServer`'s own, two stacked comments.
- `StartingJdtlsTest`: no test at the cli of "jdtls failed, then `gabier start --wait` again" (tested only in `ProjectServerTest.WarmingUp.startsJdtlsAgainWhenTheLastStartFailed`).

Done when:
- [ ] each point above is either fixed with a test, or answered in this issue's resolution with the reason; `bin/ci` passes.
