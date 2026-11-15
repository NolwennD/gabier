package eu.gabier.testing.fixtures;

import eu.gabier.testing.domain.TestFailure;
import eu.gabier.testing.domain.TestReport;
import eu.gabier.testing.domain.TestingErrorKey;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The contract scenarios of the run of tests, replayed against the real runner (and the real language server that
 * builds the project) by the integration tests of the daemon. The projects are hand-written Eclipse projects with the
 * JUnit Jupiter jars of the integration tests on their class path: the main sources are in {@code src/main/java}, the
 * test sources in {@code src/test/java}.
 */
public final class TestRunScenarios {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Path TESTS = Path.of("src/test/java");

    private static final String COUNTER = """
        package app;

        public class Counter {
            private int count;

            public int next() {
                return ++count;
            }

            public void reset() {
                throw new IllegalStateException("not started");
            }
        }
        """;

    /** One test that passes and one that fails, in the code of the project the test calls. */
    public static final TestRunScenario ONE_PASSING_AND_ONE_FAILING_TEST = TestRunScenario.reporting(
            "one-passing-and-one-failing-test",
            Map.of(MAIN.resolve("app/Counter.java"), COUNTER, TESTS.resolve("app/CounterTest.java"), """
                package app;

                import org.junit.jupiter.api.Disabled;
                import org.junit.jupiter.api.Test;

                class CounterTest {

                    @Test
                    void countsFromOne() {
                        if (new Counter().next() != 1) {
                            throw new AssertionError("expected 1");
                        }
                    }

                    @Test
                    void resetsACounter() {
                        new Counter().reset();
                    }

                    @Disabled
                    @Test
                    void isNotReadyYet() {}
                }
                """),
            List.of("app.CounterTest"),
            new TestReport(
                    1,
                    1,
                    List.of(new TestFailure(
                            "app.CounterTest",
                            Optional.of("resetsACounter"),
                            "java.lang.IllegalStateException",
                            "not started",
                            Optional.of("app.Counter.reset(Counter.java:11)")))));

    /** A class of the project that holds no test: no engine recognises it. */
    public static final TestRunScenario A_CLASS_THAT_IS_NO_TEST = TestRunScenario.refused(
            "a-class-that-is-no-test",
            Map.of(MAIN.resolve("app/Counter.java"), COUNTER),
            List.of("app.Counter"),
            Duration.ofSeconds(60),
            TestingErrorKey.NO_TEST_ENGINE);

    /** A test that sleeps for ten minutes, with a time limit of a few seconds. */
    public static final TestRunScenario A_TEST_THAT_NEVER_ENDS = TestRunScenario.refused(
            "a-test-that-never-ends",
            Map.of(MAIN.resolve("app/Counter.java"), COUNTER, TESTS.resolve("app/SleepTest.java"), """
                package app;

                import org.junit.jupiter.api.Test;

                class SleepTest {

                    @Test
                    void sleeps() throws InterruptedException {
                        Thread.sleep(600_000);
                    }
                }
                """),
            List.of("app.SleepTest"),
            Duration.ofSeconds(5),
            TestingErrorKey.TEST_TIMEOUT);

    private TestRunScenarios() {}

    /** Every scenario, in the order they are replayed. */
    public static List<TestRunScenario> all() {
        return List.of(ONE_PASSING_AND_ONE_FAILING_TEST, A_CLASS_THAT_IS_NO_TEST, A_TEST_THAT_NEVER_ENDS);
    }
}
