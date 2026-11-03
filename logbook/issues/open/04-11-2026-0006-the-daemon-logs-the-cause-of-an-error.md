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
