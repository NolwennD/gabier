# gabier actions names the gabier command that applies an action

Opened: 04-11-2026 02:15
Depends on: 04-11-2026-0121-cleanup-says-how-to-change-one-place-only (same docs, same help)

An agent that lists what jdtls offers on a range learns which gabier command does it better. `gabier actions` answers
"Extract to local variable", and `apply-action` applies it with the name jdtls guesses (`String string = ...`), while
`gabier extract-variable` takes the name asked for. Met on 04-11-2026 on `HelpText.java` 55:20 60:27, which offers four
extractions, a parameter introduction and three concatenation rewrites; nothing says what each one is for.

Done when:
- [ ] the action list names the gabier command for the kinds that have one, by the kind of the action and not by its
  title (`refactor.extract.variable` → `extract-variable <name>`, `.field` → `extract-field`, `.function` →
  `extract-method`, `.constant` → `extract-constant`, `refactor.inline` → `inline`); in text a reference on the line,
  with `--json` a `command` field; an action with no such command is listed as before. The title stays the server's
  text.
- [ ] `gabier actions modules/cli/src/main/java/eu/gabier/cli/HelpText.java 55:20 60:27` shows `extract-variable` next
  to "Extract to local variable"; `gabier help actions`, `docs/commands/actions.md` and `bin/ci` agree.

## Resolution

Done in `913a54a` (pull request 14).

- `OfferedAction.command()` (core domain) reads the kind, never the title, and gives the command:
  `refactor.extract.variable` → `extract-variable`, `.field` → `extract-field`, `.function` → `extract-method`,
  `.constant` → `extract-constant`, `refactor.inline` → `inline`; a fix, a quick assist, `refactor.introduce.parameter`
  and `refactor.change.signature` have none. The protocol's `CodeActions.Action` carries it (`command`, written to
  `--json` only when the action has one; the two-argument constructor stays), `CodeActionsMethod` fills it, and the text
  line is `<n> <kind> <title> -> gabier <command>`. Tests: `OfferedActionTest` (the kind is read, not the title),
  `ListCodeActionsTest`, `CodeActionsTest`, `CodeActionsMethodTest`, `ListingActionsTest` (text and `--json`).
- On the real jdtls, `actions HelpText.java 55:20 60:27` shows `-> gabier extract-variable` next to both "Extract to
  local variable" actions, `extract-field` and `extract-method` next to theirs; the help of `actions` and
  `docs/commands/actions.md` (a kind-to-command table) agree.
- `bin/ci` of the worker: format, build, test, native and jdtls all passed. The check `bin-ci` of the pull request
  passed (12 min).
- **A ceiling goes up** (`workflow.md`, point 4 of the coupling): `MOST_ACTION_TITLES` of `JdtlsCouplingTest` goes from
  3 to 4, because `CODE_ACTIONS_ON_A_CONCATENATION` now also expects the exact title "Extract to local variable", which
  proves the kind the link is made from (`ScenariosTest` counts 1,1,1,0 → 1,2,1,0). The scenario was replayed on the
  real jdtls.
- Review (neutral, once): nothing blocking. Suggestions: the wrong kind in `extract-method.md` (fixed by the supervisor
  in `5287d9e`: jdtls says `refactor.extract.function`); the link of "Extract to local variable (replace all
  occurrences)" to a command that replaces one expression only, the missing replay of `.field`, `.function`, `.constant`
  and `refactor.inline`, and the lines over 120 columns, opened on hold as `actions-command-link-review-suggestions`;
  the usage hint in the text line is a matter of taste, no issue.
- Friction met: the first `gabier actions` of a session took 46 to 55 s with nothing said; opened as
  `the-first-call-says-why-it-is-slow`.
- The worker did not use `gabier` for the Java edits (small text edits), which `dogfooding.md` allows only for what
  gabier cannot do; no friction to report from it.

Tokens:
- worker: 135532 (`subagent_tokens`)
- reviewer: 68790 (`subagent_tokens`)
- supervisor: 29 calls, 26912 output, 44202 input + cache writes (cache reads: 8081306), from the launch of the worker
  (04-11-2026 03:07) to the closing, with the other work of the session (the review suggestions, the friction issue);
  exchanges with the user: 0 messages
