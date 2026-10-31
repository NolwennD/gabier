# The diagnostics of a file can be followed as a stream

Opened: 30-10-2026 19:39
On hold: nice to have, not needed (an agent asks `diagnostics` again); taken up after the parity with the Python driver
(31-10-2026 refocus).

An agent can follow the output of a command line by line, and react to each line as it comes. jdtls already pushes its
diagnostics (`publishDiagnostics`, kept per file by `Diagnostics.publish`); the daemon relays them instead of waiting to
be asked. `gabier diagnostics --watch <file>` keeps running and prints, in the line format of `gabier diagnostics`, the
diagnostics of the file each time they change (jdtls always sends the whole list of a file: a clean file prints a line
saying so, so that an agent sees an error go away). It stops when the process is stopped. On the wire this is the first
message the daemon sends without being asked: JSON-RPC notifications (no `id`) on the connection that asked, after a
`watch` request.

Not here: telling jdtls that files changed on disk, which the adapter does not do yet (issue
`rename-after-the-files-changed-on-disk`); without it a real jdtls publishes again only when it sees a change itself.
This ticket is written and tested against the fake language server, which publishes what the test scripts.

Done when:
- after a `watch` request, the daemon writes one notification per publication for that file, in order, on the same
  connection; shown with the fake server scripted to publish an error, then a warning, then a clean file;
- a client that disconnects ends its subscription, and nothing else: another watcher, `status` and `stop` keep working,
  no thread or subscription is left (a test, a real socket in a temporary directory);
- a watcher that is too slow to read does not block the daemon or the other watchers (the choice is stated and tested);
- `gabier diagnostics --watch <file>` writes each event as soon as it comes (flushed per line), in text and with
  `--json`; it ends cleanly, no stack trace, on SIGTERM and when the daemon goes away; tested in `GabierTest` against
  `ScriptedDaemon`, and against the native binary in `bin/ci.d/40-native`;
- the README ("Command line") and the skill `add-daemon-method` say how a method answers more than once;
- `bin/ci` green, coverage 100%.

Depends on: 30-10-2026-1937-the-daemon-answers-diagnostics-of-a-file (the method, the type of a diagnostic and the line
format).
