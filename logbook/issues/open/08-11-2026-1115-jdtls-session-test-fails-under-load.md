# JdtlsSessionTest aFailureToCloseAfterAFailureToStartIsAddedToItNotInstead no longer fails when the machine is busy

Opened: 08-11-2026 11:15
On hold: seen once, by the worker of `the-first-call-says-why-it-is-slow`, in `bin/ci --fast` while another worker shared the machine; it passed alone and in a rerun of the stage.

`modules/daemon`, `JdtlsSessionTest > StartFailing > aFailureToCloseAfterAFailureToStartIsAddedToItNotInstead` (line 382
at the time) failed once under load. A test that depends on timing makes a ticket go red for nothing.

Done when:
- [ ] the cause is found (a wait, an order of threads), shown by running the test under load (several in parallel), and the
      test no longer depends on it; `bin/ci` passes.
