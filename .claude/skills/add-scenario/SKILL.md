---
name: add-scenario
description: Use when the core starts to rely on a new answer of a language server, to add a contract scenario to the catalogue and its replay against the real jdtls. Use also when a replay differs from a scenario, or when the adapter learns a new request that the catalogue already has scenarios for.
argument-hint: '<what the server is expected to answer>'
---

# Add a contract scenario and its replay

A scenario (`Scenario`, in `modules/core/src/testFixtures`, catalogue `Scenarios`) is what a language server is expected
to answer to one request on a small project. It is written once and used twice: the fake of the port is scripted with it
in `check`, and `ScenarioReplayTest` (`modules/daemon/src/integrationTest`) replays it against the real jdtls through
the adapter and fails on any difference. A scenario that no replay checks is a guess (`.claude/rules/workflow.md`, "Fake
it until you run it"). The replay is generic: it takes every scenario of `Scenarios.all()`, so adding a scenario of a
request the adapter already implements adds its replay for free.

## Steps

1. **Say what the core relies on.** One answer, one request. Check that the catalogue has not got it already
   (`Scenarios.all()`).
2. **Write the scenario** in `Scenarios.java`: a `public static final Scenario` with a one-line Javadoc, a kebab-case
   name that says what it shows, the files, the request and the answer in domain types (0-based positions, paths
   relative to the root of the project; `FILE_WITH_A_WARNING` is a model). Reuse the project of the neighbouring
   scenarios (`PROJECT`, `withProblems()`): the replay starts one jdtls per distinct set of files, about ten seconds
   each, so a scenario that adds a file to a project shares that project with the ones that already have it. Write the
   answer you expect from the protocol and from Java, not from a run of the adapter. The edits of a rename are the
   exception: jdtls merges the edits of a file, so write the expected ones as a first guess and replace them in step 7
   with the answer of the replay, copied byte for byte.
3. **Add its name to the catalogue**: in `Scenarios.all()` and in the list of `ScenariosTest`
   (`holdsTheClean...`), plus a test in `ScenariosTest` if it shows something the others do not (a position that holds
   the identifier it claims, for instance). Run the catalogue:
   `./gradlew :core:cleanTest :core:test --tests 'fr.craft.gabier.editing.fixtures.*'`.
4. **Replay it on the real jdtls** (`GABIER_JDTLS` is the launcher; `mise exec --` outside an activated mise):
   `./gradlew :daemon:jdtlsProbe --tests 'fr.craft.gabier.daemon.ScenarioReplayTest'`. The test prints what jdtls
   answered for each scenario, and lists the scenarios it does not replay (their request is not implemented by the
   adapter yet).
5. **A difference is a finding.** Read what jdtls answered and decide who is wrong:
   - the scenario (a message text, a column): correct it with the value jdtls gives once you have checked it is right
     for the Java source, and say so in the commit or the resolution;
   - the adapter (a mapping, a refusal that jdtls does not make the way the scenario says): fix the adapter, with a
     unit test against `FakeJdtls` first.
   Never loosen the comparison, never skip the scenario to get green. To see the replay fail, change one expected value
   and run it: it must name the scenario and print `expected` and `but was`.
6. **A refusal** (`Answer.Refused`) is mapped from what jdtls really does, not replayed blindly: jdtls often does not
   refuse (a file outside the project is opened as a non-project file and only warned about). The adapter decides, the
   scenario states the decision, the Javadoc of `Scenarios` says what jdtls does. A refusal that needs jdtls in a state
   (still importing) is provoked by the harness in a deterministic way (`needsJdtlsToImport` in the replay), never by a
   delay.
7. **The comparison is exact, and the scenario holds what jdtls answered.** jdtls merges the edits of a file into one that
   spans from the first occurrence to the last and repeats the code between them (a local variable used twice, a field,
   a type used twice on a line): the scenario of such a rename holds that edit, its `newText` copied byte for byte from
   the line `<scenario>: jdtls answered ...` of the replay (indentation and line breaks included), never retyped and
   never split into one edit per occurrence. The replay compares the change set with `isEqualTo` (the order counts:
   edits first, the move of a file last, under its old path), then applies it to a copy of the project and compiles
   it, and checks that the sources of the project are untouched whatever was asked. `ScenariosTest` checks what
   holds for any cut of the edits (inside the file, on the symbol, starting with the new name) and that, once the
   answer is applied, each file of the project is its source with the whole word of the old name replaced by the new
   name (at its new path when the file is moved).
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
10. **Update `Scenarios`'s Javadoc** if the scenario settles a point that was listed as unchecked, then run `bin/ci`
   (the replay is the `jdtls` stage: it runs when a file under `modules/` changed).
