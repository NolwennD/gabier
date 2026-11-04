# The command named by gabier actions says what differs, and its kinds are replayed

Opened: 04-11-2026 03:34

- `docs/commands/actions.md` and the help of `actions`: "Extract to local variable (replace all occurrences)" is listed
  as `-> gabier extract-variable`, which replaces the selected expression only (`docs/commands/extract-variable.md`). An
  agent that wants every occurrence replaced is led to a command that does not do it.
- `OfferedActionTest` copies the table of `OfferedAction`, so it cannot fail on a wrong kind, and only
  `refactor.extract.variable` is replayed on the real jdtls (`CodeActionScenarios`). `.field`, `.function` and
  `.constant` were seen on the real jdtls by hand (04-11-2026); `refactor.inline` was not seen at all.
- Reported by the worker of `a-guide-says-which-problem-each-improvement-solves` (04-11-2026): the kind
  `refactor.inline` is shared, so "Make Static" is listed as `-> gabier inline`, which is wrong: `inline` replaces a
  variable, a method or a constant by its value. To reproduce first, on a method that can be made static. This is a real
  use, so the issue is no longer on hold.
- Javadoc and help lines of `Actions.java` and `CodeActionsMethod.java` are 121 to 182 columns wide.

Done when:
- [ ] red first: `gabier actions` on a method that can be made static does not name `gabier inline`; the link is made
  only for the actions that `inline` really does (by kind and by what the kind covers), a test per shared kind;
- [ ] the docs and the help say that the action with "(replace all occurrences)" is not what the command does, or the
  action is not linked;
- [ ] a scenario replays on the real jdtls each kind of the table (`.field`, `.function`, `.constant`,
  `refactor.inline`), or the `## Resolution` says why one cannot; the lines are rewrapped to 120 columns; `bin/ci`
  passes.
