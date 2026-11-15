# `gabier test` starts its JVM from an AOT cache of the dependencies

Opened: 15-11-2026 18:13

An agent that calls `gabier test` again and again pays the start of a JVM each time (3.1 s for a small class on this
repository, 1.3 s of it the build). The measures of the closed ticket `14-11-2026-0125-aot-cache-speeds-up-a-test-jvm`
(its resolution, and the guide entry in `docs/improve.md`) show that an AOT cache of the dependency jars cuts about 40 % of
the start of a test JVM. Once this ticket is closed, `gabier test` makes and uses such a cache by itself, when asked to, and
the next calls start faster. The worker chooses how it is asked for (an option, a setting) and says why; with nothing asked,
the behaviour of the command does not change. The JVM arguments are built in one place, `ProcessTestRunner.jvmArguments`.

Done when:
- On this repository, a call of `gabier test` that uses the cache is faster than one that does not, measured as in the
  closed ticket (several runs, median, the load average noted), and the figures are in `docs/daemon.md`.
- A cache is never used when it is stale: another JDK build, a dependency jar that changed (path, size or date), and a daemon
  jar that changed each give a new cache or none, on a JDK that does not validate the jars (25 and 26) as on one that does;
  a test shows each case.
- A cache that cannot be made or used (a class folder on the class path, a `-javaagent`, an unsupported JDK, a full disk) never
  makes a test run fail or refuse: the run goes on with no cache; a test shows it.
- The caches are in the cache folder of gabier (`GABIER_CACHE`), and the ones that no longer match any project are removed, so
  they do not pile up.
- The help of `test`, `docs/daemon.md` and the guide entry on the AOT cache say what the agent sees and does, and stay true.

## Resolution

Done in d1b1894 (pull request 62, check `bin-ci` green). `gabier test` makes and uses an AOT cache (JEP 483) of the dependency
jars when the daemon is started with `GABIER_TEST_AOT_CACHE=on`; with anything else the command is unchanged. A call is 25 to
30 % faster (JDK 25: 4.2 s to 3.1 s; JDK 27: 4.3 s to 3.0 s; one class of 12 tests, 10 calls, medians, load average 7 to 10);
the first call pays 6 to 8 s once; a cache is 20 MB. The cache is keyed by the JDK build and the path, size and date of each
jar and of the daemon jar, so it is never used stale, which matters on JDK 25 and 26 that do not check the jars (tests on a real
JDK 25 for a changed size, a changed date alone and a changed daemon jar; the key weakened twice made them red). It lives in
`<GABIER_CACHE>/test-jvm/<project>/`; a project keeps its current key, and the folders of gone projects are removed. A cache
that cannot be made or used (failed creation, a JVM that refuses it, an unwritable folder, a daemon not run from a jar, a JDK
older than 25) never fails a run.

Why a setting and not an option of `test`: the protocol has no optional parameter, so an option would be required of every
client (the MCP tools included); the cost is that a running daemon keeps the setting it started with. Why the jars come first
on the class path: a cache must be a prefix of the class path and cannot be made with class folders, so with the setting on the
dependency jars and the daemon jar come before the class folders (a project class named like a library class is shadowed;
documented). Why a training run with no test class: a creation cannot have class folders, so only the launcher and engine
classes are cached (about two thirds of the classes loaded). Follows the closed ticket `14-11-2026-0125` (opt-in, key by JDK
build and jar path, size and date, `AOTMode=auto`).

Checked by the worker: full `bin/ci` green (test 451 s, native 285 s, jdtls 1249 s; release-install and release-notes skipped).
Review (neutral, once): nothing blocking; the suggestions are in
`15-11-2026-2012-the-aot-cache-of-gabier-test-is-hardened.md` (on hold). Frictions reported by the worker: `gabier test` on a
test that uses `CompiledSources` fails with "Unresolved compilation problem" (the known false errors of jdtls, ticket
`15-11-2026-1638-jdtls-reports-fixtures-as-not-accessible`); a `GABIER_SOCKET` under `/tmp` is refused (`socket-not-private`),
under the scratchpad it is too long.

Tokens:
- worker: 308363 (`subagent_tokens`)
- reviewer: 94285 (`subagent_tokens`)
- supervisor: covers the supervisor session from the launch of the worker, other work included: 10 calls, output 7309,
  input + cache write 311358 (cache reads 1411922); exchanges with the user: 0 messages
