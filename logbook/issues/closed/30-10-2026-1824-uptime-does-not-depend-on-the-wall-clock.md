# The uptime of the daemon does not depend on the wall clock

Opened: 30-10-2026 18:24

The uptime reported by `status` is measured with `Clock.systemUTC()` (the `StatusMethod` supplier in `DaemonServer`).
If the system clock is set back after the start, `Duration.between` is negative, the `DaemonStatus` constructor
(`uptime.toMillis()` at least 0) throws, and `gabier status` reports an internal error on a healthy daemon. Found in the
review of `daemon-listens-on-a-unix-socket`.

Done when:
- the uptime is measured on a monotonic source (`System.nanoTime`), behind a seam the tests script;
- a test whose wall clock goes backwards after the start still gets a non-negative uptime from `status`;
- coverage stays at 100%.

Depends on: a-line-over-the-limit-is-refused (same module, `daemon`).

## Resolution

Merged as `1681c68` (second commit of the batch).

Checked as the first issue of the batch. `DaemonServer.open` takes a `LongSupplier nanoClock` instead of a `Clock`, as `Diagnostics` does; `Main` passes `System::nanoTime`; the uptime is `Duration.ofNanos(now - startedAt)`, right across the overflow of the long. The daemon no longer reads the wall clock at all (a search for `Clock`, `Instant` and `currentTimeMillis` in the main code of the daemon finds only `nanoClock`). Proof: `DaemonServerTest.Requests.theUptimeIsMeasuredOnTheMonotonicSourceAndNeverOnTheWallClock` with a scripted clock that starts 30 s before `Long.MAX_VALUE`. Gap against the wording of the issue: no test where a wall clock goes backwards, because the server has none to move; the point is met by construction, a return to `Instant.now()` would fail that test since the scripted clock would no longer drive the value (confirmed by the review). The test advances the clock only 2.5 s, so it never wraps: in the issue `the-daemon-server-bounds-its-connections`.

Review (one pass): see the first issue of the batch.

Tokens:
- worker: shared with the batch, see the first issue (126954)
- reviewer: shared with the first issue (62392)
- supervisor: shared with the batch, see the first issue
