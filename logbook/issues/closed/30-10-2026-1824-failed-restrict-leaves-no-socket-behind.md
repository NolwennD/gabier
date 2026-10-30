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

## Resolution

Merged as `57d1e47` (third commit of the batch).

Checked as the first issue of the batch. `DaemonServer.open(Path, SocketGuard, LongSupplier, long, ServedProject)` is a new overload (the one with the `String uid` delegates to it); `restrictOrGiveBack` closes the listener and deletes the socket file when `restrict` throws, adds a failure of the cleanup as suppressed and rethrows the original error, which `open` wraps as `socket-unavailable`. `SocketGuard` lost `final` so that a test can subclass it. Tests in `DaemonServerTest.Start`: `closesTheListenerAndRemovesTheSocketWhenTheSocketCannotBeRestricted` (a client queued on the listener is dropped), `reportsTheOriginalErrorAndWhatCouldNotBeCleanedUpWhenTheSocketCannotBeRestricted`, `aSuccessfulStartAfterAFailedOneTakesTheSocket`; with the cleanup disabled 3 tests fail.

Review (one pass): see the first issue of the batch. In the issue `the-daemon-server-bounds-its-connections`: a `bind` that fails leaves the listener open (real, confirmed); the delete is not scoped to our file; `SocketGuard` should be final again.

Tokens:
- worker: shared with the batch, see the first issue (126954)
- reviewer: shared with the first issue (62392)
- supervisor: shared with the batch, see the first issue
