# The lines of the daemon log carry the time

Opened: 26-11-2026 09:43

An agent that measures how long jdtls takes to start reads the log of the daemon (`<socket>.log`, see
`docs/daemon.md`) and cannot date any line: `jdtls started`, the statuses, `ServiceReady`, a retry on an empty
workspace, `did not shut down cleanly`. Seen on another project, where a start lost about five minutes and the log
could not say where. Once this is closed, the duration between two lines of the log can be read from the log alone.

Done when:
- every line the daemon writes to its log, its own and the ones relayed from jdtls (its output included), begins with
  the time it was written, to the second at least, in the local time of the machine with its offset;
- a test proves it with a fixed clock, for a line of the daemon and for a relayed line of jdtls;
- what the documentation says of the log (`docs/daemon.md`, and the help of a command if one describes it) says that
  the lines are dated;
- `bin/ci` is green.
