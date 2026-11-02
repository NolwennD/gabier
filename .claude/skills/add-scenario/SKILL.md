---
name: add-scenario
description: Use when the core starts to rely on a new answer of a language server, to add a contract scenario to the catalogue and its replay against the real jdtls. Use also when a replay differs from a scenario, or when the adapter learns a new request that the catalogue already has scenarios for.
argument-hint: '<what the server is expected to answer>'
---

# Add a contract scenario and its replay

A scenario (`Scenario`, in `modules/core/src/testFixtures`, catalogue `Scenarios`) is what a language server is expected
to answer to one request on a small project. It is written once and used twice: the fake of the port is scripted with it
in `check`, and `ScenarioReplayTest` (`modules/daemon/src/integrationTest`) replays it against the real jdtls through
the adapter and fails on any difference **of effect**: the text of the files once the change set is applied, the severity
and the position of a diagnostic, the places of a reference, the key of a refusal, not the edits or the words jdtls chose.
A scenario that no replay checks is a guess (`.claude/rules/workflow.md`, "Fake it until you run it", which says why the
comparison is the effect: the coupling to jdtls is mastered, so that another language server stays possible). The replay
is generic: it takes every scenario of `Scenarios.all()`, so adding a scenario of a request the adapter already implements
adds its replay for free.

## Steps

1. **Say what the core relies on.** One answer, one request. Check that the catalogue has not got it already
   (`Scenarios.all()`).
   **Observe first.** Before writing the scenario, see what the real jdtls answers: a throwaway replay (a scratch test in
   `modules/daemon/src/integrationTest`, not committed) that starts jdtls on the project, sends the request through the
   adapter and prints the answer, run with the command of step 4. Write the scenario from that observation and from what
   the protocol and Java say, never from a guess corrected at the end. The guard stays: the scenario is not a copy of a
   run (a message, an indentation, an order that jdtls happens to choose is not part of what the core relies on).
2. **Write the scenario** in `Scenarios.java`: a `public static final Scenario` with a one-line Javadoc, a kebab-case
   name that says what it shows, the files, the request and the answer in domain types (0-based positions, paths
   relative to the root of the project; `FILE_WITH_A_WARNING` is a model). Reuse the project of the neighbouring
   scenarios (`PROJECT`, `withProblems()`): the replay starts one jdtls per distinct set of files, about ten seconds
   each, so a scenario that adds a file to a project shares that project with the ones that already have it. Write the
   answer you expect from the protocol and from Java, not from a run of the adapter. A change set is written with the
   simplest edits that have the effect (one edit per occurrence): the replay compares the text of the files once it is
   applied, not the edits, so jdtls's merged edit is not copied.
3. **Add its name to the catalogue**: in `Scenarios.all()` and in the list of `ScenariosTest`
   (`holdsTheClean...`), plus a test in `ScenariosTest` if it shows something the others do not (a position that holds
   the identifier it claims, for instance). Run the catalogue:
   `./gradlew :core:cleanTest :core:test --tests 'eu.gabier.editing.fixtures.*'`.
4. **Replay it on the real jdtls** (`GABIER_JDTLS` is the launcher; `mise exec --` outside an activated mise):
   `./gradlew :daemon:jdtlsProbe --tests 'eu.gabier.daemon.ScenarioReplayTest'`. The test prints what jdtls
   answered for each scenario, and lists the scenarios it does not replay (their request is not implemented by the
   adapter yet).
5. **A difference is a finding.** Read what jdtls answered and decide who is wrong:
   - the scenario (a column, a line of the resulting text): correct it with the value jdtls gives once you have checked
     it is right for the Java source, and say so in the commit or the resolution;
   - the adapter (a mapping, a refusal that jdtls does not make the way the scenario says): fix the adapter, with a
     unit test against `FakeJdtls` first.
   Never loosen the comparison, never skip the scenario to get green. To see the replay fail, change one character of the text the
   expected edits write and run it: it must name the scenario and print `expected` and `but was`.
