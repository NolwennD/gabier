package eu.gabier.daemon;

import eu.gabier.protocol.SelectedTests;
import eu.gabier.testing.application.AffectedTests;
import java.util.Map;

/**
 * {@code affected-tests-changed}: the tests that what changed on disk since the last answer affects, with no file given
 * (the other way to ask {@code affected-tests}). The daemon knows the Java files it was told of, by this or any other
 * request, and answers for them once: the changes are forgotten when the answer is made, and kept when the call is
 * refused. They are the daemon's, so two clients share them. The result and the refusals are those of {@code
 * affected-tests}, except that a Java file that was deleted, or is under no source folder, is left out and never refused.
 */
record AffectedTestsChangedMethod(AffectedTests affectedTests) implements DaemonMethod {

    @Override
    public String name() {
        return "affected-tests-changed";
    }

    @Override
    public Outcome call(Map<String, String> params) {
        return Outcome.of(new SelectedTests(affectedTests.affectedByChanges()).toJson());
    }
}
