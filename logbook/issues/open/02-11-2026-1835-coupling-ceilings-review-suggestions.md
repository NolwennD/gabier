# Coupling to jdtls: review suggestions

Opened: 02-11-2026 18:35
On hold: findings of the review of ea8aa60, none breaks a `Done when` point; the user lifts the hold.

Done when:
- [ ] `JdtlsLanguageServerTest` (`@FakeOnly` on `ExtractingWhatJdtlsCannotExtract`, reason `NOTHING_IS_THE_ONLY_ANSWER`): the reason says that the replay shows the case, which contradicts the label ("no replay can show it"); the label goes, or the reason says why the replay cannot.
- [ ] `ScenarioReplayTest.compareEffect`: a core test shows that for every `Answer.Changing` the effect of the scenario's change set differs from the project's own files (a no-op scenario would pass against a no-op jdtls).
- [ ] `JdtlsCouplingTest` ceilings (4 exact comparisons, 4 compiler messages held, 3 action titles held, 26 indented edits): the ceiling constants are checked against a value kept in a file that a review sees change (the figures are written to `modules/core/build/jdtls-coupling.txt`, a build output), so that raising a ceiling is a visible diff.
- [ ] `JdtlsCouplingTest` `INDENTED_LINE`: the figure of the indented edits counts every edit that holds an indented line (the merged rename edits repeat the project's own text); it measures what the server's formatter decided, or its Javadoc says it is a proxy that overstates.
- [ ] The report line "compared by their effect" counts the references and the refusals, which are compared exactly: it says what it counts.
- [ ] `Effect.sources` (Effect.java:63) walks regular files only: an empty folder left or created by a move is part of the compared effect. `Effect.java:87`: `root.relativize(file)` throws on a path outside the root; `compareEffect` records a difference instead of aborting the replay.
- [ ] `FakeOnlyGuard` does not see a value of the wrong shape (a null edit, an edit with no message, an edit of another file), a dead process and `neverAnswersInitialize`: those tests stay labelled by hand; either they are made visible (a scripted-failure marker on those fake methods) or the doc keeps saying it.
