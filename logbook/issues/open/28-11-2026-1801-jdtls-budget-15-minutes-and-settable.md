# The jdtls budget is 15 minutes and can be set

Opened: 28-11-2026 18:01

Real bug met while using gabier on theano: jdtls imports the project for longer than 5 minutes, so the start fails with
`Timed out after 300.0 seconds`. An agent can use gabier on a project whose import is slow, and the user can raise the
budget without a rebuild.

Done when:
- [ ] The default budget of a start and of a request (`ProjectServer.TIMEOUT`) is 15 minutes, and the daemon gives that
      budget to jdtls (`JdtlsSettings.of`), proven by a test.
- [ ] An environment variable of the daemon, `GABIER_START_TIMEOUT`, in minutes, sets it, read like `GABIER_IDLE_TIMEOUT`
      (`Main`); a value that is not valid is refused with `usage-error` (`UsageException`), each case tested.
- [ ] The default timeout of `gabier start --wait` is checked against the new budget and kept coherent with it; the
      decision is written in the resolution.
- [ ] `docs/daemon.md` and the helps that name the old budget stay true.

Found while reading the open issue `25-11-2026-2136` (same constant): its second point is done by this one.
