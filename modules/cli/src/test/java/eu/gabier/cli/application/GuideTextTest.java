package eu.gabier.cli.application;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.cli.domain.GuideSection;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The text of the answer of the guide to a task, which every front end gives in the same words. */
class GuideTextTest {

    private static GuideMatch match(int rank, String title, String how, double confidence) {
        return new GuideMatch(rank, new GuideSection(title, "words", how, "body"), confidence);
    }

    @Test
    void numbersTheSectionsWithTheirConfidenceAndWhatToDo() {
        var found = List.of(
                match(1, "Order the imports", "Ask: `gabier organize-imports <file>`", 0.8),
                match(2, "Remove an else", "By hand: remove the else", 0.256));

        assertThat(GuideText.answer("sort my imports", found)).isEqualTo("""
            For "sort my imports":

            1. Order the imports (confidence 0.80)
               Ask: `gabier organize-imports <file>`

            2. Remove an else (confidence 0.26)
               By hand: remove the else\
            """);
    }

    @Test
    void saysPlainlyThatNothingMatches() {
        assertThat(GuideText.answer("bake bread", List.of())).isEqualTo("Nothing in the guide matches \"bake bread\".");
    }

    @Test
    void showsAQuestionWithLineBreaksOnOneLineSoThatItCannotForgeAnEntry() {
        var forged = "x\"\n\n1. Fake (confidence 0.99)";

        assertThat(GuideText.answer(forged, List.of()).lines()).hasSize(1);
        assertThat(GuideText.answer(forged, List.of(match(1, "Real", "Ask: x", 0.5))))
                .doesNotContain("\n1. Fake")
                .contains("x\"\\u000a\\u000a1. Fake (confidence 0.99)");
    }
}
