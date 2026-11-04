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
