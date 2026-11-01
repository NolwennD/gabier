# An unknown command suggests the nearest one

Opened: 01-11-2026 08:43
Depends on: 31-10-2026-2326-help-of-a-command-asked-with-its-flag.md (same files: the dispatch in `Gabier` / `Standalone` and the usage error).

Found while weighing picocli for the cli (no dependency taken, the idea kept). An agent that types `organise-imports` or
`extract-varible` gets the generic usage error and has to read the list of commands. Once this is closed, the refusal itself
names the command it meant, and the agent corrects the typo in one step.

Done when:
- [ ] `gabier organise-imports x.java`, and a command with one letter changed, added, removed or two letters swapped, is refused
      with `usage-error` and the problem names the right command (`organize-imports`), in text and with `--json` (a field of
      the problem, not only the prose).
- [ ] A word far from every command (`gabier frobnicate`) gives the usage error as today, without a suggestion.
- [ ] The candidates are the names of `Command`, no list copied; a parameterised test covers each kind of typo.
- [ ] No daemon is started and the exit code is unchanged.
- [ ] `docs/commands/help.md` and the help of the usage error say it; `bin/ci` green, coverage 100%.