6. **A refusal** (`Answer.Refused`) is mapped from what jdtls really does, not replayed blindly: jdtls often does not
   refuse (a file outside the project is opened as a non-project file and only warned about). The adapter decides, the
   scenario states the decision, the Javadoc of `Scenarios` says what jdtls does. A refusal that needs jdtls in a state
   (still importing) is provoked by the harness in a deterministic way (`needsJdtlsToImport` in the replay), never by a
   delay.
7. **The comparison is the effect** (`ScenarioReplayTest.compareEffect`, `Effect` in the test fixtures). The change set
   jdtls answers is applied to a copy of the project (`Effect.of`), and the sources it leaves are compared with the ones
   the scenario's change set leaves, then compiled with javac: the order and the shape of the edits do not count (the
   move of a file is at its new path in the result, a created file is there with its text), a set that cannot be applied
   is a difference. A diagnostic is compared by its severity and its position, never by the text of its message (which
   is printed: `<scenario>: jdtls answered ...`). The titles of the actions offered stay exact
   (`Scenario.Comparison.EXACT`, counted by `JdtlsCouplingTest`). The replay also checks that the sources of the project
   are untouched whatever was asked. `ScenariosTest` checks what holds for any cut of the edits (inside the file, on
   the symbol, starting with the new name) and that, once the answer is applied, each file of the project is its source
   with the whole word of the old name replaced by the new name (at its new path when the file is moved). The exact
   answer of jdtls (its edits, its messages) is tested where the adapter is, in `modules/daemon/src/test`, against
   `FakeJdtls`, not in a scenario.
8. **A request the adapter does not implement yet** is not replayed: the ticket that adds the request to the adapter
   extends `replayed` in `ScenarioReplayTest`, makes `ask` and `replay` call it, and runs this skill for each scenario
   that was waiting.
9. **Files that change on disk between two requests** are a `Sequel` (`Scenarios.sequels()`, not `Scenarios.all()`): two
   scenarios, the second written for the project as it is once the disk has changed (the first's answer applied to its
   files when it is a rename, the edit of a file otherwise). Write both scenarios as above (the second's `files` are the
   project after the change, its answer is what jdtls must say for the new text), add them to `Scenarios.sequels()` and
   to `SequelsTest`, which checks that applying the first's change set to the first's files gives the second's files. The
   replay (`ScenarioReplayTest.replay(Sequel, ...)`) runs the sequels last on the jdtls of their project, puts the files
   back as the first writes them, replays the first, changes the disk, then replays the second: jdtls answers for the new
   text only because the adapter tells it of the changes (`WatchedFiles`, `JdtlsLanguageServer.takeInChangesOnDisk`).
10. **A case that no replay can show is `@FakeOnly(reason = "...")`**: a null answer, a response error `InvalidRequest`
   that jdtls does not give, a server that dies, a timeout. It is a test of the adapter in `modules/daemon/src/test` (it
   checks how a failure is handled, never what jdtls answers when it is right), not a scenario; the annotation
   (`eu.gabier.editing.fixtures.FakeOnly`) carries the reason (the usual ones are in `FakeOnlyReasons`). A test that
   makes `FakeJdtls` play a failure (never answered, a failure other than `InvalidRequest`, an error status, a failed
   shutdown, null projects, changes never acknowledged) fails without it (`FakeOnlyGuard`, put on the test class with
   `@ExtendWith`), and a blank reason fails `FakeOnlyReasonTest`; a wrong-shaped answer or a dead process is not seen, label it by hand.
11. **Check the coupling.** `JdtlsCouplingTest` (core) counts the scenarios that compare an exact answer, the compiler
   messages, the action titles and the edits that write an indented line, against ceilings that only go down
   (`modules/core/build/jdtls-coupling.txt`, printed by the `test` stage of `bin/ci`). A scenario that raises a number
   says why in its issue, and the ceiling is raised in the same commit; one that lowers it lowers the ceiling.
12. **Update `Scenarios`'s Javadoc** if the scenario settles a point that was listed as unchecked, then run `bin/ci`
   (the replay is the `jdtls` stage: it runs when a file under `modules/` changed).
