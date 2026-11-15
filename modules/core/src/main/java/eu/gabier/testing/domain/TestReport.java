package eu.gabier.testing.domain;

import eu.gabier.shared.error.domain.Assert;
import java.util.List;

/**
 * What a run of tests gave: how many passed, how many were skipped, and the failures. A test that ran is a test that
 * passed or failed; a test that was disabled, or whose assumption did not hold, is skipped and did not run.
 *
 * @param passed how many tests passed
 * @param skipped how many tests were skipped
 * @param failures the tests that failed, and the classes that failed before their tests could run, in the order they
 *     finished
 */
public record TestReport(int passed, int skipped, List<TestFailure> failures) {

    public TestReport {
        Assert.field("passed", passed).min(0);
        Assert.field("skipped", skipped).min(0);
        Assert.field("failures", failures).notNull().noNullElement();

        failures = List.copyOf(failures);
    }

    /** What a run that ran nothing gives. */
    public static TestReport nothingRan() {
        return new TestReport(0, 0, List.of());
    }

    /** How many tests failed. */
    public int failed() {
        return failures.size();
    }

    /** How many tests ran: those that passed and those that failed. */
    public int ran() {
        return passed + failed();
    }
}
