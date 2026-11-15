# An AOT cache speeds up the JVM that runs a project's tests

Opened: 14-11-2026 01:25

An agent that runs the tests of a project (after `gabier affected-tests`) starts a JVM for each run, and the start is most of
the time of a short run. The AOT cache of the JDK (JEP 483, class loading and linking; JEP 515, method profiles) may shorten
it. JEP 544 (AOT code) is in a JDK later than the ones tested here, so it is out. This is an exploration: measures first, a
decision, and tooling only if the gains are worth it. Once closed, an agent knows whether its test JVM can start faster, how,
and finds that out without being told.

Done when:
- Measured on this repository, with the numbers in the resolution: a JVM that runs a JUnit test class of the project with no
  cache, with a cache of class loading and linking, and with a cache that also holds the profiles; on the JDK 25 and the JDK 27
  of the SDKMAN! candidates, **none of them GraalVM**. Several runs of each, the median and the spread. The size of a cache,
  the time to create it, and what makes it unusable (the conditions of the JEPs, checked against a project classpath that
  holds class folders and against classes loaded by a class loader of the project) are written, each one read from a JEP or
  observed.
- What the measures need is thrown away and is not in the repository; the resolution says how to redo them.
- The worker stops after the measures and reports them to the supervisor with a recommendation. What follows is done only on
  the supervisor's go.
- On the go: an agent that wants to run tests faster finds, with `gabier help --for` and the guide (`gabier help guide`),
  that the cache exists, whether it applies to its JDK, and how to create and use it. Whether gabier creates the cache or
  only documents it is decided with the supervisor from the measures (skills `extend-guide`, and `add-daemon-method` if a
  command is added).

## Resolution

Done in a3ee5f7 (pull request 60, check `bin-ci` green): measures, then documentation only, no command and no cache code.
Not reviewed: small ticket (a guide entry and two questions of its test, no production code).

Measures (tests of `:core` run by a minimal JUnit Launcher main, one JVM per run; class `RenameTest`, 32 tests; Temurin 25.0.2
and 27.0.0.0+35, neither GraalVM; 16 cores, load average 5 to 10 from other work; runs interleaved, wall medians in ms):

- JDK 25, N=15: no cache 1580 (min 1370, max 2520, sd 304); cache of classes 810; cache with profiles 760.
- JDK 27, N=15: no cache 1600 (sd 107); classes 860; profiles 770.
- Cache of the dependency jars only, the project classes appended after them (N=12): 950 on both JDKs against 1590 (25) and
  1540 (27) with no cache.
- A cache trained on all 63 test classes serves another class as well (3 classes, JDK 25: none 1360, cache 660); with profiles
  it is slower (about 970 against about 800). Profiles add nothing on runs this short.
- Whole module (927 tests, N=5): JDK 25 14.7 s to 11.6 s; JDK 27 18.4 s to 14.9 s (classes) or 15.7 s (profiles).
- Cost on JDK 25: record 3.7 to 4.5 s, create 1.6 to 2.9 s; 26.0 MB (classes) and 27.8 MB (profiles) trained on `RenameTest`,
  50 and 59 MB trained on all classes (record 18 to 22 s); dependency cache 23.7 MB (JDK 25) and 24.4 MB (JDK 27).

Conditions, each marked observed or read from a JEP in the guide entry: a class folder on the creation class path fails (exit
1); JDK 25 and 26 do not validate application jars, so a rebuilt jar runs silently from the old cache, even with
`AOTMode=on`, while JDK 27 refuses; a cache of another JDK build is refused; ZGC, a very large heap and a `-javaagent` refuse
it (JaCoCo itself was not tried); 3199 of 3268 classes of a run come from the cache.

How to redo them: a Gradle init script that writes the test runtime class path of `:core` to a file; a 20-line main that selects
test classes with the JUnit Launcher API; a jar of the test classes (a cache cannot be made from a class folder); caches in two
steps (`-XX:AOTMode=record -XX:AOTConfiguration=...`, then `-XX:AOTMode=create ...`, with
`-XX:+UnlockDiagnosticVMOptions -XX:-AOTRecordTraining` for no profiles); N interleaved runs per configuration under
`/usr/bin/time`, median and sd, the load average noted; `-XX:AOTMode=on` with `-Xlog:class+load` to count the classes served by
the cache. Run with `LC_NUMERIC=C`, not `LC_ALL=C` (`15-11-2026-1528-a-test-passes-in-the-c-locale`).

Decision: document first (guide entry "A JVM that runs the tests starts slowly", ranked first for `gabier help --for="make the
test JVM start faster"`). Follow-up, to open once `gabier test` is merged: an opt-in cache of the dependency jars, made and used
by the command that owns the test JVM, keyed by the JDK build and the path, size and date of each jar (JDK 25 and 26 do not
validate them), created once (about 5 s, 24 MB), used with `AOTMode=auto` so that a miss falls back silently.

Checked by the worker: full `bin/ci` green (test 319 s: cli 1655, core 927, daemon 1327, protocol 441, shared 236, architecture
24; native, jdtls; release-install and release-notes skipped).

Tokens:
- worker: 200110 (`subagent_tokens` of its last notification; the agent was resumed once, the figure is the total)
- reviewer: none (not reviewed)
- supervisor: covers the supervisor session from the launch of the worker (13:35 UTC), shared with ticket
  `15-11-2026-1434`, other work included: 18 calls, output 15297, input + cache write 39754 (cache reads 2119609); exchanges
  with the user: 1 message
