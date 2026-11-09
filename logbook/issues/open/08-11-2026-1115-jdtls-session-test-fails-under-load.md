# JdtlsSessionTest aFailureToCloseAfterAFailureToStartIsAddedToItNotInstead no longer fails when the machine is busy

Opened: 08-11-2026 11:15

The hold is lifted for the release v0.5.0 (09-11-2026): the release workflow runs `bin/ci` on the tagged commit, and a test that fails at random there leaves a tag with no release.

`modules/daemon`, `JdtlsSessionTest > StartFailing > aFailureToCloseAfterAFailureToStartIsAddedToItNotInstead` (line 382
at the time) failed once under load. A test that depends on timing makes a ticket go red for nothing.

Done when:
- [ ] the cause is found (a wait, an order of threads), shown by running the test under load (several in parallel), and the
      test no longer depends on it; `bin/ci` passes.
