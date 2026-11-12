package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.JsonRpc;
import eu.gabier.protocol.Problem;
import eu.gabier.protocol.SelectedTests;
import eu.gabier.shared.error.domain.ErrorStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** What a client that follows the tests is told: the tests that a change affects, and why a change could not be answered. */
class AffectedTestsUpdateTest {

    private static final Problem REFUSED = new Problem(
            "urn:gabier:error:testing-compilation-failed",
            "Compilation failed",
            "These files do not compile",
            "testing-compilation-failed",
            Map.of(),
            ErrorStatus.BAD_REQUEST);

    @Test
    void tellsNothingWhenNoTestIsAffectedAndNothingIsRefused() {
        assertThat(AffectedTestsUpdate.of(List.of()).notifications()).isEmpty();
    }

    @Test
    void tellsTheAffectedTestsInOneNotification() {
        var update = AffectedTestsUpdate.of(List.of("app.AnotherTest", "app.GreeterTest"));

        assertThat(update.notifications())
                .containsExactly(
                        JsonRpc.affectedTests(new SelectedTests(List.of("app.AnotherTest", "app.GreeterTest"))));
    }

    @Test
    void tellsTheRefusalInAnotherNotification() {
        assertThat(AffectedTestsUpdate.refused(REFUSED).notifications()).containsExactly(JsonRpc.refusal(REFUSED));
    }

    @Test
    void tellsTheTestsBeforeTheRefusalWhenThereAreBoth() {
        var update = AffectedTestsUpdate.of(List.of("app.GreeterTest")).then(AffectedTestsUpdate.refused(REFUSED));

        assertThat(update.notifications())
                .containsExactly(
                        JsonRpc.affectedTests(new SelectedTests(List.of("app.GreeterTest"))), JsonRpc.refusal(REFUSED));
    }

    @Test
    void putsTheTestsOfTwoUpdatesTogetherOnceEachInTheOrderOfTheNames() {
        var first = AffectedTestsUpdate.of(List.of("app.GreeterTest", "app.ZetaTest"));
        var second = AffectedTestsUpdate.of(List.of("app.AlphaTest", "app.GreeterTest"));

        assertThat(first.then(second).tests()).containsExactly("app.AlphaTest", "app.GreeterTest", "app.ZetaTest");
    }

    @Test
    void keepsTheLatestRefusalOnlyAndNoneOnceTheLatestUpdateWasAnswered() {
        var other = new Problem(
                "urn:gabier:error:testing-invalid-test-filter",
                "Invalid filter",
                "A filter is wrong",
                "testing-invalid-test-filter",
                Map.of(),
                ErrorStatus.BAD_REQUEST);

        assertThat(AffectedTestsUpdate.refused(REFUSED)
                        .then(AffectedTestsUpdate.refused(other))
                        .refusal())
                .contains(other);
        assertThat(AffectedTestsUpdate.refused(REFUSED)
                        .then(AffectedTestsUpdate.of(List.of("app.GreeterTest")))
                        .refusal())
                .isEmpty();
    }
}
