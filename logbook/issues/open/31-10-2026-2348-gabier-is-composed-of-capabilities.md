# `Gabier` is composed of capabilities (structure only)

Opened: 31-10-2026 23:48

`Gabier.java` holds the code of every command: `rename`, `organizeImports`, `extractVariable`, `extractMethod`... each one repeats the
same steps (the words after the command, `--dry-run`, the operands without the options, the arity check, the absolute path,
the positions from 1, the call to the daemon, `ChangedFiles.of`, the printing). Each new command adds a method to the file.
After this ticket a command is a class of its own, and adding one is a class, a constant of `Command` and one `case`.

Shape aimed at (the worker confirms it against the code and chooses the names): a package-private interface `Capability`
(`Command command()`, `void run(Invocation invocation)`); a record `Invocation` (the arguments, `json`, the output, the access to
the daemon); a value type `Arguments` (the words after the command, `dryRun()`, the operands without the options, `file(i)` as an
absolute path, `position(...)` for `line:column` and for two numbers, the one usage error); one class per capability
(`Diagnostics`, `Rename`, `OrganizeImports`, `ExtractVariable`, `ExtractMethod`, `Status`, `Stop`, `Help`); the part the commands that return
changed files share (daemon call, `ChangedFiles.of`, diff, dry run) written once by composition (a `ChangedFilesReply`), not by
inheritance; `Capabilities.of(Command)` as an exhaustive `switch` (a constant of `Command` without a capability does not compile).
`Command` keeps the name and the help text, unchanged. `DaemonMethod` (module `daemon`) is the model of the pattern.

Done when:
- [ ] The behaviour is identical: `GabierTest`, `CommandTest`, `DaemonClientTest` and `DaemonLauncherTest` pass **without any
      change** (`git diff` on them is empty), coverage stays at 100%.
- [ ] `Gabier.java` holds no command: under 100 lines.
- [ ] The rules for reading the arguments live in `Arguments`, with a unit `ArgumentsTest` (no socket) that is parameterised:
      `--dry-run` first, in the middle, last; an operand missing or too many; a position `0`, `-1`, `x`, `1:2:3`.
- [ ] The skill `add-daemon-method` (step 5) describes the new way to add a command and is applied once on a throwaway
      capability to prove it works, then removed (a mechanism meant to grow ships its skill).
- [ ] `bin/ci` green.

Depends on: 31-10-2026-2259-gabier-extracts-a-method (same file, `Gabier.java`).
