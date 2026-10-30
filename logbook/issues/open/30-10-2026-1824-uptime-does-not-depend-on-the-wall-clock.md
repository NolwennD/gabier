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
