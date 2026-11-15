package eu.gabier.testing.domain;

/**
 * Runs test classes in a process of their own, apart from the daemon, and tells what happened: the adapter of a real
 * runner lives at the edge, and the tests of the core script a fake.
 */
public interface TestRunner {

    /**
     * Runs the classes. Tests that fail are a report, not an exception.
     *
     * @throws TestingException if a class has no test engine that recognises it (none of the classes is run), the time
     *     limit passes before the tests end, or the process ends without a report
     */
    TestReport run(TestRun run);
}
