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

## Resolution

Done in cf70052 (PR 47). `diagnostics --all` answers from the last publication per file that jdtls made, kept in the daemon
(`Diagnostics.published()`), with no walk of the source folders and no file opened. The same commit closes
`11-11-2026-1236-diagnostics-all-review-suggestions`, whose points all concerned the walk.

- **Observed on the real jdtls (1.63.0), before any code:** jdtls publishes diagnostics for files nobody opened, with
  Gradle, Maven and a hand-written Eclipse project, main and test sources, and publishes again after an edit on disk told
  with `didChangeWatchedFiles`. No `java.*` setting changes this; `JdtlsSettings` is unchanged. The capability used is
  `workspace.didChangeWatchedFiles.dynamicRegistration`, already declared. `window.workDoneProgress` and a job tracker were
  tried and dropped: `$/progress` does not report the init job of jdtls.
- **Signal of a finished build:** the answer to `java/buildWorkspace(false)` followed by 1 s without a publication
  (`Builds.SETTLE`). At startup that answer comes before the first publication of the whole workspace (about 6 s later on
  the 532-file Gradle checkout, where the first call answered clean twice), so `Builds.awaitInitialized` waits first for
  jdtls to register the files it watches (`WatchedFiles.registered()`, about 26 ms before that publication).
- **Time:** 151 s before. Now 50 s for the first call right after the daemon starts (jdtls start and Gradle import
  included), then 1.2 s per call with the same 1161-line output. A broken file created on disk shows up on the next call.
- **Visible change:** a clean file is no longer in the answer (`{"files":[]}` for a clean project).
- **Checked:** `bin/ci` full on the worktree: format, build, test (cli 1581, core 772, daemon 1084, protocol 414, shared 236,
  architecture 22, coupling ceilings unchanged), native passed; release-install and release-notes skipped by the tool. The
  jdtls stage failed once in `InstalledDaemonTest` (it still expected the clean file in the answer); the test was changed and
  the class re-run alone, the whole stage was not re-run by the worker. The check of the pull request, `bin-ci` on the full
  `--all`, passed (14 m 47 s).
- **Reuse by the stream ticket:** the store of what jdtls published and the signal of a finished build live in the daemon
  (`Diagnostics.published()`, `Builds`), readable by any method, as asked for `30-10-2026-1939-diagnostics-can-be-followed-as-a-stream`.
  The worker was told of that ticket by message during its work.
- **Review:** no blocking finding. Four suggestions (a deleted file, a project-level error read as clean, wording that reads as
  a guarantee, a jdtls that never registers its watchers) are in one issue on hold,
  `11-11-2026-1439-diagnostics-all-stream-review-suggestions`.
- **Process note:** the worker wrote part of the production code (`Builds`, the store) before its test, and checked the red
  phase only for `awaitInitialized`; the reviewer was told not to report process.
- **Not shown by `--all` (reported by the worker, no issue opened):** about 2161 errors that jdtls reports in sources outside
  the imported source folders on this checkout (for instance `fakeServed`), and the project-level errors (build path,
  `pom.xml`), since only `.java` files are kept.

Tokens:
- worker: 206221 (`subagent_tokens`)
- reviewer: 98291 (`subagent_tokens`)
- supervisor: 25 calls, output 17412, input + cache write 269942 (cache read 3438300 apart); covers the supervisor session
  from the launch of the worker to the merge, other work included (the discussion of the diagnostics stream and of
  Infinitest); exchanges with the user: 4 messages
