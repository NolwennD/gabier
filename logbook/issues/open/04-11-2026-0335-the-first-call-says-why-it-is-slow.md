# The first call to the daemon says why it is slow

Opened: 04-11-2026 03:35

An agent that runs its first `gabier` command of a session waits 46 to 55 seconds and sees nothing, so it cannot tell a
hang from a wait. Met on 04-11-2026 with the installed gabier (`gabier actions` on `HelpText.java` 55:20 60:27: 46 s by
the supervisor, 55 s by the worker of `actions-names-the-gabier-command-that-applies-an-action`); the same call warm
takes 6 to 9 s, which is what `docs/commands/actions.md` announces (five seconds of quiet from jdtls). The wait before
the first answer is not in the docs. `04-11-2026-0122` measured 3 min 23 s on a repository with nested projects.

Done when:
- [ ] the cause of the first wait is shown on the real jdtls (the start of jdtls, its import of the project, the wait
  for diagnostics), with the time of each part for this repository;
- [ ] the agent is told while it waits (a line on the standard error, never on the standard output, and none when the
  answer is quick) and `docs/daemon.md` says what the first call costs; `bin/ci` passes.
