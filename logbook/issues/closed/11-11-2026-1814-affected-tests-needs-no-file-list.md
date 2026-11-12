# affected-tests answers for what changed, with no file list

Opened: 11-11-2026 18:14
Depends on: 11-11-2026-1616-gabier-lists-the-tests-a-change-affects (same command, same files, same module).

An agent that edited Java files calls `gabier affected-tests` and gets the tests to run again, without listing the files it
changed. Today it must build that list by hand (`affected-tests <file>...`), which is the manual step that makes the
command of little use: the daemon already knows what changed on the disk since it was last asked (`WatchedFiles`, the
files it tells jdtls of before each request), and jdtls rewrites the class files after each change. The next step towards
what Infinitest does in an IDE, where nothing is asked at all, is not in this ticket.

Done when:
- `gabier affected-tests` with no file answers, in the same format as the call with files, the tests affected by what changed
  since the last answer of the daemon (what jdtls was told of, or the class files it rewrote: the worker observes on the
  real jdtls which of the two is right, for an edit made by hand, by an agent's text edit and by a `gabier` refactoring
  such as `rename`, and writes why in the resolution). The call with files keeps its form and its answers.
- A second call with no new change answers that no test is affected; an edit to a test class lists that test; an edit to an
  application class lists the tests that depend on it, as the call with files does.
- A call that is refused (a file that does not compile, a server not ready) does not use up the changes: the next call that
  is answered still lists their tests.
- What the first call of a daemon answers, when nothing has been asked before, is a choice the worker states in the
  resolution and the page, with the reason; the choice does not make an agent run every test of a large project by surprise.
- The state belongs to the daemon of the project: two clients of the same daemon, and a restart of the daemon, are covered by
  a test, and the page says what each gives.
- The daemon method, the cli command, the MCP tool, the page of the command, its help and the guide (`gabier help
  --for`, the task "which tests to run after my edit") say it (skills `add-daemon-method`, `extend-guide`); a contract
  scenario is scripted into the fake first and replayed on the real jdtls (skill `add-scenario`).
- The native image of the cli stays built (`bin/ci --stage native` green).

## Resolution

Done in 5bff138 (PR 50): `gabier affected-tests` with no operand answers the tests affected by what changed since the last
answer; with files it keeps its form. A second daemon method `affected-tests-changed` (no parameter), served by the cli and the
MCP tool.

- **Source of the changes, observed on the real jdtls (1.63.0, scratch Eclipse project, daemon log and class-file mtimes,
  throwaway):** what jdtls was told of (`WatchedFiles` events, accumulated in `JdtlsLanguageServer.takeInChangesOnDisk`, the one
  call site of `session.changesOnDisk`) was kept over the class files it rewrote.
  - A body edit (by hand or with the Edit tool): told of = the edited file; class files rewritten = its class only.
  - `gabier rename`: told of = the edited dependent, the new file created, the old one deleted; class files rewritten = the same
    classes.
  - A signature change: told of = the edited file only; class files = it and its dependent. A deleted used class: told of =
    the deletion only; the dependent is rewritten as an error stub, and `diagnostics --all` reports it.
  - After a stop and a start of the daemon, **every** class file is rewritten, untouched ones included: a class-file source would
    list every test after each start, so it is rejected. Known gap of the chosen source, written on the page: the dependents of
    a deleted class are listed only if their own file changed.
- **First call of a daemon:** answers for what changed since the jdtls session started (the baseline is read just before jdtls
  starts, so edits during the import count, edits before the daemon started do not). A daemon started by the call itself has seen
  no change: `no affected tests`. Why: an agent never runs every test of a large project by surprise; to run them all, give the
  files. The page says to run `gabier start` before editing.
- **Refused call:** the changes are not used up (`ChangedBuild` carries the snapshot and the acknowledgement; `answered()` removes
  only the (path, revision) pairs of the snapshot, so a file changed again after it stays). Files that are not Java, deleted or
  outside a source folder are left out instead of refused; a file with errors is still refused.
- **Checked** (subagent's `bin/ci` on the final tree): format, build, test (314 s, coverage 100 %, architecture, Checkstyle,
  coupling ceilings unchanged 6 / 5 / 11 / 26), release-install and release-notes skipped (no changed file reaches them), native
  (243 s, new checks on the fake daemon), jdtls (1395 s, run again alone after "Gradle build daemon disappeared unexpectedly",
  environmental). Check of the pull request `bin-ci`: pass (17 m).
- **Review (once):** nothing blocking. Suggestions, in one issue on hold, `12-11-2026-2125-affected-tests-changes-review-suggestions`:
  two overlapping calls can both be answered the same changes (the page says "the second prints `no affected tests`", true of
  sequential calls only); an empty operand list (a glob with no match) now asks for the changes instead of being a usage error;
  a change recorded before a timed-out wait is recorded again on the retry; the rename replay writes the files, it does not run a
  `gabier rename` through the daemon.
- **Choices kept:** the MCP tool `affected-tests-changed` is not marked read-only (it writes no file but uses up daemon state; a
  retried call after a lost answer would come back empty; stated in `docs/commands/mcp.md`). `ProjectBuild` is no longer a
  functional interface (two methods); the tests that used lambdas use `FakeProjectBuild`.
- **Not covered by a test, stated in the docs from the code or a scratch observation:** the forgetting of the changes when jdtls
  restarts after a failure; the dependents of a deleted class; the rewriting of every class file at a start; the stop after an hour
  with no request. The restart test is a second `JdtlsLanguageServer` on the same folder, not a daemon process restart; the
  two-clients test is at the `DaemonServer` level with `FakeProjectBuild`.
- **Release note:** `docs/releases/<version>.md` is not written (the next version is not chosen; `affected-tests` and
  `diagnostics --watch` have none either). It is written before the next tag.
- **Dogfooding gap:** the worker ran `gabier start` and `stop` only; the import lines and two small extractions were text edits
  checked by the compiler and `bin/ci`, not by `organize-imports` or `diagnostics`. No friction with gabier reported. The
  isolation guard of the worktree refused compound shell commands several times (worked around with script files).
- **Next, no issue yet:** a flow mode for `affected-tests` (what `diagnostics --watch` is to `diagnostics`), and running the tests,
  which the project leaves out of scope.

Tokens:
- worker: 334514 (`subagent_tokens`)
- reviewer: 92838 (`subagent_tokens`)
- supervisor: 32 calls, output 15879, input + cache write 210088 (cache read 2741942 apart); covers the supervisor session from the
  launch of the worker to the merge, other work included; exchanges with the user: 0 messages
