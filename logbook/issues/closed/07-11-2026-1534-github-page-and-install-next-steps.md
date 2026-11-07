# The GitHub page and the end of the install tell what gabier does and what to do next

Opened: 07-11-2026 15:34

Someone who searches GitHub for a Java refactoring tool for coding agents does not find gabier (no description, no topic,
no homepage), and someone who installs it is told nothing about what to do next. After this ticket, a visitor sees what
gabier does in one screen of the README, and an installer is told the first command and what to give an agent.

Done when:
- [ ] the README shows, right after its first paragraph, one real example: the command, its output and the effect on
      the files (a `rename --dry-run` or a `move`), produced by running the installed `gabier` on a small project, not
      written by hand; the text says what is not released yet (Linux x86-64 only, JDK 25, Python 3) before the install
      steps, in one place;
- [ ] `install.sh` (`scripts/release-install.sh.template`) ends with a short message: the version and the folders it
      installed, `gabier --version`, the first command to try, and the line to put in the instructions of an agent
      (`gabier help`, `gabier help --for="<task>"`); a test of the script checks the message; the mise route, which has
      no hook, is covered by the README;
- [ ] the report proposes the text of the GitHub description (one sentence) and the topics (five or six), for the
      supervisor to apply with `gh repo edit` once the user agrees; the worker does not touch GitHub;
- [ ] `bin/ci` passes.

## Resolution

Done in d31dbff (pull request 19, `bin-ci` green).

- README: a real example (`rename --dry-run --token`, its output, the diff) right after the first paragraph, produced by
  the installed `gabier` on a throwaway project (paths shortened); one paragraph says what is released so far (Linux
  x86-64, JDK 25, Python 3); a "First steps" section covers the mise route, which has no hook.
- `install.sh` (template): ends with the version, the folders, the first command and the line for an agent's
  instructions. New stage `bin/ci.d/35-release-install` runs the script on fake release files, red first on the old
  template, for the links folder on and off the `PATH`.
- Proposed for GitHub, not applied (the user agrees first): description "Java refactorings for coding agents: rename,
  move, extract and inline through a real language server (jdtls) instead of editing text."; topics `java`,
  `refactoring`, `coding-agents`, `language-server`, `jdtls`, `cli`. No homepage.
- `bin/ci` of the worker: format, build, test, release-install passed; native and jdtls skipped (no changed file reaches
  them).
- Review: no blocking finding. Suggestions: unquoted path in the message, more cases for the stage, leftover placeholder,
  `reachedBy`, one source for the agent line, all in `install-message-review-suggestions` (on hold); the wording of the
  `bin/ci` stages in `workflow.md` and the width of two lines fixed by the supervisor.
- jdtls rewrote the `.project` of the demo project (a `filteredResources` block): the same family as
  `the-daemon-does-not-write-into-nested-projects`, no new issue.

Tokens:
- worker: 119644
- reviewer: 73295
- supervisor: 17 calls, 10205 output, 33700 input + cache write (1747507 cache reads), over the period from the launch of
  the worker to the merge, other work included (the lot A worker in flight); exchanges with the user: 1 message
