# jdtls shares the indexes of the libraries between projects

Opened: 02-11-2026 09:00
Depends on: 31-10-2026-2243-organize-imports-asks-diagnostics-only-when-it-can-lose-code.md

An agent gets the first answer in a new worktree without waiting for the JDK and the jars of `~/.gradle` to be indexed
again: today each jdtls indexes them alone (about 1 min 30 for this checkout, 226 Mo in its `-data`).

**jdtls is configured, not fought**: the system property `jdt.core.sharedIndexLocation` is the setting that gives this
(`IndexUtils.copyIndexesToSharedLocation`, called by `JDTLanguageServer` once the import is over; vscode-java's
`java.sharedIndexes.*`); no `java.*` setting replaces it. It goes through `--jvm-arg=` while the Python launcher serves;
the ticket "The daemon starts jdtls without Python" (on hold) must carry it to the `java` command line.

Done when:
- [ ] The daemon passes `-Djdt.core.sharedIndexLocation=<folder>`, one folder per jdtls version:
      `$XDG_CACHE_HOME/gabier/jdtls-<version>/indexes`, else `~/.cache/gabier/...`; a documented variable moves it.
- [ ] An integration test shows that a second jdtls on the same index folder reuses the index of a jar instead of
      building it again.
- [ ] The time of the first `gabier diagnostics` in a fresh worktree is measured before and after and written in the
      `## Resolution`; if the gain is negligible the resolution says so and why the change is kept or dropped.
- [ ] `docs/daemon.md` says what the cache holds, where it is and how to empty it. `bin/ci` passes.
