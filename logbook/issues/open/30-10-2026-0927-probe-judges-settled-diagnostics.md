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
