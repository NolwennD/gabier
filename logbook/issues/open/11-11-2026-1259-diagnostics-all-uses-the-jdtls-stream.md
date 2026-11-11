# Whole-project diagnostics come from the jdtls stream

Opened: 11-11-2026 12:59

An agent that asks for the problems of the whole project gets them as fast as jdtls already knows them, whatever the number
of files, instead of waiting for every file to be opened and settled (532 files took 151 s on this checkout with
`gabier diagnostics --all`). jdtls compiles incrementally and publishes diagnostics (`publishDiagnostics`, kept per file
by `Diagnostics.publish`); the command should use that flow and not rebuild it file by file.

Done when:
- Observed first, on the real jdtls and with the settings of gabier (`JdtlsSettings`), and written in the resolution:
  whether jdtls publishes diagnostics for files that are not open, for which projects (Gradle, Maven, no build tool), and
  which signal says that its build is finished (progress notifications, status) in place of a silence of `quiet`. If
  jdtls publishes nothing for closed files under any setting, the worker stops and reports to the supervisor: no
  workaround is built (`.claude/rules/workflow.md`, "jdtls is never fought").
- `diagnostics --all` answers from what jdtls published, without opening every file; its time no longer grows with the
  number of files; measured on this checkout against the 151 s above, the figures in the resolution.
- After an edit made on disk, the answer reflects it once jdtls has rebuilt (a test, with the signal found above); a
  build still running is not answered as clean.
- Fake first, then the contract scenario replayed on the real jdtls (skills `add-scenario`); the page of the command, its
  help and the guide stay true.
- The open on-hold issue `11-11-2026-1236-diagnostics-all-review-suggestions` is revised: the points that concern the
  file walk and the opening of every file no longer apply and are dropped from it (or the issue is closed with its
  reason if none is left).
