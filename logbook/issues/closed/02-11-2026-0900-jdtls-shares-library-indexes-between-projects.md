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

## Resolution

Commit 763aeae. `bin/ci` (worker, full, on the final tree, 71b3b68): format 11s, build 20s, test 181s, native 161s, jdtls 297s, all passed; tests: cli 559, core 638, daemon 817, protocol 369, shared 236, architecture 19, 0 failures, coverage 100%. Reviewed once: one blocking finding (below), fixed by the worker; the suggestions are in `02-11-2026-1219-jdtls-workspace-lock-review-suggestions.md` (on hold). The tree `bin/ci` ran on is the final tree; commit 3 was not built on its own.

- Setting: `-Djdt.core.sharedIndexLocation=<cache>/jdtls-<version>/indexes`, passed with `--jvm-arg=` to `bin/jdtls` (`JdtlsLauncher.jvmArguments`); the cache is `$GABIER_CACHE`, else `$XDG_CACHE_HOME/gabier`, else `~/.cache/gabier` (a relative value is ignored); the version is that of the `org.eclipse.jdt.ls.core_<v>.jar` bundle of the launcher's distribution, and without it nothing is shared and the daemon logs it. **The ticket "The daemon starts jdtls without Python" (on hold) must carry this property to the `java` command line.**
- Proof: `SharedIndexesReuseTest` (`jdtlsProbe`, real jdtls): the first jdtls builds 2 indexes (the JDK and a jar) and shares them, a second one has none in its own data folder; red without the property ("Expected size: 2 but was: 0").
- Measure (this checkout, loaded machine): the first `gabier diagnostics` in a fresh worktree takes 31.0 s without shared indexes, 37.1 s with a cold cache, 31.7 s with a warm one; the very first run, 1 min 42, was cold for the OS and is not comparable. **The first diagnostics gains nothing.** Where it helps: the first `references` right after it, 47.4 s without and 28.6 s with a warm cache. The change is kept for the `references` / `rename` gain.

Tokens (the batch of four issues shares these figures):
- worker: 482246 (`subagent_tokens` of its last notification; the worker was resumed once, after the review, and the first notification said 446626).
- reviewer: 92192 (`subagent_tokens`).
- supervisor: 20 calls, output 27027, input + cache write 595597 (cache read 5591166); covers the supervisor session over the period of the batch (launch of the worker to now), other work included (the issue of the orphaned caches, the questions to the user); exchanges with the user: 2 messages typed, and two questions answered.
