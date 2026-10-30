# A failed permission change leaves no socket behind

Opened: 30-10-2026 18:24

In `DaemonServer.open`, if `guard.restrict(socket)` fails after `bind`, the listener stays open, the socket file stays
on disk and the shutdown hook is not registered yet. The next daemon removes the file as stale, but this one leaks its
listener. Found in the review of `daemon-listens-on-a-unix-socket`.

Done when:
- when `restrict` fails, the listener is closed, the socket file is removed and the original error reaches the caller
  (shown by a test whose guard fails in `restrict`);
- a successful start behaves as before;
- coverage stays at 100%.

Depends on: uptime-does-not-depend-on-the-wall-clock (same module, `daemon`, same file `DaemonServer`).
