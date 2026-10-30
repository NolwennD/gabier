# Closing a jdtls session ends what its launcher started before or after the recording

Opened: 30-10-2026 19:18

`JdtlsSession` records the process tree once, after `awaitReady()` (`JdtlsSession.java:142-143`), and kills the recorded
tree with the current one at `close()`. Found in the review of `closing-a-session-ends-an-orphaned-jdtls`:
- a launcher that dies during the start (`fake.child("sh", "-c", "sleep 300 & exit 3")`) leaves nothing recorded: `start()`
  closes the session and the `sleep` survives, although the javadoc says the tree is ended "when the start fails" too;
- a process the launcher spawns after the recording (a second `sleep` one second after ready, then the launcher killed)
  is in neither the record nor the live tree and survives `close()`;
- `JdtlsSessionTest` (`endsTheChildrenOfALauncherThatDiedBeforeTheClose`, around lines 357-376) leaks its `sleep 300`
  when an assertion fails before `close()`: the fake's teardown sees no descendant of the dead launcher;
- the javadoc line at `JdtlsSession.java:30` is well over 120 columns.

Done when:
- a test per case above is red first for its own reason: a launcher that dies during the start, a child started after
  the recording (the tree is recorded again when it can change, or the choice not to is stated and the javadoc says so);
- the test of the orphan destroys what it started in a `finally`;
- the javadoc says what the code does and fits 120 columns;
- `bin/ci` green, coverage 100%.
