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

## Resolution

Merged as `1b1dc35`. Every line of the daemon log begins with its time (local time of the machine, offset, to the second):
the lines of the daemon, the `log.accept` ones, the `jdtls:` statuses, the raw stderr of jdtls (relayed line by line by
`OutputRelay`, it was inherited and undated) and the `java.util.logging` records of the libraries (`JulLog`, one dated line
each). `docs/daemon.md` and the notes of `gabier help start` say it.

Why: the log was born as the place where the output of the daemon goes when the cli starts it (`8bce856`), and no later
ticket asked for a format; nothing decided against dating.

- Checked: the worker's `bin/ci` (format, build, test, native, jdtls passed; release-install and release-notes skipped, no
  changed file reaches them), and the check `bin-ci` of the pull request (#64) green. Each `Done when` point is proven by
  `DatedLinesTest`, `OutputRelayTest`, `JulLogTest`, `JdtlsLauncherTest` and the `MainTest.Serving` tests with a fixed clock.
- Review: no blocking finding. Suggestions, all in `26-11-2026-1033-dated-log-lines-review-suggestions.md`: the last stderr
  lines of jdtls may be lost at shutdown or dated after the `exited` line, `JulLog` drops the causes of an exception, its
  installation is in `main` (not covered, also run for `--version`), two constructors of `JdtlsLauncher` default to undated
  output, two added lines run past 120 columns.
- Not done through gabier: imports, the launcher record signature and the constructors of `Main` were edited as text.

Tokens:
- worker: 167661 (`subagent_tokens`)
- reviewer: 84059 (`subagent_tokens`)
- supervisor: 30 calls, output 13244, input + cache write 42174 (cache read 2901750), over 09:43 to 10:40 on 26-11-2026, other work included; exchanges with the user: 2 messages
