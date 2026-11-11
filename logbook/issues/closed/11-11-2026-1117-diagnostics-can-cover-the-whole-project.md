# Diagnostics can cover the whole project

Opened: 11-11-2026 11:17

An agent that wants every error of the project has to list the Java files itself and pass them all to `gabier
diagnostics <file>...`. Once this is closed, one call (for instance `gabier diagnostics --all`) answers the problems of
every Java file of the project, as the build would show them, with no list to build by hand. Friction met while using
gabier: the command takes files only.

Done when:
- A call of `diagnostics` with no file list answers the problems of every Java file of the project the daemon serves,
  tests and other source sets included, in the same format as the call with files (text and `--json`); a clean project
  answers nothing, as a clean file does.
- The files the language server refuses (outside the project, missing) do not make the whole call fail: say in the
  issue's resolution how they are handled, and why.
- The daemon method, the cli command and the MCP tool `diagnostics` all offer it (skill `add-daemon-method`), the page
  `docs/commands/diagnostics.md` and `gabier help diagnostics` say it, and the guide (`gabier help guide`) answers the
  task "see all the errors of the project" with it (skill `extend-guide`).
- Tested against the fake first, with a contract scenario replayed on the real jdtls (skill `add-scenario`); how the
  files are chosen is read from what jdtls knows of the project, not guessed from the folder layout.

## Resolution

Done in 14d6596 (PR 46): `gabier diagnostics --all`, daemon method `diagnostics-all`, MCP tool `diagnostics-all`.

- **Why the MCP tool is `diagnostics-all` and not `diagnostics`:** the rule "a request has no optional parameter" makes two
  operations, `diagnostics` (parameter `files`) and `diagnostics-all` (none). The cli form stays `diagnostics --all`;
  `--all` with a file or `--dry-run` is a usage error.
- **Files chosen from what jdtls knows:** `java.project.listSourcePaths` (the existing `sourceFolders`), walked on disk.
  Files jdtls would refuse (a source folder outside the imported projects or not on disk, non-`.java` files, folders named
  `*.java`, duplicates of nested source folders) are left out, so one such file does not hide the problems of the others.
  jdtls exclusion patterns of source folders are not read: a file the build excludes is still opened.
- **Checked:** `bin/ci` full on the worktree, green (format, build, test: cli 1581, core 771, daemon 1082, architecture 22,
  Checkstyle, coverage 100%; native; jdtls with the two new scenarios replayed on the real jdtls; release-install and
  release-notes skipped, no changed file reaches them). One jdtls-stage run died on "Gradle build daemon disappeared
  unexpectedly" (memory) and passed when run again alone. Check of the pull request `bin-ci`: pass.
- **Observed on the real jdtls:** on this checkout, 532 files in 151 s, no failure. Slow on a large project.
- **Review:** no blocking finding. Five suggestions (a file deleted during the walk, a file that is not UTF-8, a symlinked
  source folder reported clean, an unreadable subfolder, memory and `didOpen` cost on a large project) are in one issue on
  hold, `11-11-2026-1236-diagnostics-all-review-suggestions`.
- **Left:** the fake-served daemon prints empty messages for scenario diagnostics (scenarios hold no messages; the
  `40-native` expectations carry it).

Tokens:
- worker: 111510 (`subagent_tokens`)
- reviewer: 80262 (`subagent_tokens`)
- supervisor: 40 calls, output 30138, input + cache write 82218 (cache read 3810517 apart); covers the supervisor session
  from the launch of the worker to the merge, other work included (the discussion of Infinitest); exchanges with the user:
  5 messages
