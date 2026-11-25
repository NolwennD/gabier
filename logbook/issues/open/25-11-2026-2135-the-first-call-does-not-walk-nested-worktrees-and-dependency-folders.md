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
