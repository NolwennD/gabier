package eu.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.cli.ranking.Match;
import eu.gabier.cli.ranking.Ranking;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The table of questions that a person (or an agent) asks while working on Java, with the section of the guide that
 * answers each, as judged by reading the guide and not by running the ranking, and the rank it must have. The questions
 * come from the logbook and from the frictions met in this repository: six of the clean-ups and actions of the language,
 * two where the clean-up does not fit, and the structure moves and their limits.
 *
 * <p>The section is looked for among all the sections of the guide, so a rank of 1 means that the section is the
 * first one the ranking gives.
 */
class GuideQuestionsTest {

    private static final Ranking RANKING = Ranking.of(
            GuideSections.ofTheGuide().stream().map(GuideSection::document).toList());

    private static final List<GuideSection> SECTIONS = GuideSections.ofTheGuide();

    static Stream<Arguments> questions() {
        return Stream.of(
                // The language: clean-ups and actions.
                question(
                        "turn this string concatenation into a text block",
                        "A string built from literals with `+` and line breaks"),
                question(
                        "cleanup says nothing to change on a string built with calls and quotes",
                        "A concatenation that is not only literals"),
                question("remove the cast after instanceof", "A cast right after an `instanceof`"),
                question(
                        "replace this loop over an index with a for each",
                        "An index loop that only reads the elements"),
                question("this anonymous Comparator should be a lambda", "An anonymous class with one method"),
                question(
                        "the clean-up changed the whole file but I want only one place",
                        "Change one place, not the whole file"),
                question("this class only holds values, make it a record", "A class that only holds values"),
                question(
                        "a switch with a break in every case that only sets a variable",
                        "A `switch` statement that only assigns or returns"),
                // The structure.
                question("this long condition needs a name", "An expression used twice, or too long to read"),
                question("this method is too long, split it", "Statements that deserve a name"),
                question("replace this wrapper method by its body", "A call that hides what it does"),
                question("rename a record component", "Rename a component of a record"),
                question("rename the package, the tests keep the old package", "The package of one source set"),
                // What jdtls does not offer.
                question("invert this if", "An `if` whose branches must trade places"));
    }

    private static Arguments question(String question, String title) {
        return Arguments.of(question, title);
    }

    static Stream<String> questionsWithNoAnswer() {
        return Stream.of(
                "how do I bake bread",
                "deploy the application to a kubernetes cluster",
                "what is the weather today",
                "add a dependency in the gradle build",
                "open a pull request on github",
                "",
                "a 1 -");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("questions")
    void theSectionThatAnswersAQuestionIsTheFirstOneTheRankingGives(String question, String title) {
        var matches = RANKING.rank(question);

        assertThat(titleOf(matches.getFirst())).isEqualTo(title);
        assertThat(RANKING.answer(question)).isNotEmpty().first().isEqualTo(matches.getFirst());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("questionsWithNoAnswer")
    void aQuestionWithNoRelationToTheGuideGetsNoAnswer(String question) {
        assertThat(RANKING.answer(question)).isEmpty();
    }

    @Test
    void invertThisIfAnswersWithTheKnownLimitOfJdtlsWhichIsToDoItByHand() {
        var answer = RANKING.answer("invert this if");

        assertThat(SECTIONS.get(answer.getFirst().index()).byHand()).isTrue();
        assertThat(SECTIONS.get(answer.getFirst().index()).how()).contains("Invert conditions");
    }

    private static String titleOf(Match match) {
        return SECTIONS.get(match.index()).title();
    }
}
