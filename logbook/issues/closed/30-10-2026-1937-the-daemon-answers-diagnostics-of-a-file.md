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

## Resolution

Merged as `621e0e9`.

Checked by the worker's `bin/ci` (full, after the formatter): format 4s, build 12s, test 79s, native 127s, jdtls 109s.
The method `diagnostics` (`DiagnosticsMethod`, parameter `file`) answers `{"diagnostics":[{"severity":"error","line":4,
"column":15,"message":"..."}]}` in a type of `protocol` (`FileDiagnostics`, with `Diagnosis` and `Severity`), 0-based as the
domain. `DiagnosticsMethodTest` runs it through `RequestHandler` with the `FakeJavaLanguageServer` scripted by the core
scenarios (file with an error, with a warning, clean, outside the project, server not ready, missing parameter, a path
with a NUL, which is the new `invalid-parameter`); `DaemonServerTest` on a real socket; `GabierTest.Diagnostics` against
`ScriptedDaemon` (text 1-based `file:line:column: severity: message`, nothing for a clean file, `--json` 0-based, refusal
with exit 2, no daemon, a relative file sent as an absolute path, exit 0 whatever the diagnostics); `40-native` runs the
native `gabier` against the installed daemon (not ready, exit 2) and against a fake-served daemon.

How the fake is selected (a choice of the worker, confirmed by the review): the fake is a separate distribution,
`gabier-daemon-fake` (`:daemon:installFakeServed`, main class `FakeServedMain` in the source set `fakeServed` of
`modules/daemon`, outside the coverage gate, on the class path). The installed daemon cannot be switched to it by a
request, an argument or an environment variable; it uses the new `NoProjectServer`, which refuses everything as
`language-server-not-ready` until `the-daemon-serves-a-project-through-jdtls` replaces it. `DaemonServer.open` takes a
`JavaLanguageServer`. The skills `add-daemon-method` and `verify` and the README were updated.

Review (one pass): no BLOCKING finding; the isolation of the fake and the single +1 conversion hold. Suggestions, now
issues: the text of the diagnostics reaches the terminal raw, message and file name (`diagnostics-text-neutralises-
control-characters`); `normalize()` is lexical and `FakeServedMain` overwrites a non-empty folder
(`diagnostics-paths-are-resolved-as-the-shell-does`); a directory under the project gives an internal error, added to
`missing-file-is-not-outside-the-project`.

Tokens:
- worker: 198578
- reviewer: 69235
- supervisor: 10586 output, 23750 input + cache write over 10 calls (cache read 3058841), supervisor session over the
  period of the ticket (from the launch of the worker; the other work of the session included); exchanges with the user:
  0 messages
