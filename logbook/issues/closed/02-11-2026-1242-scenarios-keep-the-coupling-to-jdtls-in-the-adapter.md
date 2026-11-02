# The scenarios keep the coupling to jdtls in the adapter

Opened: 02-11-2026 12:42

Decision of the user (02-11-2026): the coupling to jdtls is mastered over time, so that another Java language server stays possible.
The rule "Fake it until you run it" (`.claude/rules/workflow.md`) and the skill `add-scenario` change in two ways, and the fake keeps
the marginal cases. Today the replay compares the answer of jdtls with the scenario by `isEqualTo`: messages of the Eclipse compiler,
the exact shape of an edit, the indentation of a text block (97 mentions of jdtls in the fixtures); the 19 ids of `CleanUp` are in the domain.

1. **Observe first, then script.** A new answer of jdtls is observed on the real server before the scenario and the fake are written
   (a throwaway replay that prints it), not guessed and corrected at the end. The existing guard stays: the scenario states what the
   protocol and Java say, not a copy of a run; the observation is where it starts.
2. **Compare the effect, not the answer.** A scenario of the core states the effect: the text of the files once the change set is applied
   (rename, extract, move, inline, cleanup, organize-imports); for diagnostics the severity and the position, not the text of a message.
   The exact answer of jdtls (edits, messages, command arguments) is the business of the adapter: its tests keep the recorded exchange
   (in `modules/daemon`), the core never sees it. Another server that writes the same effect with other edits passes the same scenarios.
3. **The fake keeps the marginal cases**: faults that real jdtls cannot produce on demand (null answer, `InvalidRequest`, an edit with no
   message, a server that dies, a timeout, not ready). Each is labelled "fake only" with the reason why no replay can show it, and carries
   the handling by the adapter of a failure, never what jdtls answers when it is right.
4. **The coupling stays visible**: a number kept up to date and shown by the build (a test or a line of `bin/ci`): how many scenarios still
   compare an exact answer of jdtls instead of an effect, and how many values observed from jdtls (messages of the compiler, indentation)
   the core fixtures hold. It goes down; a ticket that raises it says why.

Existing scenarios migrate when a ticket touches them (not in one pass), the count of point 4 says how many remain. The ticket
`01-11-2026-1038-another-language-server-measures-the-jdtls-coupling.md` (on hold) stays the real measure.

Done when:
- [ ] `workflow.md` ("Fake it until you run it") and the skill `add-scenario` say the four points above, with the existing guard
      (not a copy of a run) kept; `AGENTS.md`/`verify` updated where they describe the replay.
- [ ] The replay compares the effect for the families of scenarios that have a change set (rename, extract, move, inline, cleanup,
      organize-imports) and the severity and position for diagnostics, through one comparison in the replay; the exact answer of jdtls is
      kept in the adapter tests; the scenarios already in the catalogue of one of these families are migrated (a family per commit if need
      be, say which are left, and the count of point 4 with them).
- [ ] The fake-only cases are labelled with their reason (a test fails on one without).
- [ ] The coupling number of point 4 is printed by `bin/ci` (or fails above a ceiling that only goes down). `bin/ci` passes.

## Resolution

Commit ea8aa60 (rebased by the worker over the removal of the kept workspace, then `bin/ci` run again). `bin/ci` (worker, full, once on the rebased tree): format 16s, build 5s, test 220s, native 27s, jdtls 375s, all passed; tests: cli 598, core 684, daemon 866, protocol 366, shared 236, architecture 19, 0 failures. Reviewed once: one blocking finding (the label `@FakeOnly` was declared and not enforced), fixed and checked by `bin/ci`, not reviewed again.

- Decision of the user (02-11-2026): the coupling to jdtls is mastered in time. The scenarios of the core state the effect; the exact exchanges of jdtls stay in the tests of the adapter (`modules/daemon`); the fake keeps the marginal cases, each with its reason; the coupling is counted and only goes down. `CleanUp` stays in the core (wanted coupling).
- Effect: `Effect.of(root, changes)` (core `testFixtures`) and `ScenarioReplayTest.compareEffect`: the replay applies the answer of jdtls to a copy of the project, compares the resulting sources with the ones the scenario's change set leaves, and compiles them. The sealed `Answer.Changing` groups the 9 answers that carry a change set (rename, extract, move, move to package, inline, create, code action applied, organize imports, clean-up), `Scenario.Comparison` is EFFECT, SEVERITY_AND_POSITION or EXACT. Diagnostics are compared by severity and position; the message is printed, not compared. Shown red on the real jdtls by changing one character of the expected edit of `rename-a-method`.
- Coupling figures (127 scenarios): exact comparisons 56 (42 change sets, 5 diagnostics, 4 offered actions, 5 references) before, 4 after (the offered actions: the title is jdtls's own text, and it is how the core selects an action); compiler messages held in fixtures 4, held and no longer compared; edits that write an indented line 26 (a regex proxy over `newText`, unchanged). The references are still compared exactly and counted as effect: a judgement call of the worker.
- Ceilings set in `JdtlsCouplingTest` at today's values: 4 exact comparisons, 4 compiler messages held, 3 action titles held, 26 indented edits; `bin/ci.d/30-test` prints `modules/core/build/jdtls-coupling.txt` after the test counts and fails if it is missing.
- Fake only: `@FakeOnly(reason)` (mandatory reason) and `FakeOnlyGuard`, a JUnit extension that fails the test after it ran, naming `Class#method`, when the fake `FakeJdtls` played a failure (a request that never answers, a failed future that is not an `InvalidRequest` response error, an error status, a shutdown that does not answer, `answersNothingToProjects`, `neverAcknowledgesChanges`) and neither the method nor an enclosing class carries `@FakeOnly` with a non-blank reason; `FakeOnlyReasonTest` scans the daemon test classes recursively, fails on a blank reason and on a test class that uses `FakeJdtls` without the guard; shown red once; 26 existing tests were found unlabelled and labelled (`FakeOnlyReasons`). **Limit, which amends the issue's third point to what is enforced**: failures that jdtls produces and the replay shows (a response error `InvalidRequest`, a null answer, "still importing", `neverAnswersInitialize`) are not flagged, so as not to label replayed cases as fake-only; a value of the wrong shape and a dead process stay labelled by hand. The core `FakeJavaLanguageServer` scripts no failure (only scenarios), so no core test is guarded.
- Text: `workflow.md` ("Fake it until you run it") has the four points and keeps the guard "not a copy of a run"; the skill `add-scenario` has the observe-first step, the simplest-edits step, the effect comparison, the `@FakeOnly` step and the coupling check; `verify` is updated. Existing scenarios were not rewritten: they migrate when a ticket touches them.
- Review suggestions: `02-11-2026-1835-coupling-ceilings-review-suggestions.md` (on hold).

Tokens:
- worker: 277657 (`subagent_tokens` of its last notification; the worker was resumed twice: after the removal commit and after the review).
- reviewer: 81779 (`subagent_tokens`).
- supervisor: 46 calls, output 57351, input + cache write 117529 (cache read 21719321); covers the supervisor session over the period of the ticket (launch of the worker to now), other work included (the shipping of the removal ticket, the study of the release of gabier with JReleaser and GraalVM); exchanges with the user: 2 messages.
