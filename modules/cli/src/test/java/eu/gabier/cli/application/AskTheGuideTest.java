package eu.gabier.cli.application;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.cli.domain.Guide;
import eu.gabier.cli.domain.GuideSection;
import eu.gabier.cli.domain.ranking.Ranking;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AskTheGuideTest {

    private static final Guide GUIDE = new Guide(String.join(
            "\n",
            section("Rename a method", "rename symbol, change name, method", "Ask: `gabier rename`."),
            section("Rename a package", "rename symbol, change name, package", "Ask: `gabier rename`."),
            section("Remove an else", "unnecessary else, else after return", "By hand: remove the else."),
            section("Make a text block", "text block, multi-line string", "Ask: `gabier cleanup`."),
            section("Order the imports", "organize imports, sort imports", "Ask: `gabier organize-imports`."),
            section("Move a class", "move class, other package", "Ask: `gabier move`.")));

    private static String section(String title, String words, String how) {
        return "### " + title + "\n\nWords: " + words + "\n\n" + how + "\n";
    }

    @Test
    void givesTheSectionThatAnswersTheQuestionRankedFromOne() {
        var matches = AskTheGuide.answer("organize the imports of a file", GUIDE);

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().rank()).isEqualTo(1);
        assertThat(matches.getFirst().section().title()).isEqualTo("Order the imports");
        assertThat(matches.getFirst().section().byHand()).isFalse();
    }

    @Test
    void givesTheSectionsBestFirstWithTheRankOfEachInOrder() {
        var matches = AskTheGuide.answer("rename a symbol and change its name", GUIDE);

        assertThat(matches).extracting(GuideMatch::rank).isEqualTo(List.of(1, 2));
        assertThat(matches)
                .extracting(match -> match.section().title())
                .containsExactlyInAnyOrder("Rename a method", "Rename a package");
        assertThat(matches.get(0).confidence())
                .isGreaterThanOrEqualTo(matches.get(1).confidence());
    }

    @Test
    void givesTheConfidenceTheRankingGivesWithNothingAdded() {
        var sections = GUIDE.sections();
        var ranking = Ranking.of(sections.stream().map(GuideSection::document).collect(Collectors.toList()));

        var matches = AskTheGuide.answer("move a class to another package", GUIDE);

        var expected = ranking.answer("move a class to another package");
        assertThat(matches).isNotEmpty();
        assertThat(matches)
                .extracting(match -> match.confidence())
                .isEqualTo(expected.stream().map(match -> match.confidence()).toList());
        assertThat(matches)
                .extracting(match -> match.section().title())
                .isEqualTo(expected.stream()
                        .map(match -> sections.get(match.index()).title())
                        .toList());
    }

    @Test
    void givesNothingForAQuestionTheGuideDoesNotAnswer() {
        assertThat(AskTheGuide.answer("bake bread with sourdough", GUIDE)).isEmpty();
    }
}
