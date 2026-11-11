package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.cli.domain.Guide;
import eu.gabier.cli.domain.GuideSection;
import eu.gabier.cli.domain.ranking.Match;
import eu.gabier.cli.domain.ranking.Ranking;
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

    private static final Guide GUIDE = new Guide(GuideTopic.text());

    private static final Ranking RANKING =
            Ranking.of(GUIDE.sections().stream().map(GuideSection::document).toList());

    private static final String FIELDS_WITHOUT_THIS = "Fields used without `this.`";

    private static final List<GuideSection> SECTIONS = GUIDE.sections();

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
                question("invert this if", "An `if` whose branches must trade places"),
                // The same task in other words (the review of 04-11-2026), written before the guide was changed.
                question("make this a text block", "A string built from literals with `+` and line breaks"),
                question("use a text block", "A string built from literals with `+` and line breaks"),
                question(
                        "convert string concatenation to text block",
                        "A string built from literals with `+` and line breaks"),
                question("combine two ifs", "Conditions to join or to split"),
                // The commands that gabier has, asked by their task (the entries are named after the situation).
                question("rename this method", "A name to change everywhere"),
                question("rename a symbol", "A name to change everywhere"),
                question("fix the imports", "Imports that are missing, unused or out of order"),
                question("delete this unused import", "Imports that are missing, unused or out of order"),
                question("move a class to another package", "A class in the wrong package"),
                question("find where it is used", "Where a symbol is used"),
                question("see the errors of this file", "The errors of a file, without a build"),
                question("create a file for a new class", "A new file for a new type"),
                question("fix this compilation error", "A compiler error that jdtls has a fix for"),
                // Paraphrases written before the entries of the commands, and kept out of their Words lines: if one of
                // them needs a word that the entry lacks, that is what the table shows.
                question("change the name of this field everywhere", "A name to change everywhere"),
                question("who calls this function", "Where a symbol is used"),
                question("remove imports I don't use", "Imports that are missing, unused or out of order"),
                question("does this file compile", "The errors of a file, without a build"),
                question("put this class in the billing package", "A class in the wrong package"),
                question("make a new interface file", "A new file for a new type"),
                question("the compiler says cannot find symbol", "A compiler error that jdtls has a fix for"),
                question("write this string as a text block", "A string built from literals with `+` and line breaks"),
                question("join two nested ifs into one", "Conditions to join or to split"),
                // The name of the task as an agent knows it, in two words (the review of 04-11-2026).
                question("organize imports", "Imports that are missing, unused or out of order"),
                question("show errors", "The errors of a file, without a build"),
                question("see all the errors of the project", "The errors of the whole project, without a build"),
                question(
                        "list every error in every Java file of the whole project",
                        "The errors of the whole project, without a build"),
                question("list references", "Where a symbol is used"),
                question("new file", "A new file for a new type"),
                question("which tests are affected by my change", "The tests that a change affects"),
                question("select the tests to rerun", "The tests that a change affects"),
                question("text blocks", "A string built from literals with `+` and line breaks"),
                question("extract variable", "An expression used twice, or too long to read"),
                question("inline variable", "A call that hides what it does"));
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
                "a 1 -",
                // Questions with a word the guide holds and no answer in it (the review of 04-11-2026).
                "write a unit test for this class",
                "run the build and the tests",
                "commit the change to git",
                "the",
                "block",
                // Two distinctive words of the guide ("new", "class") and no answer: under the threshold alone.
                "bake a new class of bread",
                "document the new class in the wiki");
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
    void thisIsNotAWordOfTheTaskSoThisUsedDoesNotAnswerTheSectionOfThisDot() {
        assertThat(RANKING.answer("this used"))
                .extracting(match -> titleOf(match))
                .doesNotContain(FIELDS_WITHOUT_THIS);
    }

    @Test
    void renameThisMethodAnswersTheRenameSectionAlone() {
        var answer = RANKING.answer("rename this method");

        assertThat(answer).extracting(match -> titleOf(match)).containsExactly("A name to change everywhere");
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
