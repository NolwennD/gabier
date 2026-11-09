# The text of a result is a pure function of the result

Opened: 09-11-2026 07:55
Depends on: the-command-line-lives-in-the-primary-adapter-layer

Step 2 of the plan that prepares the cli for an MCP facade (structure only, behaviour identical). Today the text of a
daemon result is built inside the commands, next to the printing, and `ChangedFilesReply.text()` is private to a record
that holds an `Invocation`. A second primary adapter (MCP) must show the same text as the command line, so the rendering
must be callable from the result alone.

Done when:
- the text of each result kind that a command prints (`ChangedFiles`, `FileDiagnostics`, `FoundReferences`,
  `CodeActions`) is produced by a function of that result only: no `Invocation`, no `PrintStream`, no `Arguments`;
  neutralisation (`TerminalText`) and positions counted from 1 are part of it, as today;
- it lives in a layer that the architecture tests allow and that a second primary adapter may call (the worker chooses
  and says why in the resolution);
- the commands (`Diagnostics`, `References`, `Actions`, `ChangedFilesReply`, and any other that formats a result) only
  print what it returns; `--json` and the dry-run line are unchanged;
- one test per result kind calls the rendering directly (an empty result, a result with several entries, a hostile
  text neutralised); the existing cli tests pass unchanged apart from their package;
- `bin/ci` is green.

## Resolution

Commit ea2eff2 (PR #33). `ResultText` in `eu.gabier.cli.application` renders `ChangedFiles` (lines and warnings), `FileDiagnostics`, `FoundReferences` and `CodeActions` from the result alone, neutralised, positions from 1. `Diagnostics`, `References`, `Actions` and `ChangedFilesReply` only print it; `--json` and the dry-run line are untouched.

- Layer: `application`, the layer a second primary adapter may call and that sees the `protocol` types (the domain cannot). It passes the architecture tests only because of the previous issue.
- `lines(String file, FileDiagnostics)` takes the file as the user typed it, since `FileDiagnostics` does not carry it.
- `Status` and `Start` also format a result (`DaemonStatus`); not in scope, MCP does not expose them.
- Proof: `ResultTextTest`, per kind: an empty result, several entries, a hostile ESC neutralised; the existing cli tests pass unchanged apart from their package.
- `bin/ci` of the worker, run once on 97a62bd: format, build, test, native, jdtls passed; release-install skipped (no changed file reaches `install.sh`). Check `bin-ci` of PR #33 green (11m56s).
- Review (neutral, once): nothing blocking. Seven suggestions, grouped in the on-hold issue `operation-descriptor-review-suggestions` (command-line words in the descriptor notes, wire form of the array parameters, dead `required` flag, tests of the descriptor, liveness test of the new exemption); the fourth copy of the parameter names in each `Map.of` is the subject of the next issue of the plan.

Tokens:
- worker: 335,623 (cumulative figure of its last completion notification; one resume).
- reviewer: 102,227.
- supervisor: 25 calls, output 17,642, input + cache write 32,176 (cache reads 2,821,962), covering the supervisor session from the launch of the worker (06:55 UTC) to the merge, other work included; exchanges with the user: 0 messages.
