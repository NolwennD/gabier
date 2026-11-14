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
