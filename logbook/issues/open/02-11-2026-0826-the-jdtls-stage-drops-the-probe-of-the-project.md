# The jdtls stage no longer imports the gabier project itself

Opened: 02-11-2026 08:26

`bin/ci` takes 15 min 37 s (measured 02-11-2026: format 10 s, build 10 s, test 182 s, native 178 s, jdtls 554 s). In the
stage `jdtls`, `JdtlsProbeTest` alone takes 261 s (`ScenarioReplayTest` 165 s, `InstalledDaemonTest` 120 s): it makes jdtls
import the 7 modules of this repository, then checks 192 sources for errors and organizes the imports of 326 sources. Decision
of the user (02-11-2026): gabier is developed with gabier, so that jdtls works on this project is proven by use; the test goes.

**Care**: what the probe checked on small cases must not be lost. For each of its cases (refused as not ready while importing; a
clean file answers an empty list; a copy with an error gets it; a file outside the project is refused; an untidy copy gets the edit
that removes its unused import; `organize-imports` refused on a source whose imports jdtls cannot resolve, with the imports named)
name in the resolution the scenario or test that covers it, and add a contract scenario (skill `add-scenario`) for each one that
no scenario replays. The checks over the whole project (no error in the 192 sources; the imports of the 326 sources unchanged)
are dropped, and the resolution says so. The task `jdtlsProbe` stays (it runs the replay and the installed daemon); the rules, skills,
docs and the description of `bin/ci.d/50-jdtls` that name the probe are updated. The issue
`30-10-2026-1231-probe-opens-the-test-sources.md` is about this probe: close it as obsolete, with this reason, in the resolution.

Done when:
- [ ] `JdtlsProbeTest` is deleted; `./gradlew jdtlsProbe` still replays the scenarios and runs the installed-daemon test.
- [ ] Each case listed above is covered by a named scenario or test that fails if the behaviour changes; the ones that had none got a
      contract scenario replayed on the real jdtls.
- [ ] No rule, skill, doc or Javadoc names `JdtlsProbeTest`; the stage `jdtls` of `bin/ci` is timed before and after in the resolution.
- [ ] `bin/ci` passes.
