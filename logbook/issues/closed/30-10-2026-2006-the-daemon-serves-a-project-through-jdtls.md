# The daemon serves one project through the real jdtls

Opened: 30-10-2026 20:06

Dogfooding needs gabier to work on gabier: today the daemon answers `status` and `stop` and nothing starts jdtls for it.
The daemon is started for one project root (an argument or `GABIER_PROJECT`, the checkout it is run in by default), starts
the jdtls session on the first request that needs it (not at start, not for `status`), keeps it for the next requests,
and ends it at `stop` and at SIGTERM, with no process left (`JdtlsSession.close`). The operations plug in on the
`JavaLanguageServer` port as before: the real adapter `JdtlsLanguageServer` stands where the fake stood in `check`.

Done when:
- `gabier status` tells the project root and whether jdtls is not started, starting, ready or failed; a jdtls that cannot
  start is a problem object, not a hang (timeout stated), and the next request tries again;
- a request that needs the server starts it once, whatever the number of requests at the same time (a test with
  concurrent requests and a counting fake starter);
- stopping the daemon while jdtls starts or runs ends it with no process left, shown on the real jdtls in the stage
  `jdtls` of `bin/ci` (the installed daemon, a small project, `diagnostics` of a file with an error through the socket,
  then `stop`);
- `bin/ci` green, coverage 100%, nothing in `check` needs jdtls.

Depends on: 30-10-2026-1937-the-daemon-answers-diagnostics-of-a-file (the first operation to serve).

## Resolution

Merged as `0d9ef4a` (rebased three times on `main` as the batches landed; the last two rebases were over logbook-only or
already-green changes).

Checked by the worker's `bin/ci --all` on the rebased commit (before the last logbook-only rebase): format 5s, build 47s,
test 102s, native 149s, jdtls 180s, the native and jdtls stages really run; cli 67, core 259, daemon 304, protocol 255,
shared 236, architecture 19, no failure. `ProjectServer` starts jdtls on the first request that needs it and keeps it
(`ServedProject`, `StartedLanguageServer`; `NoProjectServer` is gone); `status` never starts it and shows
`gabier-daemon 0.1, pid N, up Ns, project <root>, jdtls not-started|starting|ready|failed` (`--json` adds `project` and
`jdtls`); a start that fails is the problem object `jdtls-start-failed` (exit 1) that states the five-minute timeout, the
next request tries again, every request that waited for a failed start gets its failure; 20 simultaneous requests make
one start (`ProjectServerTest.Starting.startsItOnceWhateverTheNumberOfRequestsAtTheSameTime`); `stop` ends jdtls before the
client is released, so `gabier stop` returns once jdtls is gone (`InstalledDaemonTest` in the stage `jdtls`: the installed
daemon on a small project with an error, `not-started`, then the error at line 4 column 15, `ready`, `stop`, no process
left; and a `stop` while jdtls still starts). The native stage runs the installed daemons with `GABIER_JDTLS` pointing at
a missing file, so it can never start a real jdtls.

Choices taken: the project root is the argument of `gabier-daemon [<project folder>]`, else `GABIER_PROJECT`, else the
working directory, resolved to its real path; the launcher is `GABIER_JDTLS`; the first request that needs jdtls blocks
until it is ready (no `language-server-not-ready` before the daemon is closed); five minutes for the whole start and for
each later request; for an arbitrary root `JdtlsSettings.ofWhatJdtlsImports` (a sixth component, `awaitSettledProjects`)
declares the session ready once jdtls has listed a project and the list is the same twice, two seconds apart, because
waiting for the root itself timed out on this Gradle checkout (jdtls lists the modules, not the root); on this checkout the
first `diagnostics` took about 97 seconds, the second 8. `JdtlsStartException` (key `jdtls-start-failed`, internal-error
status) maps a start failure to a problem object. The rebase added `TerminalText.neutralise` on the `project` and `jdtls`
fields of `gabier status`.

Review (one pass): no BLOCKING finding; no race found in `ProjectServer`, `close()` or the order of `DaemonServer.close`.
Suggestions, now issues of the module `daemon`: `jdtls-restarts-and-says-when-the-import-is-done` (a jdtls that died is
never restarted; the readiness heuristic; the message of an interrupted start; the exit code of a launcher) and
`the-daemon-tests-and-docs-leave-nothing-behind` (the leaks of `InstalledDaemonTest`, `FakeServedMain`, the raw standard
error of jdtls, the README on the duration of `stop` and on an untrusted checkout).

Tokens:
- worker: 290331 (its last completion notification; it was resumed by messages)
- reviewer: 87157
- supervisor: 122137 output, 186744 input + cache write over 81 calls (cache read 28824027), supervisor session over the
  period of the ticket (from the launch of the worker; the other workers and the rest of the session are included, so
  the figure is shared with the batches of the period); exchanges with the user: 1 message
