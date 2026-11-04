# The command named by gabier actions says what differs, and its kinds are replayed

Opened: 04-11-2026 03:34
On hold: findings of the review of `actions-names-the-gabier-command-that-applies-an-action`; they break no `Done when`
point.

- `docs/commands/actions.md` and the help of `actions`: "Extract to local variable (replace all occurrences)" is listed
  as `-> gabier extract-variable`, which replaces the selected expression only (`docs/commands/extract-variable.md`). An
  agent that wants every occurrence replaced is led to a command that does not do it.
- `OfferedActionTest` copies the table of `OfferedAction`, so it cannot fail on a wrong kind, and only
  `refactor.extract.variable` is replayed on the real jdtls (`CodeActionScenarios`). `.field`, `.function` and
  `.constant` were seen on the real jdtls by hand (04-11-2026); `refactor.inline` was not seen at all.
- Javadoc and help lines of `Actions.java` and `CodeActionsMethod.java` are 121 to 182 columns wide.

Done when:
- [ ] the docs and the help say that the action with "(replace all occurrences)" is not what the command does, or the
  action is not linked;
- [ ] a scenario replays on the real jdtls each kind of the table (`.field`, `.function`, `.constant`,
  `refactor.inline`), or the `## Resolution` says why one cannot; the lines are rewrapped to 120 columns; `bin/ci`
  passes.
