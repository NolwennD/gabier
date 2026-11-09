# The daemon stops when nobody has used it for a while

Opened: 09-11-2026 19:57
On hold: the default delay is the user's decision (see below); lift the hold with the value.

Real risk met while reviewing the MCP server: no code stops the daemon but `gabier stop` (nothing says "idle" in
`modules/daemon`, and `docs/daemon.md` has no such rule). The daemon is one per project, shared by the sessions of that
project, but with MCP every session starts it when the host connects, so every project an agent has worked in leaves a
daemon and its jdtls (a JVM and the Gradle import) running until `gabier stop` or a reboot. The command line already
had the leak; MCP makes it the normal case.

What an agent can do once it is closed and could not before: open projects through its host without leaving a jdtls
running behind each of them.

Done when:
- the daemon stops itself, with jdtls and its workspace, after a delay with no request of any kind (`status` and
  `ping` count as a request); the delay is read from a variable (`GABIER_IDLE_TIMEOUT`, in minutes, `0` turns it off),
  has a default said in `docs/daemon.md`, and is tested with a fake clock, with no real wait;
- a command sent while the daemon is stopping, or just after, starts a new one and is answered (no refusal that the
  next call would not have), shown by a test;
- the MCP server does not stop the daemon when the host disconnects (other sessions and the command line share it);
- `gabier status` says the delay and the time left;
- `bin/ci` is green.

To decide: the default delay. A restart costs the whole import again (seconds for a small project, a minute or more for a
large one), so too short is a nuisance; supervisor's proposal: 30 minutes.
