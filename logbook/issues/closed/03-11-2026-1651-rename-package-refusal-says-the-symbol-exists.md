# gabier rename of a package refuses with a message that is wrong

Opened: 03-11-2026 16:51

An agent that gives a package rename a new name jdtls cannot apply is told that there is no symbol, and goes looking for
a position error that is not there. Met on 03-11-2026 with the installed gabier, on the line
`package eu.gabier.shared.generation;` of `ExcludeFromGeneratedCodeCoverage.java`:
`gabier rename --dry-run <file> 1:20 shared.coverage` (the position is on `shared`, the new name does not start with the
current `eu.gabier`) answers `language-server-no-such-symbol: There is no symbol at 0:19`, while the position is on the
name of a package; `1:30 eu.gabier.shared.generated` (on the last segment, whole new name) renames it.

Done when:
- the case above is refused with a key and a message that say what is wrong (the new name must be the whole name, the
  clicked segment decides the prefix renamed) and not that there is no symbol; the key is listed in `gabier help rename`;
- observed on the real jdtls first (what it answers: an empty edit, a refusal?), a scenario in the catalogue replays it;
- `bin/ci` passes.

## Resolution

Done in `6ca0fb0`. The ticket's guess was wrong: the new name does not have to start with the current prefix. Observed on the
real jdtls: `prepareRename` declines a package name, and `textDocument/rename` on a segment whose prefix holds no class of
its own (`legacy` in `legacy.tools`, `demo` in `demo.app`) answers an empty edit, which the adapter turned into
`language-server-no-such-symbol`. It is now the new refusal `language-server-empty-package` (`LanguageServerErrorKey.EMPTY_PACKAGE`,
`LanguageServerException.emptyPackage`; parameters `file`, `line`, `column`, `package`, `declaredPackage`), whose message
names the empty package and says that the segment at the position decides how much of the name is renamed. The same empty
edit on a symbol that is not a package stays `no-such-symbol`; the last segment with the name it already has stays
`invalid-name`. Listed in `gabier help rename` and `docs/commands/rename.md`. Scenario
`rename-a-package-that-holds-no-class` (`PackageRenameScenarios`), replayed on the real jdtls by `ScenarioReplayTest`.

Checked: the worker's `bin/ci` on the commit, first full run `test` FAILED on `:daemon:jacocoTestCoverageVerification`
(`DaemonServer`, 2 lines missed; the file is untouched by this commit; run on a loaded machine); stages rerun alone on the
same code: test passed (611s), native passed (590s), jdtls passed (916s); format 18s and build 7s passed in the first run.
The flake may be the one of the open issue `a-daemon-test-fails-now-and-then`. The check `bin-ci` of the pull request
(#11) passed (10m4s). No ceiling of `JdtlsCouplingTest` raised.

Review (once, neutral): no blocking finding. Suggestions, opened together on hold as
`empty-package-refusal-review-suggestions`: the message is false and circular on the last segment when jdtls answers an
empty edit there; the dot between two segments is untested; the scenario's new name is not a valid prefix rename.

Tokens:
- worker: 215,010
- reviewer: 71,730
- supervisor: 25 calls, output 15,928, input + cache write 40,454 (cache read 4,023,795), over the period of the ticket,
  other work included (the load watch, the plan for v0.2.0); exchanges with the user: 4 messages
