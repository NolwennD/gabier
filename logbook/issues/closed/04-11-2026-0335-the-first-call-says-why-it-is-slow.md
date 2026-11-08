# The first call to the daemon says why it is slow

Opened: 04-11-2026 03:35

An agent that runs its first `gabier` command of a session waits 46 to 55 seconds and sees nothing, so it cannot tell a
hang from a wait. Met on 04-11-2026 with the installed gabier (`gabier actions` on `HelpText.java` 55:20 60:27: 46 s by
the supervisor, 55 s by the worker of `actions-names-the-gabier-command-that-applies-an-action`); the same call warm
takes 6 to 9 s, which is what `docs/commands/actions.md` announces (five seconds of quiet from jdtls). The wait before
the first answer is not in the docs. `04-11-2026-0122` measured 3 min 23 s on a repository with nested projects.

Done when:
- [x] the cause of the first wait is shown on the real jdtls (the start of jdtls, its import of the project, the wait
  for diagnostics), with the time of each part for this repository;
- [x] the agent is told while it waits (a line on the standard error, never on the standard output, and none when the
  answer is quick) and `docs/daemon.md` says what the first call costs; `bin/ci` passes.

## Resolution

Done in 87a51a4 (pull request 25, merged fast-forward, with `status-shows-the-indexing-of-jdtls`). The cause of the wait was
measured on the real jdtls with a throwaway test (removed) and is in the table "What the first call costs" of `docs/daemon.md`:
on this repository, about 6 s from the launch of jdtls to its first status, then 53 s of Gradle import (9 s with a warm Gradle
daemon), 0.1 s from ready to the list of projects, then 7 to 9 s of wait for diagnostics (five seconds of quiet); the first
`diagnostics` took 69 s (112 s under load; 28 to 32 s on two later cold runs). Nearly all of it is the Gradle import.

- `WaitNotice` (cli): after 3 s without an answer it writes on the standard error `gabier: waiting for jdtls (starting, Ns so
  far; …)` and repeats every 15 s while jdtls is starting; it reads `status` on a second connection; no line when the answer
  is quick, when jdtls is ready or when the daemon does not answer. The standard output holds only the answer.
- Checked: the worker's full `bin/ci` on the rebased tree (format, build, test, native, jdtls passed; release-install
  skipped) and `bin-ci` green on the pull request (run 42479553276, 11 min 49 s). Tests: `WaitingForJdtlsTest`,
  `WaitNoticeTest`, and one for the two behaviours together (a refusal after the notice under `--json` is the notice, then the
  problem object with its `help` member).
- Choices: the notice is plain text on stderr in `--json` mode too (documented in `docs/daemon.md`), not a JSON line. The
  delay of 3 s is a package-private seam (`Gabier.run(..., Duration)`); an environment variable was added then removed, so
  there is no new documented setting.
- Review (neutral, once, of the batch): nothing blocking. Suggestions in
  `08-11-2026-1115-start-wait-review-suggestions.md` (on hold): the `45%` prefix shown against the doc, the first line coming at
  about 18 s when the first poll finds no daemon, weak assertions in two tests. A test of `JdtlsSessionTest` that failed once
  under load: `08-11-2026-1115-jdtls-session-test-fails-under-load.md` (on hold).

Tokens: see `status-shows-the-indexing-of-jdtls` (one batch, one worker, one review, one supervisor period).
