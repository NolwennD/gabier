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

## Resolution

Done in 2e777ad (PR 49): `gabier diagnostics --watch` (daemon methods `watch` for files and `watch-all` for the project, with
`--all`), the first time the daemon sends messages that are not the answer to a request (JSON-RPC notifications on the
connection of the client).

- **What it does:** prints the problems as they are now, then the files whose list of problems changed after each edit on disk,
  and `<file>: no problems` for a file that became clean; `--json` gives one object per line. One reader serves every client:
  it starts with the first watcher and stops, giving the disk back, with the last, so nothing runs when nobody watches. The
  cli ends on SIGTERM (status 143) and, when the daemon goes away, with `daemon-connection-failed` (code 1). No MCP tool: a
  tool answers once, and the docs say so.
- **How the daemon learns of the edits (the choice of the ticket), and why:**
  - jdtls does not look at the disk: on each `workspace/didChangeWatchedFiles` event it refreshes the resource
    (`WorkspaceEventsHandler.handleFileEvent`), and nothing in jdtls sets the workspace preference `refresh.enabled`; read in
    the jdtls source and confirmed by a probe. Setting that preference in jdtls's prefs file works but is slow (2 to 8 s on a
    small project, 7 to 23 s on this checkout). So the client, the daemon, watches.
  - Decided by the user after measurements on the real jdtls: `WatchService` (inotify) on the folders of the files jdtls asked to
    watch (`WatchedFiles`), with an automatic fallback to a scan every second and a `gabier: notice:` when events are lost or
    watches run out. The measurements (probe, not a guarantee): registration 24 ms (14 files) and 205 ms (2578 files); a change
    seen 0.1 to 1.4 s after the write; an error shown 1.1 to 1.5 s after a write on a small project and 10.8 to 15.8 s on a copy
    of this checkout, where jdtls takes 11 to 15 s to read; idle CPU zero; the scan fallback costs 263 to 726 ms per pass on
    2578 files and 0.7 to 1.3 s on 43,345, so 25 to 100 % of a core at 1 s; no read loop (0 reads when idle and after an edit).
    The source adds about a second at most to the delay, jdtls the rest.
  - Not retained: hooks of an agent (not portable from one agent to another, decision of the user), and a scan only (its cost
    grows with the number of files). Infinitest does a timestamp scan of the class folders triggered by the build-finished event
    of the IDE; ours scans sources, to tell jdtls what changed.
- **Checked:** `bin/ci` complete on the final tree, green: format, build, test (cli 1644, core 914, daemon 1213, protocol 436,
  shared 236, architecture 22; coverage 100 %, Checkstyle), native (with the new `watchingWithTheFakeServer`), jdtls (with
  the whole-project replay); release-install and release-notes skipped, no changed file reaches them. The coupling ceilings did
  not rise (6 / 5 / 11 / 26). Two full runs failed on a gate earlier and were fixed (a Checkstyle declaration order, a coverage
  gap in `RpcMessage`). Check of the pull request `bin-ci`: pass (13 m 38 s).
- **Mutation checks by the worker:** on core, `FolderWatch`, `ProblemFeed`, `Outbox`, `Watching`, `Connection`, `RequestHandler`,
  `WatchMethod`, `WatchedFiles`, `NioFolderEvents`, `JdtlsSession`, `JdtlsLanguageServer`, `DaemonServer`, protocol and cli:
  each killed by a test, but a redundant `return`, an interrupt that shortens a stop by at most 250 ms, a predicate that is only
  an optimisation, and an overflow the real `WatchService` cannot produce on demand. Not checked by mutation: the tests of the
  last round (`OutboxTest`, `Connection`, `WatchMethods`, `FolderWatch`, `ProblemFeed`), added for coverage gaps. The red
  phase of the core tests was not observed when they were first written, and no deliberately failing replay on the real jdtls
  was run (16 minutes each).
- **Review:** no blocking finding. Seven suggestions (a watcher arriving during a read is offered an older state, a second pump
  when a watcher arrives as the feed ends, a folder moved out of the project not read again, every call of the daemon waiting
  behind a read of the feed since the language server is `synchronized`, the first notification of `watch <file>` perhaps
  spurious, untested option combinations, the debounce that is no bound while events keep coming) are in one issue on hold,
  `12-11-2026-0026-diagnostics-watch-review-suggestions`. The likeliest to matter in use is the wait of the other calls behind a
  read.
- **Seen by the worker, no issue opened:** the Error Prone warnings `OperatorPrecedence` at `modules/daemon/.../Main.java:84` and
  `StringSplitter` in `ToolWords.java`, already on `main`; `ConnectionFollowingTest` flaky under load (timeout raised from 5 s
  to 30 s); the test stage takes 8 to 13 minutes when the machine is loaded.
- **Next:** a note `docs/releases/<version>.md` is needed before the next tag (new option `diagnostics --watch`, new daemon
  methods `watch` and `watch-all`, new `affected-tests` command already noted). Running the tests as the files change, with
  their failures shown as problems on the stream, is not in this ticket.

Tokens:
- worker: 154093 (`subagent_tokens` of its last notification; the agent was resumed several times)
- reviewer: 141569 (`subagent_tokens`)
- supervisor: 74 calls, output 66554, input + cache write 976843 (cache read 21799910 apart); covers the supervisor session
  from the launch of the worker to the merge, other work included (the choice of the disk source, the exploration of the
  jdtls and Infinitest clones); exchanges with the user: 13 messages
