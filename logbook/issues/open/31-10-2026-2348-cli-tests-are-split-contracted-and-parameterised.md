# The tests of the cli are split by capability, share a contract and are parameterised (tests only)

Opened: 31-10-2026 23:48

`GabierTest.java` is 1800 lines and over 120 tests, with no parameterised test in the module. The commands that return changed
files (`rename`, `organize-imports`, `extract-variable`, `extract-method`) each repeat about ten identical tests (absolute path, `--dry-run`
anywhere, list then diff, terminal escapes, `--json`, daemon refusal, invalid answer); the text refusal is written six times;
`socket()` and `runWithSocket()` are copied in seven nested classes. After this ticket no test class is long, and a new command
that returns changed files inherits its ten tests in about twenty lines.

Shape aimed at: a base `CliTest` (or an extension) with `@TempDir`, `out()`, `err()`, `run(...)`, `socket()`, `runWithSocket(...)`, a factory of
`ScriptedDaemon`; `CliFixtures` for the shared `Problem` and `ChangedFiles` samples; one test file per capability (`DiagnosticsTest`,
`StatusAndStopTest`, `RenamingTest`, `OrganizingImportsTest`, `ExtractingAVariableTest`, `ExtractingAMethodTest`, `HelpTest`, `StartingTheDaemonTest`,
`WithoutADaemonTest`) and a `GabierTest` of the dispatch under 200 lines; a test interface `ChangesCommandContract` with default `@Test`
methods, implemented by a `@Nested` class per command that gives only its command line and its expected request;
`@ParameterizedTest` + `@MethodSource` for the refusals and invalid answers common to the commands that talk to the daemon and for
the usage-error loops, `@EnumSource(Command.class)` for the help of each command. AssertJ only; style of reference:
`RequestHandlerTest.java:258` in the module `daemon`.

Done when:
- [ ] No test lost or weakened: the resolution holds the table "old test -> new test or contract" for every test of `GabierTest`, and
      coverage stays at 100%. A test that cannot fail is a reason to stop and report.
- [ ] No test class of the module `cli` over 400 lines, no copy of `socket()` or `runWithSocket()` left.
- [ ] Adding a command that returns changed files is one `@Nested` class of about twenty lines (shown on the last command added).
- [ ] The duplication measured by `ripwire modules/cli --clones` (`dup_pct=` in the `<clones>` line) falls from 39.2% (01-11-2026,
      before the split, `GabierTest` alone holding most of the groups). The resolution gives the figure after, and says for every
      group left in `GabierTest*` why it stays. Links are by name, so the figure is a floor: it is a measure, not a gate.
- [ ] `bin/ci`: `format`, `build` and `test` are enough (no production code changes); the worker says so.

The nested classes are taken out with `gabier move-type-to-file` (dogfooding), not by cutting and pasting. A moved class
loses its `@Nested`, and the number of tests run per class is compared before and after (the "old test -> new test" table
above): a top-level class still annotated `@Nested` would silently not run. A friction met with the command is a ticket.

Depends on: 01-11-2026-0855-gabier-moves-a-nested-type-to-its-own-file.md (the command used to move the nested classes, same
module). The structure it builds on, `31-10-2026-2348-gabier-is-composed-of-capabilities.md`, is closed.
Depends on: 01-11-2026-1041-gabier-inlines-a-variable-a-method-or-a-constant.md (same files: the tests of the cli grow with each command; the split
is done once the combo extract-variable, extract-method and inline is complete).
