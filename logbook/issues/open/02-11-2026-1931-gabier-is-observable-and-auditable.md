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
  symbol name. Written by the daemon for what it answers; no two processes write the same file, one file per daemon run
  under `$XDG_STATE_HOME/gabier/events/`;
- **traces**: the lines that say what happens for a request, on each side, for diagnosis (the request received, handed to
  jdtls, answered, refused);
- **timings**: how long each part takes, as the daemon sees it: the handling of the request, and the call to jdtls
  measured at its edge.

The **correlation id** is a UUIDv4 (`UUID.randomUUID`, no class of our own) made by the daemon when it receives a request
(decision of the user, 03-11-2026: the daemon is the backend of the cli, so the cli stays thin and depends little on the
evolutions of the daemon: it makes no id and knows nothing of the log). The daemon sends it back in the answer. It is in
every event, trace and timing of the request, and in the problem object of a refusal or an error (text and `--json`), so
that it can be quoted. What the cli sees alone (no daemon answers, the start failed, the connection was lost) has no id,
since no daemon made one: whether the cli records it, and how, is decided at the start of the work. A new context
`usage`: events, projections and the ports (the log, the source of ids, the clock) in a pure domain, the JSON-lines file
as adapter, the writing in `RequestHandler.call`, the reading by `gabier stats`.

Done when:
- each request the daemon answers leaves an event with its correlation id, refusals and errors included;
- the id made by the daemon is the same in its event, its traces, the answer and the problem object of a refusal; two
  requests have two ids; proven with a fixed source of ids and a fixed clock, ports of the domain, so that the tests
  depend on neither chance nor time; the cli sends nothing new to the daemon;
- the timings of a request (handling, call to jdtls) are in its events, and a call to jdtls that never
  answers shows as such;
- `gabier stats` prints, per command, the number of calls, of refusals by key, of errors and the usual durations, read
  from every log, with no daemon running;
- the help and `docs/` describe `gabier stats`, the log and the correlation id; `bin/ci` passes.

Cut it in two when it is worked (the id and the events, then `gabier stats` and the timings): it is more than one ticket.

Depends on: rename-the-base-package.
