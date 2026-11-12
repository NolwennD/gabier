package eu.gabier.daemon;

import eu.gabier.protocol.ProblemMapper;
import eu.gabier.testing.application.AffectedTests;
import eu.gabier.testing.domain.TestingException;

/**
 * The tests that what changed since the last answer affects, as a client that follows them is told: the same answer as
 * {@code affected-tests-changed}, except that a refusal of the testing context (a changed file that does not compile, a
 * filter that is not a regular expression) is an update and not a failure, so that following goes on: the changes are
 * kept ({@link AffectedTests#affectedByChanges}), and the next read, after the next change on disk, lists their tests
 * once they can be told. What is not a refusal of the testing context (the language server is not ready, jdtls died)
 * fails the read.
 */
record FollowedTests(AffectedTests affectedTests) {

    /** The answer for the changes now, which uses them up when it lists their tests. */
    AffectedTestsUpdate read() {
        try {
            return AffectedTestsUpdate.of(affectedTests.affectedByChanges());
        } catch (TestingException refused) {
            return AffectedTestsUpdate.refused(ProblemMapper.toProblem(refused));
        }
    }
}
