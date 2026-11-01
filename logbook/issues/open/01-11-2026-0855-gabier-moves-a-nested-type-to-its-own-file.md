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
- [ ] A static nested class and an inner class that does not use the outer instance are moved on disk (file created,
      references and imports updated); `--dry-run` writes nothing. Shown on the fake and by contract scenarios replayed on
      the real jdtls (skill `add-scenario`). A position inside a method of the nested type moves that type (scenario).
- [ ] An inner class that uses the outer instance gets a field and a constructor parameter of the outer type: the answer is
      replayed as a scenario. The help of the command (`gabier help move-type-to-file`, `gabier move-type-to-file --help`; a
      test checks it) and `docs/commands/move-type-to-file.md` say it: a `@Nested` test class cannot then be instantiated by
      JUnit. They say how to avoid it: first make the class independent of the outer instance, for example by having it
      extend the shared base class.
- [ ] Refusals, each a scenario replayed on the real jdtls: a position outside any type, a position in a top-level type, a
      target file that already exists (nothing overwritten; the resolution says who refuses, jdtls or gabier), a file outside
      the project.
- [ ] The page `docs/commands/move-type-to-file.md`, the line of the README list and the row of `docs/protocol.md` are added
      (skill `add-daemon-method`); `.claude/rules/dogfooding.md` names the command. `bin/ci` green, coverage 100%.

Depends on: 01-11-2026-0843-an-unknown-command-suggests-the-nearest.md (same files: `Command`, `Gabier`, `docs/commands/*`).
