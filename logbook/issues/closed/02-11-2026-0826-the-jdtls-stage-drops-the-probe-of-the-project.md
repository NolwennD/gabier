# The jdtls stage no longer imports the gabier project itself

Opened: 02-11-2026 08:26
Depends on: 31-10-2026-2243-organize-imports-asks-diagnostics-only-when-it-can-lose-code.md

`bin/ci` takes 15 min 37 s (measured 02-11-2026: format 10 s, build 10 s, test 182 s, native 178 s, jdtls 554 s). In the
stage `jdtls`, `JdtlsProbeTest` alone takes 261 s (`ScenarioReplayTest` 165 s, `InstalledDaemonTest` 120 s): it makes jdtls
import the 7 modules of this repository, then checks 192 sources for errors and organizes the imports of 326 sources. Decision
of the user (02-11-2026): gabier is developed with gabier, so that jdtls works on this project is proven by use; the test goes.

**Care**: what the probe checked on small cases must not be lost. Decision of the user (02-11-2026): a file outside the project and
an import jdtls cannot resolve are answered by an error, nothing more; the refusal of the unresolved imports is the business of
`31-10-2026-2243-...` (which this issue waits for, and which brings its scenario). For each remaining case of the probe (refused
as not ready while importing; a clean file answers an empty list; a copy with an error gets it; a file outside the project is
refused; an untidy copy gets the edit that removes its unused import) name in the resolution the scenario or test that covers it
(the scenarios `diagnostics-*` and `organize-imports-*` are expected to), and add a contract scenario (skill `add-scenario`) for
each one that no scenario replays. The checks over the whole project (no error in the 192 sources; the imports of the 326 sources
unchanged) are dropped, and the resolution says so. The task `jdtlsProbe` stays (it runs the replay and the installed daemon); the
rules, skills, docs and the description of `bin/ci.d/50-jdtls` that name the probe are updated. The issue
`30-10-2026-1231-probe-opens-the-test-sources.md` is about this probe: close it as obsolete, with this reason, in the resolution.

Done when:
- [ ] `JdtlsProbeTest` is deleted; `./gradlew jdtlsProbe` still replays the scenarios and runs the installed-daemon test.
- [ ] Each case listed above is covered by a named scenario or test that fails if the behaviour changes; the ones that had none got a
      contract scenario replayed on the real jdtls.
- [ ] No rule, skill, doc or Javadoc names `JdtlsProbeTest`; the stage `jdtls` of `bin/ci` is timed before and after in the resolution.
- [ ] `bin/ci` passes.

## Resolution

Commit 4e07f9c. `bin/ci --all` (worker, full, on the worktree, machine busy): format 23s, build 29s, test 300s, native 290s, jdtls 467s, all passed. Not reviewed: small ticket, the production code changed is one Javadoc line (`JdtlsLanguageServer.QUIET`); the rest is the deletion of `JdtlsProbeTest` (311 lines), a Gradle property that only it read, and wording in rules, skills, docs and `bin/ci.d/50-jdtls`.

- Why: decision of the user (02-11-2026): gabier is developed with gabier, so that jdtls works on this project is proven by use; the probe made jdtls import the 7 modules, check 192 sources for errors and organize the imports of 326 sources, for 261 s of the 554 s of the stage `jdtls` (measured on the main checkout, 02-11-2026: whole `bin/ci` 15 min 37 s).
- Dropped: no error in the 192 sources, and the imports of the 326 sources unchanged. Kept: each small case of the probe is a scenario replayed on the real jdtls by `ScenarioReplayTest` (not ready while importing: `diagnostics-while-the-server-imports`; a clean file: `diagnostics-of-a-clean-file`; an error: `diagnostics-of-a-file-with-an-error`, also in `InstalledDaemonTest`; a file outside the project: `diagnostics-of-a-file-outside-the-project` and `organize-imports-of-a-file-outside-the-project`; an unused import: `organize-imports-of-a-file`; unresolved imports: `organize-imports-refuses-a-used-import-jdtls-cannot-resolve`). No scenario had to be added.
- Time of `./gradlew jdtlsProbe` on the worktree, same conditions: the three test classes added up to 664 s before (replay 295, installed daemon 139, probe 230) and 454 s after (replay 301, installed daemon 153): about 210 s less. The stage in the full `bin/ci` took 467 s (reference 554 s measured on another, quieter run: the two figures are not comparable beyond this order of magnitude).
- Not verified by the worker: that Gradle never saw `JdtlsProbeTest` missing during the baseline timing (it removed the file, then restored it before compilation); the baseline log shows the probe ran.
- The issue `30-10-2026-1231-probe-opens-the-test-sources.md` is closed as obsolete: the probe it was about is gone.

Tokens:
- worker: 93434 (`subagent_tokens`).
- reviewer: none, not reviewed.
- supervisor: 4 calls, output 3554, input + cache write 11194 (cache read 1113964); covers the supervisor session over the period of the ticket (launch of the worker to now), other work included; exchanges with the user: 0 messages.
