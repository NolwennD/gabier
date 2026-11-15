package eu.gabier.testing.fixtures;

import eu.gabier.shared.error.domain.Assert;
import eu.gabier.testing.domain.TestReport;
import eu.gabier.testing.domain.TestRun;
import eu.gabier.testing.domain.TestRunner;
import eu.gabier.testing.domain.TestingException;
import java.util.ArrayList;
import java.util.List;

/**
 * A hand-written fake of {@link TestRunner}: no process. It gives the report a test scripted, as the real runner gives
 * the one of the tests it ran ({@link TestRunScenario} says which), or throws the refusal it was scripted with, and
 * keeps the runs it was asked for. It fails with an {@link AssertionError} when nothing was scripted.
 */
public final class FakeTestRunner implements TestRunner {

    private final List<TestRun> runs = new ArrayList<>();
    private TestReport report;
    private TestingException refusal;

    /** The report every run gives from now on. */
    public FakeTestRunner reporting(TestReport report) {
        Assert.notNull("report", report);

        this.report = report;
        this.refusal = null;

        return this;
    }

    /** The scenario's report is the one every run gives from now on. */
    public FakeTestRunner reporting(TestRunScenario scenario) {
        return reporting(scenario.report());
    }

    /** The refusal every run ends with from now on. */
    public FakeTestRunner refusing(TestingException refusal) {
        Assert.notNull("refusal", refusal);

        this.refusal = refusal;
        this.report = null;

        return this;
    }

    /** The runs that were asked for, in order. */
    public List<TestRun> runs() {
        return List.copyOf(runs);
    }

    @Override
    public TestReport run(TestRun run) {
        runs.add(run);
        if (refusal != null) {
            throw refusal;
        }
        if (report == null) {
            throw new AssertionError("The fake runner was not scripted: say what the real runner reports");
        }

        return report;
    }
}
