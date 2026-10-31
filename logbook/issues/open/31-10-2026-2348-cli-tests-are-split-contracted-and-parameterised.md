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
- [ ] `bin/ci`: `format`, `build` and `test` are enough (no production code changes); the worker says so.

Depends on: 31-10-2026-2348-gabier-is-composed-of-capabilities.md (the structure it fixes, same module).
