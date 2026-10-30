# The daemon serves one project through the real jdtls

Opened: 30-10-2026 20:06

Dogfooding needs gabier to work on gabier: today the daemon answers `status` and `stop` and nothing starts jdtls for it.
The daemon is started for one project root (an argument or `GABIER_PROJECT`, the checkout it is run in by default), starts
the jdtls session on the first request that needs it (not at start, not for `status`), keeps it for the next requests,
and ends it at `stop` and at SIGTERM, with no process left (`JdtlsSession.close`). The operations plug in on the
`JavaLanguageServer` port as before: the real adapter `JdtlsLanguageServer` stands where the fake stood in `check`.

Done when:
- `gabier status` tells the project root and whether jdtls is not started, starting, ready or failed; a jdtls that cannot
  start is a problem object, not a hang (timeout stated), and the next request tries again;
- a request that needs the server starts it once, whatever the number of requests at the same time (a test with
  concurrent requests and a counting fake starter);
- stopping the daemon while jdtls starts or runs ends it with no process left, shown on the real jdtls in the stage
  `jdtls` of `bin/ci` (the installed daemon, a small project, `diagnostics` of a file with an error through the socket,
  then `stop`);
- `bin/ci` green, coverage 100%, nothing in `check` needs jdtls.

Depends on: 30-10-2026-1937-the-daemon-answers-diagnostics-of-a-file (the first operation to serve).
