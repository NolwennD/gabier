# The cli explains why the daemon is unreachable and what to do

Opened: 03-11-2026 23:52

A user (or an agent) whose `gabier` cannot reach its daemon is told what happened and gets what it takes to choose the
right fix (decision of the user, 03-11-2026: without the daemon the cli can do nothing and has no memory, so the message is the
whole answer). Today the three refusals say the reason, the socket, the log and the exit code at best:
`daemon-not-running` ("No daemon answers on <socket>: start it with gabier-daemon"), `daemon-start-failed` ("The daemon
did not start: <reason>") and `daemon-connection-failed` ("The connection to the daemon failed: <reason>"). They do not
say which cause is the likely one, nor what each fix costs.

Done when:
- each of the three says, from what the cli can observe, the probable cause and the action that goes with it, one per
  case the cli tells apart: a socket file left by a dead daemon (remove it, or `gabier stop`), `gabier-daemon` not
  found or not executable (where the cli looked), a JDK missing or too old (which `java` it found, which release is
  needed), the daemon that exited (its exit code, the last lines of its log), a daemon silent for too long (how long it
  waited, where its log is), a connection that broke or a hang-up with no answer (the daemon may have been stopped or
  killed: run the command again, `gabier status`);
- the project and the socket in play are in the message (`GABIER_PROJECT`, `GABIER_SOCKET` when they decide it), so that
  a wrong folder is seen at once; a test per case, on the fake daemon of the cli tests;
- `gabier help` and `docs/` list the causes and the actions; the keys and the exit code do not change; `bin/ci` passes.
