# gabier follows the problems of the project as they change

Opened: 30-10-2026 19:39
Reopened: 11-11-2026 18:41 (the hold is lifted: the user wants the agent to stop asking again and again)

An agent edits Java and sees the problems that appear or go away without asking again after each edit: one command that keeps
running and prints, line by line, what changed, which the agent reads as it comes. Today `diagnostics` and `diagnostics --all`
are calls, each paying a wait for jdtls. jdtls already pushes its diagnostics (`publishDiagnostics`) and compiles
incrementally; the daemon keeps what it published per file (`Diagnostics.published()`) and knows when a build is finished
(`Builds`), since the ticket `diagnostics-all-uses-the-jdtls-stream`. This ticket relays that flow to a client that asked for it,
and is the first time the daemon sends a message that is not the answer to a request.

Done when:
- `gabier diagnostics --watch` (with no file: the problems of the project, as `diagnostics --all`; with files: those files) keeps
  running. It first prints the problems as they are now, then, each time they change after a build of jdtls, the files whose
  list of problems changed; a file that became clean prints a line saying so, so that an agent sees an error go away. The line
  format is the one of `gabier diagnostics`; with `--json`, one object per line. An edit made on disk reaches the output with no
  other call.
- The daemon learns of the edits made on disk while a client watches: it has no watcher thread today (`WatchedFiles` compares the
  disk when a request comes). The worker observes on the real jdtls what that costs and what delay an edit takes to show up,
  chooses how the daemon follows the disk for the duration of a subscription, and states the choice and its delay in the
  resolution and the page.
- On the wire, the daemon sends notifications (JSON-RPC messages with no `id`) on the connection of the client that sent a
  `watch` request; the order of the publications is kept; `docs/protocol.md` and the skill `add-daemon-method` say how a method
  answers more than once.
- A client that disconnects ends its subscription and nothing else: another watcher, `status` and `stop` keep working, no thread
  or subscription is left (a test, a real socket in a temporary directory). A watcher that is too slow to read does not block
  the daemon or the other watchers (the choice is stated and tested).
- The command writes each event as soon as it comes (flushed per line), and ends cleanly, no stack trace, on SIGTERM and when
  the daemon goes away; tested against the scripted daemon of the cli tests and, for the native binary, in `bin/ci.d/40-native`.
- The MCP server: a tool answers once, so the worker states what it offers for a client that cannot read a stream, in the page
  and the guide (`gabier help --for`, the task "follow the errors while I edit"); nothing is promised that a tool cannot do.
- Fake first, then a contract scenario replayed on the real jdtls (skill `add-scenario`): an error is introduced in a file, shows
  up; it is fixed, and the file is reported clean.
- The native image of the cli stays built (`bin/ci --stage native` green).

Not here: running tests (the notification of a failed test as a problem is a later ticket). The on-hold issue
`11-11-2026-1439-diagnostics-all-stream-review-suggestions` touches the same code (`Builds`, `WatchedFiles`): it stays as it is.

Depends on: 30-10-2026-1937-the-daemon-answers-diagnostics-of-a-file and 11-11-2026-1259-diagnostics-all-uses-the-jdtls-stream
(both closed).
