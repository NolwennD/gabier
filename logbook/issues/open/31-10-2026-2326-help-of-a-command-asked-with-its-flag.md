# `gabier <command> --help` shows the help of the command, and the help lists every refusal

Opened: 31-10-2026 23:26

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
