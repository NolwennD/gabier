# gabier explains its commands with help

Opened: 31-10-2026 22:59

An agent that does not know a command asks gabier instead of reading the documentation: `gabier help` lists the
capabilities (one line each), `gabier help <command>` gives the usage, an example and the refusals of that command, and
`gabier --help` and `-h` do the same as `gabier help`. Today `gabier --help` is a usage error (exit 2) that prints a long line.
The Python driver this replaces printed its help; the anti-goal of gabier is an agent that gets lost, so the commands must be
self-discoverable.

The text is written once, next to the command, not copied from `docs/commands/`: choose the place in the cli and say why. A
command that has no help text fails a test, so that the next command ships its help (the skill `add-daemon-method` says so).
The output is plain text on the standard output with exit code 0, and with `--json` the same content as an object; an unknown
command in `gabier help <command>` is a usage error naming the known commands.

Done when:
- [ ] `gabier help`, `gabier --help` and `gabier -h` list every capability (`diagnostics`, `rename`, `organize-imports`,
      `extract-variable`, `status`, `stop`, `help`) on the standard output, exit 0; `--json` gives the same list as an object.
- [ ] `gabier help <command>` gives the usage, one example and the refusals of the command; an unknown command is a usage error
      that lists the known ones. A test fails when a command of the cli has no help text.
- [ ] The wrong usage message (`UsageException`) points to `gabier help` instead of repeating the whole usage line.
- [ ] `docs/commands/help.md`, the README list and `docs/protocol.md` (no daemon method: `help` never talks to the daemon, say so)
      are updated; the skill `add-daemon-method` asks for the help text of a new command. `bin/ci` green, coverage 100%.

Coupled with `31-10-2026-2259-gabier-extracts-a-method.md` (both touch the usage text of `Gabier` and `UsageException`): they run in parallel on the
user's decision; the second one to be merged rebases over the first.
