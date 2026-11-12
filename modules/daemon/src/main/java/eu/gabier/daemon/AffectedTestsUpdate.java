package eu.gabier.daemon;

import eu.gabier.protocol.JsonRpc;
import eu.gabier.protocol.Problem;
import eu.gabier.protocol.SelectedTests;
import eu.gabier.shared.error.domain.Assert;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * What a client that follows the tests is told after the project was read: the tests that the changes since the last
 * answer affect, and, when the changes could not be answered (a file that does not compile), why. Unlike the problems of
 * the project, an update is something that happened, not the state of the project: the changes it covers are used up, so
 * two updates that the writer has not taken yet are put together ({@link #then}), never one replaced by the other.
 *
 * @param tests the binary names of the tests, in the order of the names, none when no test is affected
 * @param refusal why the changes could not be answered, which keeps them for the next read
 */
record AffectedTestsUpdate(List<String> tests, Optional<Problem> refusal) {

    AffectedTestsUpdate {
        Assert.field("tests", tests).notNull().noNullElement();
        Assert.notNull("refusal", refusal);

        tests = List.copyOf(tests);
    }

    /** The tests the changes affect, which may be none. */
    static AffectedTestsUpdate of(List<String> tests) {
        return new AffectedTestsUpdate(tests, Optional.empty());
    }

    /** The changes could not be answered for this reason: they are kept. */
    static AffectedTestsUpdate refused(Problem reason) {
        return new AffectedTestsUpdate(List.of(), Optional.of(reason));
    }

    /**
     * What a client that has not been told this update is to be told once the later one comes too: the tests of both,
     * once each, and the refusal of the later one only (a later answer is the sign that the files compile now).
     */
    AffectedTestsUpdate then(AffectedTestsUpdate later) {
        var together = new TreeSet<>(tests);
        together.addAll(later.tests);

        return new AffectedTestsUpdate(new ArrayList<>(together), later.refusal);
    }

    /** The lines to write for this update: the tests, then the refusal; none when it tells nothing. */
    List<String> notifications() {
        var lines = new ArrayList<String>();
        if (!tests.isEmpty()) {
            lines.add(JsonRpc.affectedTests(new SelectedTests(tests)));
        }
        refusal.ifPresent(reason -> lines.add(JsonRpc.refusal(reason)));

        return lines;
    }
}
