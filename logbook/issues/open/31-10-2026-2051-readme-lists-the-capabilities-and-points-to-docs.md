# The README lists the capabilities and points to docs/

Opened: 31-10-2026 20:51

The README is 329 lines and grows with each command (the "Command line" and "The daemon" sections hold the usage, the
examples, the daemon methods and the refusals of every operation). An agent that reads it to learn what gabier can do
must go through all of it. After this ticket the README says what gabier is, how to set it up, and lists the
**capabilities** (one line each: the command and what it lets an agent do), each pointing to its page under `docs/`.

Done when:
- [ ] `README.md` keeps the setup and a list of the capabilities (`diagnostics`, `rename`, `organize-imports`,
      `extract-variable`, `status`, `stop`), one line each with a link; no usage detail, example or refusal list is left in
      it, and it is well under half its present length.
- [ ] Each capability has a page `docs/commands/<command>.md` (usage, example, answer in text and `--json`, refusals), and
      the daemon (starting on demand, installing by name, methods, socket) has `docs/daemon.md`, exit codes `docs/exit-codes.md`:
      what the README said is moved, none of it is lost (checked by a diff of the words removed from the README).
- [ ] The pointers follow: `scripts/install.sh:4` ("README, The daemon"), the open issues that name the README, and the
      Javadoc or tests that cite it, now name the new page.
- [ ] The skill `add-daemon-method` (step "Update the Command line section of the README", `SKILL.md:63`) says: a new
      command is a page under `docs/commands/` and a line in the README list; the rules that name the README as the place of
      the documentation (`grep -rn README .claude`) are updated the same way. The skill is applied once on a throwaway page to
      prove it works, then the page is removed.
- [ ] `bin/ci` green (no code is touched, so only format and build run).
