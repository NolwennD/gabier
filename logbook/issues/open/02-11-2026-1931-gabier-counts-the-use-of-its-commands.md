# gabier counts the use of its commands

Opened: 02-11-2026 19:31
On hold: the user wants to rework the intent before it is worked, for a later release, no urgency (03-11-2026).

The user can see which commands the agents call, how often each is refused and why, and how long each takes: the
frictions that `.claude/rules/dogfooding.md` turns into tickets, measured instead of remembered. Event sourcing
(decision of the user, 02-11-2026): every request the daemon answers appends an event to a local log that is never
rewritten, and the statistics are projections computed when they are read. An event has a type, a schema version, the
instant it happened (the logs of several daemons merge by sorting it), and its data: the method, dry run or not, the
outcome (success, key of the refusal, or error), the duration, a hash of the project path; never a file content or a
symbol name. One file per daemon run under `$XDG_STATE_HOME/gabier/events/`, so no two processes write the same file. A
new context (`usage`): events, projections and the port of the log in a pure domain, the JSON-lines file as adapter, the
writing in `RequestHandler.call`, the reading in the cli.

A correlation id (decision of the user, 03-11-2026), as a logger has one: the daemon gives every request a UUIDv4
(`UUID.randomUUID`, no class of our own, no UUIDv7) when it receives it, and everything that happens for that request
carries it. It lets one follow what happens between the request leaving the system gabier controls (the call to jdtls)
and its answer coming back, and lets an agent quote it when it reports a friction. It is in the event, in every trace the
daemon writes about the request (the call to jdtls, the answer or the timeout, with the time between them), and in the
problem object of a refusal or an error (text and `--json`).

Done when:
- each request the daemon answers appends one event with the data above and its correlation id, refusals and errors
  included;
- the correlation id of a request is the same in its event, in the traces of its exchange with jdtls and in the problem
  object of its refusal or error, and two requests have two ids; proven with a fixed source of ids, a port of the
  domain, so that the tests do not depend on chance;
- `gabier stats` prints, per command, the number of calls, of refusals by key and of errors, read from every log, with
  no daemon running;
- the help and `docs/` describe `gabier stats`, the log and the correlation id; `bin/ci` passes.

Depends on: rename-the-base-package.
