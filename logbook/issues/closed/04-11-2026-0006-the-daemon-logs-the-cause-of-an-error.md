# The daemon logs the cause of an internal error

Opened: 04-11-2026 00:06

Met by the worker of `rename-a-record-component`: when a request ends in `internal-server-error`, the daemon writes
nothing about the cause in its log, so finding it takes a throwaway test. An agent or a maintainer who meets the error
can then say what happened (decision of the user, 04-11-2026: observability of what gabier controls; the correlation id of
`gabier-is-observable-and-auditable` will tie it to the request, this ticket does not wait for it).

Repro (before `rename-a-record-component`): `gabier rename --dry-run modules/core/src/main/java/eu/gabier/editing/domain/Location.java 14:29 path`
answers `internal-server-error`; `$XDG_RUNTIME_DIR/gabier/<project>.sock.log` holds no line about it (the
NullPointerException of jdtls is in the data of its answer, dropped by `RequestHandler.call` and the mapper).

Done when:
- a `RuntimeException` that `RequestHandler.call` turns into `internal-server-error` leaves its type, its message and
  its cause (the data of the jdtls error included) in the daemon log, with the method of the request; a test with the
  fake daemon reads the log;
- no file content and no symbol name goes to the log; `bin/ci` passes.

## Resolution

Commit ca87719 (pull request 44). `bin/ci` of the worker, full run on the last commit: format passed, build passed, test passed, native passed, jdtls passed, release-install and release-notes skipped (no changed file reaches them); coupling ceilings unchanged (6 / 5 / 11, 162 scenarios). Check of pull request 44 (`bin-ci`): pass.

- A request that ends in `internal-server-error` leaves in the daemon log its method, the type, message and cause chain of the exception and, for an error jdtls answered, its code and data; no request parameter and no file content is written (`RequestHandlerTest.Logging`, `FailureLogTest`, `DaemonServerTest`, `MainTest.Serving`, which reads the real log file with the fake). `docs/daemon.md` and `docs/cli.md` say it.
- Beyond the issue's text: a `GeneratorException` that carries a cause is logged too, so a refusal that wraps a jdtls failure keeps its data in the log. The words of the exceptions may hold a path or a name: `docs/daemon.md` says so.
- Review: no blocking finding. The suggestions are grouped in `11-11-2026-*-failure-log-and-request-failed-review-suggestions.md` (on hold), except the wording of `docs/daemon.md` on what the log holds (it may hold a path or a name), fixed by the supervisor.

Tokens:
- worker: 116793 (one worker for the batch of four issues, three commits)
- reviewer: 125503 (one review of the batch)
- supervisor: 34 calls, output 22845, input + cache write 241562 (cache reads 4118124), over the period of the batch (launch of the worker 11-11-2026 01:47 UTC to the merge), other work included; exchanges with the user: 0 messages
