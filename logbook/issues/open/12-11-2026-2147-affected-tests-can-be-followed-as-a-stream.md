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
