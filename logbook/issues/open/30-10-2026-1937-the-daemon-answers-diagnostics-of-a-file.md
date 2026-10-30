# The daemon answers the diagnostics of a file, and `gabier diagnostics` shows them

Opened: 30-10-2026 19:37

The diagnostics are read from jdtls, in domain types, by the port `JavaLanguageServer.diagnostics` and the use case
`Diagnose`; nothing reaches them from outside (the daemon has `status` and `stop`, the cli the same). This slice exposes
them, so that an agent can ask what the incremental compiler of jdtls reports on a file instead of building. Written and
tested against the fake language server (`.claude/rules/workflow.md`, "Fake it until you run it"); how the daemon starts
the real jdtls for a project on demand is a ticket of its own, not this one.

`gabier diagnostics <file>` prints one diagnostic per line, `file:line:column: severity: message` (line and column
1-based, as an editor and a compiler show them; the protocol and `--json` keep the 0-based positions of `Position`), or
nothing when the file is clean. The exit code is 0 whenever the daemon answered, whatever the diagnostics: the reader
reads the list (an option to fail on an error can come later; the user can change this choice). A refusal (file outside
the project, server not ready) is the problem object, with its exit code.

Done when:
- the method `diagnostics` takes a `file` parameter and answers a list of diagnostics (severity, line, column, message)
  as a type of `protocol`, read by the client; a test on `RequestHandler` with the fake server: a file with an error, a
  file with a warning, a clean file (empty list), and a refusal of the server turned into its problem object;
- a test on a real socket in a temporary directory shows it answers there (`DaemonServerTest`);
- `gabier diagnostics <file>` in text and with `--json`, tested in `GabierTest` against `ScriptedDaemon`; the README
  ("Command line") and the usage text say it;
- the command is part of `Daemon.check` in `bin/ci.d/40-native`, against the native `gabier` and the installed daemon
  served by the fake server (no jdtls in this stage);
- `bin/ci` green, coverage 100%.

Depends on: 30-10-2026-1937-daemon-methods-take-parameters (the `file` parameter).
