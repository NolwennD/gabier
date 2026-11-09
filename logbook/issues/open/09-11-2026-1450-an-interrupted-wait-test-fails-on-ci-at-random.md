# A test of the interrupted wait fails on CI at random

Opened: 09-11-2026 14:50

Real bug met while shipping PR 37: the check `bin-ci` went red on `StartingJdtlsTest > Waiting >
aWaitThatIsInterruptedIsAConnectionFailureAndKeepsTheInterruption` (the assertion on `err()`, line 357: it does not
contain "the wait for jdtls was interrupted"), on a branch that touches neither the test nor the code it runs. The test
waits for two requests, then interrupts the thread: the interrupt can land while the cli reads the socket, which gives
another failure message. A red check on a correct branch costs a CI run (about 10 minutes) and a rerun.

Done when:
- the cause is found (what the thread is doing when the interrupt arrives, and what the cli answers then) and said in
  the resolution;
- the test, or the cli if the answer is wrong, is fixed so that every moment of the interrupt gives the same documented
  result (a connection failure that keeps the interruption), shown by a test that interrupts at each of the moments or
  by a deterministic hand-off in place of the polling loop;
- the test passes 200 times in a row (`--rerun-tasks` in a loop, the count said in the resolution);
- `bin/ci` is green.
