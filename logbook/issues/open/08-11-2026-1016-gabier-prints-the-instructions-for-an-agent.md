# gabier prints the instructions to give a coding agent

Opened: 08-11-2026 10:16

For 0.4.0 (discoverability). The maintainers of a Java project, or the agent itself, run `gabier help agents` and get the
few lines to paste in `AGENTS.md`/`CLAUDE.md`: use `gabier help --for="<task>"` before editing Java by hand, the commands
that replace a text edit, `--dry-run` first, `gabier start` at the start of a session. Today the end of the install prints
one line; nothing says more.

Done when:
- [ ] `gabier help agents` prints a short text (about 15 lines, English, plain Markdown) written to be pasted as it is; it
      is a resource of the help, kept with the guide, not a second copy of `docs/`;
- [ ] a test fails when a command it cites does not exist or when it cites a command the help does not list; the text
      follows `gabier start` once that ticket is merged;
- [ ] `gabier help`, `docs/commands/help.md`, the README ("First steps") and the end message of the install point to it;
      `bin/ci` passes.

Depends on: gabier-start-warms-up-the-daemon (the text cites it).
