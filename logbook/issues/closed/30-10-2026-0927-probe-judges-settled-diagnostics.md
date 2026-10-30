# The probe judges settled diagnostics

Opened: 30-10-2026 09:27

The diagnostics probe waits for the first publication of each opened file, then judges the latest one. jdtls publishes in
stages (a quick reconcile, then after the build), so a later publication with a real error can arrive after the verdict,
and a publication sent between `expect` and the `didOpen` of a file (the workspace build) can be taken for its answer.
`Diagnostics.pathOf` also throws on a URI that is not `file:` (`jdt://`), in the reader thread.

Done when:
- the probe judges only after jdtls is quiet: no new publication for a fixed pause, or the end of the build, inside the
  shared deadline, and the message says what it waited for;
- a publication that arrived before the `didOpen` of a file is never taken as the answer for it;
- a publication for a URI that is not `file:` is ignored without breaking the reader thread, with a test;
- the real probe still finds zero errors on the clean project and detects the control error and a deliberate error.

Depends on: quick-probe-tests-run-in-check (both edit the `integrationTest` source set).

## Resolution

Done in 52c8a24. `Diagnostics` takes an injectable clock and says how long it has been quiet (no `file:` publication);
`ProbeWatch.awaitQuiet` waits for a quiet of 5 seconds inside the probe's shared deadline, and its timeout names what was
awaited; the probe judges the latest publications only after that wait. Each file's expectation is registered right
before its own `didOpen` (`Diagnostics.open`). A publication for a URI that is not `file:` (`jdt://`) is ignored, with a
test, and does not reset the quiet clock. The probe's lines are dated since its start (`[12.3s]`).

The pause of 5 seconds, a constant of `JdtlsProbeTest`: in the real runs the publications of the opened files came within
about 2 seconds of each other, the largest gap was 1.5 seconds, and nothing came after the control. It adds 5 seconds to
a probe of about a minute. jdtls 1.63.0 never published a second stage in these runs, so the wait protects against a
latent risk that was not seen to happen.

Checked by the subagent: `bin/ci` green with `GABIER_JDTLS` set (format 1s, build 9s, test 31s, native 86s, jdtls 34s).
Real probe on the clean project: 10 files, one empty publication each (30.2 to 32.3 s), the control error reported
(`GabierProbeControl.java:4: Type mismatch: cannot convert from String to int`), then 5.0 s quiet with 0 late
publication. With `int OK = "zero"` in `Main.java`: the probe failed with
`Main.java:10: Type mismatch: cannot convert from String to int` (reverted since).

Limits worth knowing: `open` shrinks the window between the expectation and the `didOpen` but cannot close it; what
covers a publication emitted before jdtls read the `didOpen` is the quiet wait and the judgement of the latest
publication, so "never taken as the answer" is true up to that. The quiet window is global and fixed: a build silent for
more than 5 seconds could still let a late error through. If jdtls echoes the document version in `publishDiagnostics`
(the client capability `versionSupport`), the question could be settled exactly instead of by arrival order: not
checked, it would be a different approach, left to the user.

Review (one neutral reviewer, nothing blocking). Fixed by the supervisor after the merge: `pathOf` also ignoring a
malformed URI (`IllegalArgumentException`), its `isEmpty()`/`get()` pair, a badly wrapped comment, the `String.format`
of the dated lines. Left: two overlapping tests in `DiagnosticsTest`, `ProbeWatch` reading `System.nanoTime` directly
(the quiet tests of `ProbeWatchTest` sleep 100 to 400 ms, and assert outcomes only).
