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
the usage-error loops, `@MethodSource` on `Capabilities.all()` for the help of each command. AssertJ only; style of reference:
`RequestHandlerTest.java:258` in the module `daemon`.

Done when:
- [x] No test lost or weakened: the resolution holds the table "old test -> new test or contract" for every test of `GabierTest`, and
      coverage stays at 100%. A test that cannot fail is a reason to stop and report.
- [x] No test class of the module `cli` over 400 lines, no copy of `socket()` or `runWithSocket()` left.
- [x] Adding a command that returns changed files is one `@Nested` class of about twenty lines (shown on the last command added).
- [x] The duplication measured by `ripwire modules/cli --clones` (`dup_pct=` in the `<clones>` line) falls from 39.2% (01-11-2026,
      before the split, `GabierTest` alone holding most of the groups). The resolution gives the figure after, and says for every
      group left in `GabierTest*` why it stays. Links are by name, so the figure is a floor: it is a measure, not a gate.
- [x] `bin/ci`: `format`, `build` and `test` are enough (no production code changes); the worker says so.

The nested classes are taken out with `gabier move-type-to-file` (dogfooding), not by cutting and pasting. A moved class
loses its `@Nested`, and the number of tests run per class is compared before and after (the "old test -> new test" table
above): a top-level class still annotated `@Nested` would silently not run. A friction met with the command is a ticket.

Depends on: 01-11-2026-0855-gabier-moves-a-nested-type-to-its-own-file.md (the command used to move the nested classes, same
module). The structure it builds on, `31-10-2026-2348-gabier-is-composed-of-capabilities.md`, is closed.
Depends on: 01-11-2026-1041-gabier-inlines-a-variable-a-method-or-a-constant.md (same files: the tests of the cli grow with each command; the split
is done once the combo extract-variable, extract-method and inline is complete).
Depends on: 01-11-2026-1100-a-command-is-declared-in-its-own-class.md (same files: `Command` stops being an enum, and the tests that read it
change source; the split is done after).

## Resolution

Commit 36add0c. The tests of the cli are split by capability: `CliTest` (base: `@TempDir`, `out()`, `err()`, `run`, `socket()`,
`runWithSocket`, a factory of `ScriptedDaemon`; the only copy of `socket()` / `runWithSocket()`), `CliFixtures`, one class per capability
(`DiagnosticsTest`, `StatusAndStopTest`, `RenamingTest`, `OrganizingImportsTest`, `ExtractingAVariableTest`, `ExtractingAMethodTest`,
`MovingATypeToFileTest`, `InliningTest`, `HelpTest`, `StartingTheDaemonTest`, `WithoutADaemonTest`), `DaemonAnswersTest` (refusals and
invalid answers common to the commands that talk to the daemon, parameterised), `UnknownUsageTest` (the usage-error loops, 60 cases) and a
`GabierTest` of 141 lines (Version, JsonOption, Failure). `ChangesCommandContract` holds nine default tests, implemented by a `@Nested`
`ReturningChangedFiles` per command that gives `cli()`, `commandName()`, `afterTheFile()` and `request(dryRun, file)`: about twenty lines
(`InliningTest.ReturningChangedFiles`, the last command added). No production code touched.

Old test -> new test or contract (`GabierTest` had 144 cases):
- Version 3, JsonOption 6, Failure 5: unchanged in `GabierTest`.
- UnknownUsage 19: `UnknownUsageTest.aCommandLineThatMatchesNoUsageIsAnUnknownUsage`, 60 cases (exit 2, empty stdout, stderr equal to the usage
  error); the three `--bogus` tests are one case.
- Help 13: `HelpTest`, 24 cases (`help`, `--help`, `-h` parameterised; `helpOfEveryCommandIsAvailable` over `Capabilities.all()`; 11 unchanged).
- Status 13, Stop 3 (`StatusAndStopTest`): 12 unchanged; the refusal as text and as JSON and the invalid result go to `DaemonAnswersTest`
  [status], [stop] (the JSON refusal of `stop` is new).
- Diagnostics 13 (`DiagnosticsTest`): 10 unchanged; refusal text, refusal JSON and invalid result go to `DaemonAnswersTest`.
- Renaming 14: 5 own (position, `--token`, dry run by token with the two options in either order, nothing changed, plus the added
  `--json --token`) + 9 contract; refusals to `DaemonAnswersTest`.
- OrganizingImports 12: 3 own + 9 contract. ExtractingAVariable 11: 2 own + 9 contract. ExtractingAMethod 6, MovingATypeToAFile 6, Inlining 6:
  1 own + 9 contract each. Refusals to `DaemonAnswersTest`.
- StartingTheDaemon 7: unchanged. WithoutADaemon 7: `status` and `stop` messages are one parameterised test; 5 unchanged.
Not lost: per-class counts equal before and after the move (compared with the surefire XML saved before any edit); a mutation of
`ChangedFilesReply` (a "created" line, the dry-run line) fails 19 tests; no assertion went from exact to weaker (contract uses
`containsExactly` where a command had `startsWith`/`contains`). New coverage: absolute path, terminal escapes and invalid answer for the
changed-files commands that lacked them; `--json` before and after the words for every daemon command.

`ripwire modules/cli --clones`: `dup_pct` 39.2% (issue) -> 41.4% at the start (inline and move added) -> **29.8%**. No group left in
`GabierTest*`. Left elsewhere: the daemon launcher and client tests and `ArgumentsTest` (outside the split, distinct setups); the
scaffold `try (daemon) { run; assert }` of the contract and of `DaemonAnswersTest` (different behaviour asserted); per-command "asks the
daemon" tests (same shape, different request constants); the data tables; the `run` of `Inline`, `MoveTypeToFile`, `ExtractSelection`,
`OrganizeImports` (production code, out of scope).

Checked: see `01-11-2026-1100-a-command-is-declared-in-its-own-class.md` (one `bin/ci` for the two commits; cli 374 cases, was 282). Review
(neutral, once): no blocking finding. Suggestion left, opened on hold: the command lists of `DaemonAnswersTest` and `UnknownUsageTest` are
hard-coded and no test ties them to `Capabilities.all()`.

Dogfooding: `rename` (13 nested classes), `move-type-to-file` (12 classes) and `organize-imports` used. Friction met (opened as a ticket):
`move-type-to-file` keeps `@Nested` on the class it makes top-level; the worker removed it by hand in 12 files. The installed `gabier`
was a build older than `move-type-to-file` and `inline`: the worker built one from its worktree. New classes and rewritten test bodies are
text edits (no `create`, no command to rewrite a body).

Tokens: see the resolution of `01-11-2026-1100-a-command-is-declared-in-its-own-class.md` (one batch, one worker, one reviewer, one
supervisor measure: worker 288023, reviewer 146098, supervisor 7 calls, 6184 output, 17974 input + cache write, 1110811 cache read;
exchanges with the user: 0 messages).
