package eu.gabier.cli.application;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.cli.domain.DaemonRefusal;
import eu.gabier.protocol.Problem;
import eu.gabier.shared.error.domain.ErrorStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProblemsTest {

    private static DaemonRefusal refusal(String key) {
        return new DaemonRefusal(
                "urn:gabier:error:" + key,
                "A title",
                "What happened",
                key,
                Map.of("file", "A.java"),
                ErrorStatus.BAD_REQUEST);
    }

    @Test
    void turnsARefusalIntoTheProblemTheDaemonGave() {
        var problem = Problems.of(refusal("editing-invalid-range"));

        assertThat(problem)
                .isEqualTo(new Problem(
                        "urn:gabier:error:editing-invalid-range",
                        "A title",
                        "What happened",
                        "editing-invalid-range",
                        Map.of("file", "A.java"),
                        ErrorStatus.BAD_REQUEST));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "language-server-not-extractable",
                "language-server-not-movable",
                "language-server-invalid-name",
                "editing-no-such-code-action",
                "editing-unknown-clean-up"
            })
    void pointsToTheHelpOfTheCommandWhenTheOperationWasDeclined(String key) {
        assertThat(Problems.pointer(Problems.of(refusal(key)), "extract-method"))
                .contains("Usage and other ways: gabier help extract-method");
    }

    @ParameterizedTest
    @ValueSource(strings = {"editing-invalid-range", "language-server-not-ready", "operation-invalid-values"})
    void pointsToNothingForAnyOtherRefusal(String key) {
        assertThat(Problems.pointer(Problems.of(refusal(key)), "rename")).isEmpty();
    }
}
