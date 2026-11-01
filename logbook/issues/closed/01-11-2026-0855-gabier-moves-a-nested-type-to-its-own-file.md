# gabier moves a nested type to its own file

Opened: 01-11-2026 08:55

An agent makes a nested type a top-level type in a file of its own with `gabier move-type-to-file`, instead of cutting and
pasting text and fixing the imports by hand. `gabier move-type-to-file [--dry-run] <file> <line>:<column>` (1-based; the
innermost type that encloses the position) creates the file in the same package. The answer is the files changed and
created and the unified diff, text and `--json`; a refusal is a problem object, nothing written. Daemon method (skill
`add-daemon-method`) and cli command on the shape of `extract-method`. The tests of the cli are split with it
(`31-10-2026-2348-cli-tests-are-split-contracted-and-parameterised.md`).

**Care**: jdtls is configured, not fought. Request `java/move` with `moveKind: "moveTypeToNewFile"`
(`MoveHandler.moveTypeToNewFile`, clone at /home/nolwenn/dev/projects/eclipse.jdt.ls, read-only), added next to
`getRefactorEdit` in `JdtlsServer`. The new file comes back as a `CreateFile` only when the client declares resource
operations (`ChangeUtil.java:151`); gabier already declares them (`JdtlsCapabilities`) and `WorkspaceEdits` turns
`CreateFile` into `FileChange.CreateFile`. The resolution confirms that no setting is missing.

Done when:
- [x] A static nested class and an inner class that does not use the outer instance are moved on disk (file created,
      references and imports updated); `--dry-run` writes nothing. Shown on the fake and by contract scenarios replayed on
      the real jdtls (skill `add-scenario`). A position inside a method of the nested type moves that type (scenario).
- [x] An inner class that uses the outer instance gets a field and a constructor parameter of the outer type: the answer is
      replayed as a scenario. The help of the command (`gabier help move-type-to-file`, `gabier move-type-to-file --help`; a
      test checks it) and `docs/commands/move-type-to-file.md` say it: a `@Nested` test class cannot then be instantiated by
      JUnit. They say how to avoid it: first make the class independent of the outer instance, for example by having it
      extend the shared base class.
- [x] Refusals, each a scenario replayed on the real jdtls: a position outside any type, a position in a top-level type, a
      target file that already exists (nothing overwritten; the resolution says who refuses, jdtls or gabier), a file outside
      the project.
- [x] The page `docs/commands/move-type-to-file.md`, the line of the README list and the row of `docs/protocol.md` are added
      (skill `add-daemon-method`); `.claude/rules/dogfooding.md` names the command. `bin/ci` green, coverage 100%.

Depends on: 01-11-2026-0843-an-unknown-command-suggests-the-nearest.md (same files: `Command`, `Gabier`, `docs/commands/*`).

## Resolution

Commit d350c47. `gabier move-type-to-file [--dry-run] <file> <line>:<column>`: port method `JavaLanguageServer.moveTypeToFile`, use
case `MoveTypeToFile`, daemon method `MoveTypeToFileMethod`, cli command, `Request.MoveTypeToFile` / `Answer.Moved` and nine contract
scenarios (replayed on the real jdtls, applied files compiled with javac). The help gets an optional `notes` field (text and
`--json`, present only when set) that carries the warning on a `@Nested` test class. Docs: `docs/commands/move-type-to-file.md`,
README, `docs/protocol.md`, `docs/daemon.md`, `dogfooding.md`, skills `verify` and `add-daemon-method`; native check in `bin/ci.d/40-native`.

jdtls is configured, not fought: request `java/move` with `moveKind: "moveTypeToNewFile"` and a `CodeActionParams` with an empty range
at the position (`MoveHandler.move`). No `JdtlsSettings` change. The `CreateFile` comes back because `JdtlsCapabilities` already
declares the resource operations (`ClientPreferences.isResourceOperationSupported`, `ChangeUtil.java:151`); `WorkspaceEdits` already
turns it into `FileChange.CreateFile`. No capability is missing. The empty Javadoc on the generated field and constructor is written
by the refactoring although `java.codeGeneration.generateComments` is false; the new file has no final newline: both stated on the page.

Who refuses an existing target file: jdtls (`MoveInnerToTopRefactoring` returns FATAL before any edit exists; `java/move` answers
`edit=null` and an error message). gabier checks only at apply time (`editing-file-exists`). The three jdtls refusals (position outside
any type, top-level type, target file exists) map to one key, `language-server-not-movable`, because telling them apart means parsing
jdtls's text. Decision of the worker, accepted by the review as meeting the point.

Checked: worker's full `bin/ci` green on d350c47, rerun after an amend of the help example (format 8s, build 3s, test 126s with coverage
100% and Checkstyle, native 139s, jdtls 399s). Review (neutral, once): no blocking finding. Suggestions, all to be opened as tickets:
the replay of a refusal compares gabier's message, not jdtls's, so the docs' "who refuses" wording rests on no test; "nothing overwritten"
is asserted on the fake only, not in the replay; the refusal message gives no hint to look for a file of the same name.
Friction met by the worker, to be opened: `ChangeReport.modified()` also lists the created file; the unified diff of a created file has
two header blocks. Pre-existing Error Prone warnings (`InvalidParam` at `JdtlsLanguageServer.java:286-288`, `ImmutableEnumChecker` on
`Extraction`, `OperatorPrecedence` at `Main.java:74`) not opened. The worker wrote the adapter, daemon method and cli capability before
their tests (probing the real jdtls first); the use-case tests and scenarios came after.

Tokens:
- worker: 367083 (subagent_tokens, resumed once)
- reviewer: 70848 (subagent_tokens)
- supervisor: 10 calls, 8324 output, 24110 input + cache write (802494 cache read) over the supervisor session from the launch of the
  worker (08:36 UTC) to the close, other work included; exchanges with the user: 0 messages
