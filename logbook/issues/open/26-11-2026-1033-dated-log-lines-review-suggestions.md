# The dated daemon log keeps what the undated one kept

Opened: 26-11-2026 10:33
Depends on: 26-11-2026-0943-the-daemon-log-lines-carry-the-time

Review of the ticket that dates the lines of the daemon log. The log is where a failed jdtls is explained, so what it
printed before the change must still reach it. Once this is closed, a jdtls that dies leaves its last words in the log,
dated, and a library that fails leaves its stack trace.

Done when:
- the last stderr lines of a jdtls that writes a stack trace and exits at once (or a `stop` that follows) reach the log
  before the daemon ends, and before the line that says jdtls exited: a test shows it without polling
  (`OutputRelay`, `JdtlsSession`);
- a `java.util.logging` record that carries an exception keeps its causes and frames in the log, dated (`JulLog`); a
  test with a nested cause shows it;
- the installation of `JulLog` is exercised by a test (today it sits in `Main.main`, which is excluded from coverage for
  "all its logic is in run"), and is done only for a daemon that serves a project, not for `--version` and
  `--self-check`;
- the `JdtlsLauncher` constructors that default the output to an undated `System.err::println` are used by tests only,
  or removed;
- the new lines of the `start` help (`Start.java`) and of `docs/daemon.md` wrap at 120 columns like the text around
  them;
- `bin/ci` is green.
