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
