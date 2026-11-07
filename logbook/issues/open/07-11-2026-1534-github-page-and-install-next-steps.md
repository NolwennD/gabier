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
