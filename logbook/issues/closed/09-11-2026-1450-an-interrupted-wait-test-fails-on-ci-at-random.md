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

## Resolution

Commit f9345b2 (PR #38). Cause, in the test and not in the cli: the test interrupted the cli as soon as the daemon had read the second request, when the cli is usually still blocked reading the answer of `status` on a `SocketChannel`; an interrupt there closes the channel (`ClosedByInterruptException`), which `DaemonClient.call` maps to "the connection was lost"; only an interrupt during `Thread.sleep(POLL)` gives "the wait for jdtls was interrupted". Reproduced with 24 busy loops on 16 cores and a temporary `@RepeatedTest`: 31 of 300 failed at line 357, all with the "connection was lost" message.

- Fix (test only): `StartingJdtlsDaemon` stays silent on `status` (`staysSilentOnStatus()`) and a latch (`awaitsAStatusRequest()`) tells the test when to interrupt, so the cli is at or about to be at the blocking read; the test is renamed `aWaitThatIsInterruptedWhileItAwaitsTheStatus...`. The moment "the wait for jdtls was interrupted" is a new `SocketDaemonTest` calling `statusAfter` with the interrupt already set (as `DaemonLauncherTest` does).
- Proof: same load, 500 repetitions of each test, 0 failures (`StartingJdtlsTest$Waiting` 511 tests, `SocketDaemonTest` 500); the repetition annotations were removed before the commit.
- Choice: the point "every moment gives the same documented result" is met for the category (`daemon-connection-failed`), the exit code 1 and the kept interruption flag; the message differs by moment, and a common message would be a change of production code, not made. An interrupt during `tryConnect` is swallowed into "the daemon stopped answering…" (`DaemonClient.tryConnect` returns an empty `Optional`): not tested, not changed.
- `bin/ci` of the worker on 72ff66d, the last commit before the branch was rebased over a README commit (a `.md` change, so no new run by the worker): format, build, test, native, jdtls passed; release-install skipped (no changed file reaches `install.sh`). Check `bin-ci` of PR #38 green on the rebased branch (10m8s; 11m41s on the first push).
- Not reviewed: small ticket (13 lines of production code, a rewrite of the text of the guide tool; the rest is test code).

Tokens:
- worker: 181,045 (its completion notification).
- reviewer: none (not reviewed).
- supervisor: 22 calls, output 14,391, input + cache write 35,637 (cache reads 5,798,003), covering the supervisor session from the launch of the worker (17:00 UTC) to the merge, other work included (the README, the draft of the notes of the release, the pull request); exchanges with the user: 0 messages.
