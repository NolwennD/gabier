# affected-tests can be followed as a stream

Opened: 12-11-2026 21:47

An agent (or a person in a terminal) starts `gabier affected-tests --watch` once and sees the tests to run again after each
edit, without asking again: the Infinitest behaviour with no IDE and no build tool. Today it must call `affected-tests`
after every edit. `gabier diagnostics --watch` already does this for the problems of the project (`Watching`, `ProblemFeed`,
`FolderWatch`, the daemon methods `watch` and `watch-all`): this ticket does the same for the tests, on the changes the
command with no file already keeps (ticket 11-11-2026-1814). Running the tests stays out of scope.

Done when:
- `gabier affected-tests --watch` does not end by itself: after each change on disk it prints the tests affected, in the
  format of the call with no file, text and `--json`, and prints nothing when no test is affected; one answer for a series
  of saves that come close together, and a test covers it. It ends as `diagnostics --watch` ends (the cli stopped, the
  daemon stopped, the connection lost), with the same refusals and exit codes.
- It reuses the stream of `diagnostics --watch` (the one watcher of the files of the project, the one read), not a second
  watcher: the worker names what is shared and what is new in the resolution.
- A change that does not compile is not answered as "no test affected": the stream says why, in the shape of the other
  refusals, keeps the change, and lists its tests once the code compiles; a test covers it.
- A watcher and a call with no file share the changes of the daemon: the page says which of the two uses them up, and what
  two watchers and a watcher with a call give; a test with two clients covers each.
- The daemon method, the cli command and its usage errors (a file, `--dry-run`), the page of the command, its help, the guide
  (`gabier help --for`, the task "follow the tests to run while I edit") and `docs/daemon.md` say it (skills
  `add-daemon-method`, `extend-guide`); whether `gabier mcp` serves it is decided as for `watch` and `watch-all`, and written
  in `docs/commands/mcp.md`; a contract scenario is scripted into the fake first and replayed on the real jdtls (skill
  `add-scenario`).
- The native image of the cli stays built (`bin/ci --stage native` green).

## Resolution

Done in 08c6545 (PR 52): `gabier affected-tests --watch` does not end by itself and prints the tests to run again after each
change on disk; daemon method `watch-affected-tests`, with the cli command and the page, the help, the guide entry
(`docs/improve.md`, "Following the tests as the files change") and the native check
(`DiagnosticsChecks.watchingTheTestsWithTheFakeServer`, called from `Session.check`; nothing added to `bin/ci.d/40-native`).

- **Shared with `diagnostics --watch`, and new:** shared are the thread and the one watcher of the files (`FolderWatch`,
  `DiskChanges`), `Outbox` and `Peer`, the `notice` and `ended` notifications and the end of the stream. `ProblemFeed` became
  `ProjectFeed` (with `gabier rename`) and serves both kinds of client; a lone `diagnostics --watch` never reads the tests and so
  never uses the changes up. New: `AffectedTestsUpdate`, `FollowedTests`, the `Followers` of `ProjectFeed`, the notifications
  `affected-tests` and `refusal`, `Streaming` in the cli, a generic `Peer.listen` with a `merge` (a slow client loses no test;
  the problems keep "latest state wins"). The first cycle of the thread reads no test: the request already answered the changes
  so far.
- **Choices, and what they replaced:**
  - Whoever answers first, a watcher or a call of `affected-tests-changed`, uses the changes up, and two watchers get the same
    tests. A cursor per watcher would have changed the core port `ChangedBuild`; it was left out. Written in the docs.
  - `watch-affected-tests` is not an MCP tool (`NOT_TOOLS`): a tool answers once. An agent over MCP calls
    `affected-tests-changed` after each edit (`docs/commands/mcp.md`).
  - A refusal at the start answers `{"tests":[]}` and the refusal goes to the listener, so the stream goes on; a failure that
    is not a refusal of the testing context (jdtls not ready, jdtls dead) refuses the request.
- **Observed on the real jdtls before scripting:** the first replay told nothing because the edit came before the disk watch
  existed; the replay waits for `watchDisk()` to return before it edits. The feed was unchanged.
- **Checked** (worker's `bin/ci` on 7da61f7, rebased without change of code on 08c6545 over `.md` commits only): format, build,
  test (core 927, daemon 1269, architecture 22; coverage 100 %, Checkstyle), native passed; release-install and release-notes
  skipped (no changed file reaches them); jdtls died once with "Gradle build daemon disappeared unexpectedly" in
  `ExtractedMethodSymbolsTest` (untouched by the ticket) and passed alone with `--stage jdtls` (990 s), `AffectedTestsReplayTest`
  green in both runs. Coupling ceilings unchanged (6 / 5 / 11 / 26). Check of the pull request `bin-ci`: pass (15 m 23 s).
- **Review (once):** nothing blocking. Suggestions in `13-11-2026-0001-the-tests-stream-loses-no-change-in-its-races` (on hold): a
  refused `watch-affected-tests` uses up the changes, an older refusal can follow the tests of code that compiles by then, a
  watcher joining during a read can read at the same time as the feed, and two `@SuppressWarnings("unchecked")` of the test peers
  carry no reason.
- **Not covered by a test, stated in the docs:** an overlapping call and watcher read can print duplicates but lose none
  (`answered()` removes the pairs of its snapshot); the 100 ms debounce is asserted through `FolderWatch.DEBOUNCE` and
  `FolderWatchTest`, the feed-level test checks one read for three saves.
- **Frictions with gabier, reported by the worker:** `gabier help --for="rename a class X to Y"` answered "nothing matches"
  though `gabier rename` does it; `gabier diagnostics` reports "not accessible" on tests that use the testFixtures of another
  module; no command adds a type parameter or changes a signature; a few Java edits were text edits or small scripts, which the
  dogfooding rule discourages. The first two are issues of their own; the third is not (no command exists to ask for).
- **Unrelated finding:** `./gradlew :daemon:compileJava` prints the warning `OperatorPrecedence` at
  `modules/daemon/src/main/java/eu/gabier/daemon/Main.java:84`; not fixed here, no issue opened.
- **Process note:** the worker was stopped by the supervisor once by mistake (the user had not asked for it) and resumed; its
  work was kept in the worktree, and it was paused a second time, at the user's request, so that `bin/ci.d/40-native` could be
  split (5d8ae52) before it added its native check, then rebased.

Tokens:
- worker: 447780 (`subagent_tokens`: 280069 at its pause, 167711 at its report; the notification of the first stop gave none)
- reviewer: 80940 (`subagent_tokens`)
- supervisor: 186 calls, output 197580, input + cache write 320169 (cache read 36079648 apart); covers the supervisor session
  from the launch of the worker to the merge, other work included (the split of the native stage, the documentation and
  package-structure discussions and the tickets opened from them); exchanges with the user: 17 messages
