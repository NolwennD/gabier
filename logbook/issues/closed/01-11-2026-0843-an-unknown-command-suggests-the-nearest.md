# An unknown command suggests the nearest one

Opened: 01-11-2026 08:43
Depends on: 31-10-2026-2326-help-of-a-command-asked-with-its-flag.md (same files: the dispatch in `Gabier` / `Standalone` and the usage error).

Found while weighing picocli for the cli (no dependency taken, the idea kept). An agent that types `organise-imports` or
`extract-varible` gets the generic usage error and has to read the list of commands. Once this is closed, the refusal itself
names the command it meant, and the agent corrects the typo in one step.

Done when:
- [x] `gabier organise-imports x.java`, and a command with one letter changed, added, removed or two letters swapped, is refused
      with `usage-error` and the problem names the right command (`organize-imports`), in text and with `--json` (a field of
      the problem, not only the prose).
- [x] A word far from every command (`gabier frobnicate`) gives the usage error as today, without a suggestion.
- [x] The candidates are the names of `Command`, no list copied; a parameterised test covers each kind of typo.
- [x] No daemon is started and the exit code is unchanged.
- [x] `docs/commands/help.md` and the help of the usage error say it; `bin/ci` green, coverage 100%.

## Resolution

Commit b88a8a2. `Command.nearestTo` (first command of `Command.values()` at optimal-string-alignment distance 1, computed by the new
`EditDistance`) feeds `UsageException.unknownCommandTypo`, raised by `Standalone.run` when the first word is not a command; the
problem keeps the `usage-error` key and adds a `suggestion` parameter. A word at distance 2 or more gives the previous message.
`docs/commands/help.md` and `docs/cli.md` say it. No daemon is started and the exit code stays 2.

Checked: worker's full `bin/ci` green on the commit (format 7s, build 29s, test 142s with coverage 100%, native 134s, jdtls 304s).
Review (neutral, once): nothing to report.

Left open: `gabier help organise-imports` (the `Help` path) still lists the known commands without a suggestion; ties at distance 1
resolve to the first command in enum order (no two current commands are that close). Neither is in the ticket's scope; no issue
opened, no real use yet.

Tokens:
- worker: 104741 (subagent_tokens)
- reviewer: 51369 (subagent_tokens)
- supervisor: 11 calls, 4430 output, 21190 input + cache write (712362 cache read) over the supervisor session from the launch of the
  worker (08:21 UTC) to the close, other work included; exchanges with the user: 1 messages
