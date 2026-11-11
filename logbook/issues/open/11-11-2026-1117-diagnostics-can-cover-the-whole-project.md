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
