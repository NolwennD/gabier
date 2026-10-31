# The tests of the jdtls session pin what they claim

Opened: 31-10-2026 07:44
On hold: test hardening; taken up after the parity with the Python driver, or earlier if one of these tests really
flakes in `bin/ci` (31-10-2026 refocus).

Findings of the review of the jdtls lifecycle batch: tests that pass for the wrong reason, or that depend on timing,
and a lock held too long. All in `modules/daemon`.

Done when:
- `JdtlsSessionTest` `aTimeoutNamesTheServiceReadyStatus` does not depend on the machine speed (a deadline that the
  fake's earlier steps cannot use up) and asserts `fake.projectRequests() == 0`: the list of projects is asked only
  after `ServiceReady`;
- a jdtls that is ready but never answers `java.project.getAll` is a test (the timeout of that step names it);
- a test shows that the list is read at `ServiceReady` and not "once, whenever": scripted answers [A] then [A, B]
  give the answer that matches the moment;
- `InstalledDaemonTest` no-build test asserts that `daemon.process.descendants()` is empty after the answer, and the
  one that checks the log line `jdtls exited with code ` polls for it (the line is written after `isAlive()` turns false);
- `ProjectServer.started()` does not hold its monitor while it ends a failed jdtls that is not dead, so that `close()`
  is not blocked by it (a test with a failed server whose end blocks, and a `close()` that returns).
- the `@ExcludeFromGeneratedCodeCoverage` of `DaemonServer.stopReading` (a connection that ends between the snapshot and
  the half-close) is challenged: a test that places the close there, or a reason the review accepts, written in the
  resolution.
