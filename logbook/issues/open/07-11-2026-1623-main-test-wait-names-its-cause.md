# The daemon test that waits for the socket names the cause when it does not accept (review suggestion)

Opened: 07-11-2026 16:23
On hold: finding of the review of `a-daemon-test-fails-now-and-then`; it concerns the message of a failure that no longer happens.

- `modules/daemon/src/test/java/eu/gabier/daemon/MainTest.java:201-204`: the wait ends after 500 tries or when the
  daemon exits, then asserts only `assertThat(socket).exists()`. A socket file that exists but does not accept passes
  that assertion, and the test fails later on the original `ConnectException`, which names nothing.

Done when:
- [ ] the assertion is `assertThat(listening(socket)).as("the daemon accepts on " + socket).isTrue()`, so a daemon that
      never accepts fails there with the socket in the message; `bin/ci` passes.

Also, from the same ticket's report (to open on its own when it matters): `SocketGuard.clearStale` treats a socket file
that refuses connections as stale and deletes it; two daemons started at the same instant can delete each other's
socket between the bind and the listen (same root cause as the fixed test).
