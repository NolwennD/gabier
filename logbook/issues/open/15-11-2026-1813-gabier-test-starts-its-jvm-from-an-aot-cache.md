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
