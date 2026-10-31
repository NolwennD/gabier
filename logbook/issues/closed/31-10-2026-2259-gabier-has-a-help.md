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
- [x] `gabier help`, `gabier --help` and `gabier -h` list every capability (`diagnostics`, `rename`, `organize-imports`,
      `extract-variable`, `status`, `stop`, `help`) on the standard output, exit 0; `--json` gives the same list as an object.
- [x] `gabier help <command>` gives the usage, one example and the refusals of the command; an unknown command is a usage error
      that lists the known ones. A test fails when a command of the cli has no help text.
- [x] The wrong usage message (`UsageException`) points to `gabier help` instead of repeating the whole usage line.
- [x] `docs/commands/help.md`, the README list and `docs/protocol.md` (no daemon method: `help` never talks to the daemon, say so)
      are updated; the skill `add-daemon-method` asks for the help text of a new command. `bin/ci` green, coverage 100%.

Coupled with `31-10-2026-2259-gabier-extracts-a-method.md` (both touch the usage text of `Gabier` and `UsageException`): they run in parallel on the
user's decision; the second one to be merged rebases over the first.

## Resolution

Merged as `e80e954`. `gabier help`, `gabier --help` and `gabier -h` list the capabilities (exit 0, standard output);
`gabier help <command>` gives its usage, an example and its refusals; `--json` gives the same as an object; an unknown command
is a usage error that lists the known ones. `UsageException` no longer repeats the usage line and points to `gabier help`.
Behaviour change on purpose: `--help` used to be a usage error; the tests of the usage error now use `--bogus`.

Where the text lives: the enum `Command` of the cli (name, summary, usage, example, refusals), rendered by `HelpText`; `Gabier.execute`
switches on `Command`, so a command that is not handled does not compile, and `CommandTest.everyCommandHasAHelpText` fails for a
constant without its text. It is next to the dispatch rather than a copy of `docs/commands/`, so the compiler and a test hold it. `Json`
(module `protocol`) became public so that the cli can quote and the test can parse. The skill `add-daemon-method` (step 5) asks for the
`Command` constant; it was applied by adding `HELP`.

Proofs (worker's full `bin/ci`, green at the last run: format, build, test with coverage 100% and Checkstyle, native, jdtls; the first
run failed at format and at Checkstyle `MissingSwitchDefault`, fixed): `GabierTest$Help` (the three spellings, `--json`, one command,
every command, unknown command in text and JSON, `helpNeverStartsADaemonNorOpensTheSocket`), `CommandTest` (every command has a
summary, an usage, an example and a refusal), the native stage (the expected usage-error JSON, `--bogus`, a `help` check).
Not red first: the first failure of the new tests was a compile error.

Review (neutral reviewer, once): nothing blocking. Findings, opened: `<command> --help` takes the flag as a file or gives the generic
usage error, and the refusal lists of the help are incomplete: issue `31-10-2026-2326-help-of-a-command-asked-with-its-flag.md` (a real use, not on hold); the other
suggestions (`Json` API, a false line about `--json`, `help --dry-run`, a test name, a nested switch): issue `31-10-2026-2326-help-review-suggestions.md` (on hold).

Tokens:
- worker: 124520
- reviewer: 74885
- supervisor: 13 calls, 15443 output, 28590 input + cache write (cache reads 3215884), exchanges with the user: 0 messages.
  Measured from the launch of the worker (21:59 UTC) to the merge; it includes the first steps of the
  `extract-method` ticket, which ran in parallel.
