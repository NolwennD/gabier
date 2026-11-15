package eu.gabier.daemon;

import eu.gabier.protocol.RanTests;
import eu.gabier.testing.application.RunTests;
import eu.gabier.testing.domain.TestReport;
import java.util.Map;
import java.util.Set;

/**
 * {@code test}: runs the given test classes in a JVM of their own, on the classes the language server compiled and its
 * class path of the tests, and gives what happened as {@link RanTests}. The parameters are {@code classes} (the binary
 * names, one per line, at least one, none blank) and {@code timeout} (the seconds the tests may take in all, a whole
 * number above zero). Tests that fail are a result, with their class, method, message and place; the refusals are those of
 * the language server that is not ready, and of the run: a Java file of the project that does not compile, a class the
 * build does not hold, a class no test engine recognises, a class path with no JUnit Platform launcher, a time limit that
 * passes, a JVM that ends with no report. They are answered as the problem objects they are, for the whole call.
 */
record TestMethod(RunTests runTests) implements DaemonMethod {

    @Override
    public String name() {
        return "test";
    }

    @Override
    public Set<String> parameters() {
        return Set.of(ChangeRequest.CLASSES, ChangeRequest.TIMEOUT);
    }

    @Override
    public Outcome call(Map<String, String> params) {
        var request = new ChangeRequest(name(), params);

        return Outcome.of(
                ranTests(runTests.run(request.classes(), request.timeLimit())).toJson());
    }

    /** What the core reports, as the protocol carries it. */
    static RanTests ranTests(TestReport report) {
        return new RanTests(
                report.passed(),
                report.skipped(),
                report.failures().stream()
                        .map(failure -> new RanTests.Failure(
                                failure.testClass(),
                                failure.method(),
                                failure.exception(),
                                failure.message(),
                                failure.where()))
                        .toList());
    }
}
