# `gabier <command> --help` shows the help of the command, and the help lists every refusal

Opened: 31-10-2026 23:26
Depends on: 31-10-2026-2348-gabier-is-composed-of-capabilities.md (the arguments are read in one place, so the flag is handled once, in the dispatch).

Found in the review of `gabier-has-a-help`. `--help` is the first thing an agent types after a command, and it does not work:
`gabier diagnostics --help`, `diagnostics -h` and `organize-imports --help` take the flag as a file, start the daemon and ask
for `<cwd>/--help` (they end in a `language-server-file-*` refusal); `gabier rename --help` and `extract-variable --help` give the
generic usage error (`Gabier.java:72-75`). And the refusals that `gabier help <command>` lists are incomplete
(`Command.java:23-27, 54-60, 71-75`): `editing-stale-edit` is missing for `organize-imports` and `extract-variable` (the pages
`docs/commands/` name it), `language-server-file-missing` and `language-server-not-a-file` for `rename` and `extract-variable`,
`usage-error` for every command, `daemon-start-failed` and `daemon-connection-failed` for every command that talks to the daemon.

Done when:
- [ ] `gabier <command> --help` and `gabier <command> -h` print the help of that command, exit 0, start no daemon, for every
      command; `gabier help <command>` and the flag give the same text (a test per command).
- [ ] The refusals of each command in `Command` are those of its page in `docs/commands/`; a test fails when a refusal key a
      command can answer is missing from its help (find a way that does not copy the list).
- [ ] `bin/ci` green, coverage 100%.

## Resolution

Done in 4067d85. `gabier <command> --help` and `-h` (anywhere after the command) print the help of the command in the dispatch
(`Gabier.execute`), run no capability and start no daemon; `gabier help --help` now gives the help of `help` (it was a usage error).
The refusals of each command in `Command` are those of its page in `docs/commands/` (a `## Refusals` section on each page).

Checked (worker's `bin/ci`, run on the commit before the rebase over a commit of `.md` files only): format 8s, build 3s, test 142s
(cli 236 tests, architecture 19, JaCoCo 100%), native 146s, jdtls 312s, all passed. `HelpFlagTest`: every command, `--help`, `-h`,
`--json` before and after, same text as `gabier help <command>`, no daemon and no socket; red first (27 of 28). `CommandRefusalsTest`:
one run per command, compares the keys of the help with the `## Refusals` bullets of the page, no copied list (mutation: a bullet
removed from `extract-method.md` makes it fail).

Choices: the flag wins over the other words (`rename --token --help` prints help); the shared refusals live in `Command.Refusal`;
`status` and `stop` get no `daemon-start-failed` since they never start a daemon.

Review (once): no blocking finding. Opened with an `On hold:` line, in `01-11-2026-0905-help-lists-every-refusal-a-command-can-answer.md`:
keys of `EditingErrorKey` missing from four pages (`editing-write-failed`, ...); `CommandRefusalsTest` cannot see a key that is on
neither page nor help; `HelpFlagTest` lacks `rename --token --help` and `help rename --help`. Not followed (formatter churn on the
`STOP` and `HELP` constants of `Command`).

Tokens:
- worker: 135081 (subagent_tokens)
- reviewer: 65212 (subagent_tokens)
- supervisor: 14 calls, 7304 output, 21239 input + cache write (1600809 cache read), over the period of the ticket, the other work of the period included; exchanges with the user: 2 messages
