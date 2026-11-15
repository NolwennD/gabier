package eu.gabier.daemon;

import eu.gabier.testing.application.RunTests;
import java.util.Map;
import java.util.Set;

/**
 * {@code test-changed}: runs the tests that what changed on disk since the last answer affects, with no class given
 * (the other way to ask {@code test}). The selection is that of {@code affected-tests-changed}, and so are the changes it
 * remembers: they are forgotten once the tests ran, whether they passed or not, and kept when the call is refused. The
 * parameter is {@code timeout}; the result and the refusals are those of {@code test}. No test affected answers a result
 * with no test and starts no JVM.
 */
record TestChangedMethod(RunTests runTests) implements DaemonMethod {

    @Override
    public String name() {
        return "test-changed";
    }

    @Override
    public Set<String> parameters() {
        return Set.of(ChangeRequest.TIMEOUT);
    }

    @Override
    public Outcome call(Map<String, String> params) {
        var request = new ChangeRequest(name(), params);

        return Outcome.of(TestMethod.ranTests(runTests.runAffectedByChanges(request.timeLimit()))
                .toJson());
    }
}
