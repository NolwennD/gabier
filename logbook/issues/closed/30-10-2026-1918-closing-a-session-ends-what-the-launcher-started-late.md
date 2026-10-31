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

## Resolution

Merged as `dac89b0`.

Checked by the worker's final `bin/ci --all` on the branch of the batch (log kept by the worker): format 5s, build 2s, test 74s (cli 71, core 259, daemon 366, protocol 320, shared 236, architecture 19, 0 failures), native 7s (cached build), jdtls 184s (real jdtls). The session records the process tree at process start, every `treeInterval` (250 ms in production, 20 ms in tests) on a daemon thread, and once more at close; the Javadoc says what is not seen (a process that lives less than one interval and whose launcher dies). Tests, red first for their own reason: `endsWhatALauncherStartedBeforeItDiedDuringTheStart` (`sh -c "sleep 300 & sleep 1; exit 3"`, initialize never answered) and `endsWhatALauncherStartedAfterTheSessionWasReady`.

Review (one pass): nothing reported on this issue.

Tokens:
- worker: 804170 for the whole batch (notifications 345758 and 458412, the second run after the review; the second figure may already include the first), shared by the five issues
- reviewer: 106756 (the first reviewer was stopped on the user's order and gave no figure; its work was redone), shared by the five issues
- supervisor: shared across the lifecycle batch, see the first issue
