# gabier is observable and auditable between the cli and the daemon

Opened: 02-11-2026 19:31
On hold: the user wants to rework the intent before it is worked, for a later release, no urgency (03-11-2026).

Goal: a good level of observability and audit of what gabier controls (decision of the user, 03-11-2026): the path from
the cli to the daemon and from the daemon back to the cli. What the agent does with an answer is not ours, but making it
less likely to go wrong is the point (the refusals and the friction that `.claude/rules/dogfooding.md` turns into
tickets, seen instead of remembered). jdtls is not ours either, only our code around it and our workarounds: it is
measured and logged at our edge, as a call that took so long and answered so, never from the inside.

Three kinds of information, each with its own use, tied together by one correlation id:
- **events** (event sourcing, decision of the user, 02-11-2026): the audit. Every request leaves an event in a local log
  that is never rewritten; the statistics are projections computed when they are read. An event has a type, a schema
  version, the instant it happened (the logs of several processes merge by sorting it) and its data: the method, dry run
  or not, the outcome (success, key of the refusal, or error), a hash of the project path; never a file content or a
  symbol name. Written by the daemon for what it answers, and by the cli for what the daemon cannot see (no daemon, the
  start that failed, the connection lost). No two processes write the same file, under `$XDG_STATE_HOME/gabier/events/`;
  how a short-lived cli does it without one file per call is decided at the start of the work;
- **traces**: the lines that say what happens for a request, on each side, for diagnosis (the request received, handed to
  jdtls, answered, refused);
- **timings**: how long each part takes, as seen from our side: the whole round trip at the cli (the daemon start
  included), the handling in the daemon, and the call to jdtls measured at its edge.

The **correlation id** is a UUIDv4 (`UUID.randomUUID`, no class of our own) made by the cli when it starts a command,
sent to the daemon in the request and sent back in the answer, so that the cli and the daemon speak of the same request.
It is in every event, trace and timing, and in the problem object of a refusal or an error (text and `--json`), so that
it can be quoted. A new context `usage`: events, projections and the ports (the log, the source of ids, the clock) in a
pure domain, the JSON-lines file as adapter, the writing in `RequestHandler.call` and in the cli, the reading in the cli.

Done when:
- each request leaves an event with its correlation id, on the daemon side and, for what only the cli sees, on the cli
  side; refusals and errors included;
- the id made by the cli is the same in the request, in the daemon's event and traces, in the answer and in the problem
  object of a refusal; two commands have two ids; proven with a fixed source of ids and a fixed clock, ports of the
  domain, so that the tests depend on neither chance nor time;
- the timings of a request (round trip, handling, call to jdtls) are in its events, and a call to jdtls that never
  answers shows as such;
- `gabier stats` prints, per command, the number of calls, of refusals by key, of errors and the usual durations, read
  from every log, with no daemon running;
- the help and `docs/` describe `gabier stats`, the log and the correlation id; `bin/ci` passes.

Cut it in two when it is worked (the id and the events, then `gabier stats` and the timings): it is more than one ticket.

Depends on: rename-the-base-package.
