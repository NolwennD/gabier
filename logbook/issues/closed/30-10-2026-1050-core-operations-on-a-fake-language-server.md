# The core diagnoses and renames against a fake language server

Opened: 30-10-2026 10:50

The business of gabier must advance without any real language server (`.claude/rules/workflow.md`, "Fake it until you
run it"). Define the port `JavaLanguageServer` in the domain of the core, with no LSP type: the diagnostics of a source
file (severity, line, column, message) and the rename of the symbol at a position, answered as the change set of
`apply-edits-to-files-atomically`, plus the refusals a server gives (not ready, no such symbol, invalid name, file
outside the project) as typed errors: exceptions of the shared error package, each with its `ErrorKey` and its
parameters (the file, the name, the position), so that one mapper at the edge can later turn any of them into a problem
report. Write a hand-written fake of the port that a test scripts with what the server
would answer, and the application use cases on top of the port: diagnostics of a file, and rename by position or by the
first occurrence of an identifier in a file (the `--token` of the Python driver), applying the answer, or giving the
diff on a dry run. Everything is proven with the fake: no jdtls, no process.

Done when:
- the port, the domain types and the use cases are in the core, null-safe and validated with `Assert`, with the
  architecture tests green and no LSP4J anywhere near them;
- the fake is usable from the tests of other modules (a test fixture of the core), and the tests of the use cases show:
  files written on a rename, nothing written on a dry run or on a refusal, and each refusal reported with its message;
- each answer the fake gives comes from a contract scenario (project files, request, answer in domain types) defined
  once in the test fixtures, in a form the adapters' integration tests can replay against a real server;
- the coverage stays at 100% and nothing in `check` starts a process.

Depends on: apply-edits-to-files-atomically.

## Resolution

Done in 1043cd0 (rebased by the supervisor from 21e353d). In the context `fr.craft.gabier.editing` of `core`: the port
`JavaLanguageServer` (`diagnostics(Path)`, `rename(Path, Position, String)` returning a `ChangeSet`), `Diagnostic` (a
severity, a `Position`, a message), the refusals as `LanguageServerException` with `LanguageServerErrorKey` (`not-ready`,
`no-such-symbol`, `invalid-name`, `file-outside-project`; all `BAD_REQUEST`; parameters file, line, column, name),
`TokenLocator` (whole words only, UTF-16 columns, any terminator, BOM ignored, a new `TOKEN_NOT_FOUND` key), and in
`editing.application` the use cases `Diagnose` and `Rename` (`RenameTarget.At` or `.Token`; `apply` and `dryRun` return a
`ChangeReport`; a refusal propagates and nothing is written).

The fake and the scenarios are test fixtures of `core` (`java-test-fixtures`, package `fr.craft.gabier.editing.fixtures`):
`Scenario` (project files by relative path, a request, an answer: diagnosed, renamed or refused), `Scenarios` (12: a clean
file, an error, a rename of a local variable, a field, a method and a type with its `RenameFile`, and the refusals) and
`FakeJavaLanguageServer`, scriptable only from a scenario, which refuses an unscripted or a twice-scripted request.
Because JPMS needs a descriptor for the fixtures jar and jdtls rejects a `module-info.java` in the fixtures source set,
the descriptor is given by an `extraJavaModuleInfo` rule keyed on the exact jar name `core-test-fixtures.jar` in the
convention plugin (it does not weaken the guard on other jars; any module whose fixtures are consumed needs its own
rule). A temporary consumer in `:daemon` was shown to compile and run against the fixtures, then removed.

Checked by the subagent: `bin/ci` green, run once at the end (format 6s, build 51s, test 54s, native 96s, jdtls 51s; it
started before the `--fast` and skip options existed, so nothing was skipped); counts: core 218, shared 236,
architecture 16, daemon 22, daemon quick probe tests 43, cli 9, protocol 4, no failure; coverage 100% per class; a
mutation (a dry run that applies) turns three tests red. The scenarios were checked against their sources by
`ScenariosTest` and by hand by the reviewer.

Review (one neutral reviewer, nothing blocking). Fixed by the supervisor: the Javadoc of `Scenarios` now says that a real
jdtls does not refuse a file outside the project and may answer an invalid rename with a null `prepareRename`, a null edit
or a response error, so the adapter maps rather than replays, and that the fake does not check that the files written are
the scenario's. Decided: the skill describing how to add a scenario and its replay ships with the ticket that writes the
first replay (`diagnostics-of-a-file-through-jdtls`, its `Done when` now says so), since the mechanism is not complete
before it. Opened as `probe-opens-the-test-sources`: the probe only opens `src/main/java`, yet jdtls found a real error in
a test of this ticket that javac accepted (the generics of a `map`). Left: `notReady` is `BAD_REQUEST` for lack of an
"unavailable" status; a split package between the fixtures and `src/test` could hurt a consumer whose tests run on the
module path; `Scenario.inside` throws a bare `IllegalStateException` if two paths collapse; the fixtures have no
`package-info`.
