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

## Resolution

Done in 20cdcfe.

- Reproduced first with the installed gabier: `gabier actions` on a method printed `Make Static -> gabier inline`.
  `OfferedAction.command()` now links the shared kind `refactor.inline` only to an action whose title starts with
  "Inline ". **Choice, reported by the review**: this puts a title of jdtls in the core, against the rule that the specifics
  of jdtls stay in the adapter (the Javadoc of the record says gabier does not read titles). The worker kept it to avoid a
  change of the protocol, and the adapter already holds the same predicate (`JdtlsLanguageServer`). A renamed or localized
  title loses the link, never a wrong one is made. Moving it to the adapter is `offered-action-title-leaves-the-core`
  (on hold).
- "Extract to local variable (replace all occurrences)" stays linked to `extract-variable`; the help of `actions`,
  `docs/commands/actions.md` and `docs/improve.md` say that the command replaces the selected expression only.
- Replay on the real jdtls (`ScenarioReplayTest`, via `:daemon:jdtlsProbe`): two scenarios,
  `code-actions-on-the-use-of-a-constant` (extract-variable, `.field`, `.constant`, `.function`, `refactor.inline`) and
  `code-actions-on-a-method-that-can-be-made-static` (Make Static, Inline Method). Tests: `OfferedActionTest`,
  `ListCodeActionsTest`. The lines of `Actions.java` and `CodeActionsMethod.java` are rewrapped to 120 columns.
- **Ceilings raised** in `JdtlsCouplingTest`, since each new scenario compares titles exactly: `MOST_EXACT_COMPARISONS`
  4 to 6, `MOST_ACTION_TITLES` 4 to 11 (10 without the repeated `EXTRACT_VARIABLE`, kept so that one range shows the four
  extractions; to drop in `offered-action-title-leaves-the-core`).
- The batch was reviewed once (no blocking finding); the suggestions are in `guide-ranking-review-suggestions`, `offered-action-title-leaves-the-core` and `main-test-wait-names-its-cause`, all on hold. `bin-ci` of pull request 20: green. The worker rebased over the code of the GitHub-page ticket and ran `bin/ci --all` again: format, build, test, release-install, native, jdtls all passed.

Tokens (one batch of three issues, shared by the three resolutions):
- worker: 221860
- reviewer: 87714
- supervisor: 53 calls, 42849 output, 102368 input + cache write (6262353 cache reads), over the period from the launch of
  the worker to the merge, the ticket of the GitHub page and the other work included; exchanges with the user: 3 messages
