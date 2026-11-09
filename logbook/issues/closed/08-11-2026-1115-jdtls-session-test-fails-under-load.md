# JdtlsSessionTest aFailureToCloseAfterAFailureToStartIsAddedToItNotInstead no longer fails when the machine is busy

Opened: 08-11-2026 11:15

The hold is lifted for the release v0.5.0 (09-11-2026): the release workflow runs `bin/ci` on the tagged commit, and a test that fails at random there leaves a tag with no release.

`modules/daemon`, `JdtlsSessionTest > StartFailing > aFailureToCloseAfterAFailureToStartIsAddedToItNotInstead` (line 382
at the time) failed once under load. A test that depends on timing makes a ticket go red for nothing.

Done when:
- [ ] the cause is found (a wait, an order of threads), shown by running the test under load (several in parallel), and the
      test no longer depends on it; `bin/ci` passes.

## Resolution

Commit 2d52051 (PR #38). Cause, in the test: the fake interrupts the test thread on receipt of a request (`onInitialize`, `onShutdown`), which can be before the thread has left the write that sent it; the pipes of the fake are `java.nio.channels.Pipe`, interruptible, so an interrupt inside the write closes the sink. Two outcomes followed: the shutdown write failed with `ClosedByInterruptException` or `ClosedChannelException`, which `askToLeave` read as "did not shut down cleanly" (no suppressed `InterruptedException`), and the interrupt flag stayed set so `awaitEnd` skipped its wait and the process was still alive at `assertNothingLeftBehind`. The 5 s shutdown timeout was not involved. The real jdtls uses process streams, not interruptible channels: production is not affected. Reproduced under the same load: at least 14 of the first ~100 runs failed (the run was cut, no final total).

- Fix (test only): `interruptOnceAwaitingAnAnswer(thread)` waits until the thread is WAITING or TIMED_WAITING with a `CompletableFuture` frame on its stack, parked on the answer, before interrupting; after 10 s it interrupts anyway so the test fails visibly instead of hanging. The assertions are unchanged.
- Proof: same load, 500 repetitions, `StartFailing` 509 tests, 0 failures; the repetition annotation was removed before the commit.
- `bin/ci` of the worker on 72ff66d, the last commit before the branch was rebased over a README commit (a `.md` change, so no new run by the worker): format, build, test, native, jdtls passed; release-install skipped (no changed file reaches `install.sh`). Check `bin-ci` of PR #38 green on the rebased branch (10m8s; 11m41s on the first push).
- Not reviewed: small ticket (13 lines of production code, a rewrite of the text of the guide tool; the rest is test code).

Tokens:
- worker: 181,045 (its completion notification).
- reviewer: none (not reviewed).
- supervisor: 22 calls, output 14,391, input + cache write 35,637 (cache reads 5,798,003), covering the supervisor session from the launch of the worker (17:00 UTC) to the merge, other work included (the README, the draft of the notes of the release, the pull request); exchanges with the user: 0 messages.
