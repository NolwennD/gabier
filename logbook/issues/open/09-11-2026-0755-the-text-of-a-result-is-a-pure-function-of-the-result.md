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
