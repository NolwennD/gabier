# The first call does not walk nested worktrees and dependency folders

Opened: 25-11-2026 21:35

Seen on a large Maven project whose checkout holds `.claude/worktrees/*` (each with its `node_modules`, about 5,500 files
per worktree) and is ignored by `.git/info/exclude`: the first call to the daemon of the main checkout told jdtls of
about 40,000 file changes at once, one 16 MB line of the daemon log, which weighs on that first call. The scan of the
files of the project (`WatchedFiles.scan`) lists every regular file below the root, whatever it is.

An agent working in a checkout that holds worktrees, or a `node_modules`, no longer pays for files that are not part of the
project.

Done when:
- A folder that has its own `.git` (a nested worktree or repository) and a `node_modules` folder are not scanned: no
  file below them is reported to jdtls as created, changed or deleted, with a test for each.
- A test shows that the files of the project itself are still reported, including a folder named like one of them that
  is not one (a source package called `node_modules` stays out of scope: say so in the resolution if the choice is by
  name only).
- Where the exclusion is documented (`docs/daemon.md`), the page says what is left out; the help of the commands is
  unchanged unless it mentioned the scan.
- The resolution says whether jdtls itself, with its own watchers, still walks these folders, and what was measured
  (the size of the first notification before and after).

## Resolution

Fixed in `5a526ea` (PR 63). `WatchedFiles.scan` leaves out, with everything below them, the folders named `node_modules`
(by name only) and the folders below the root that have a `.git` of their own, file or folder; the root is always
scanned. Six tests in `WatchedFilesTest` (four red before the change); `docs/daemon.md` says what is left out.

- **Checked**: `bin/ci` of the worker, exit 0: format, build, test (cli 1699, core 954, daemon 1405, protocol 447,
  shared 236, architecture 24, no failure), native and jdtls passed; `release-install` and `release-notes` skipped (no
  changed file reaches them). Check `bin-ci` of the pull request green.
- **Measured** (real daemons, synthetic Maven project with two nested worktrees and `node_modules` appearing while the
  daemon was stopped; one run each, so the times are indicative): the first call told jdtls of 3061 events (699,359
  bytes) before, none after; `diagnostics --all` 18.6 s before, 7.0 s after. The 16 MB line of the real project was not
  reproduced. jdtls itself still walks these folders (`strace`: about 21,500 folders opened below `node_modules` and
  `.claude/worktrees`, before and after): only the daemon's own scan changed, from 6450 folder opens to 2.
- **Choices**: a git submodule or a cloned repository that is part of the Java project is left out too (it has a `.git`);
  stated in the page. A daemon that starts on a state kept by an earlier version reports once, as deleted, the files of
  that state below a folder now left out: the page said "never" and now states the exception (fixed by the supervisor,
  not part of the commit).
- **Review** (neutral, once): no blocking finding. Suggestions, all in the issue
  `25-11-2026-2218-the-state-a-daemon-kept-is-filtered-by-the-scan-exclusions.md`: the kept state is not filtered by the
  exclusions; two assertions on `fingerprint()` cannot fail; a `.git` that is a link is neither tested nor documented.
- **Not in this ticket**: `FolderWatch.registerTree` (`diagnostics --watch`, `affected-tests --watch`) still registers a
  watch on every folder, these included; not measured, no issue opened (no real use yet).

Tokens:
- worker: 134,017 tokens (`subagent_tokens`)
- reviewer: 66,257 tokens (`subagent_tokens`)
- supervisor: 32 calls, output 19,037, input + cache write 41,885 (cache read 3,363,623), measured over the supervisor
  session from the launch of the worker (20:40 UTC) to the closing, other work included (the second issue of the same
  report, questions on the load of the machine, Baloo and the Gradle daemon); exchanges with the user: 5 messages
