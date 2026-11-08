package eu.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The guide is the page {@code docs/improve.md}, copied by the build into the resources of the cli: what it holds is
 * what an agent finds with {@code gabier help guide} when it has no clone. These tests keep its shape (one section per
 * situation, with the same lines) and the situations it must cover.
 */
class GuideTest {

    private static final Pattern IDENTIFIERS = Pattern.compile("The identifiers are those of jdtls:\\s*([^.]*)\\.");

    static Stream<String> sections() {
        return Arrays.stream(Guide.text().split("(?m)^### ")).skip(1).map(section -> "### " + section);
    }

    /** The identifiers of the clean-ups as {@code gabier help cleanup} lists them, so that this test follows that list. */
    static Stream<String> cleanUps() {
        var matcher = IDENTIFIERS.matcher(Cleanup.COMMAND.notes());
        assertThat(matcher.find())
                .as("the list of identifiers in the notes of cleanup")
                .isTrue();

        return Arrays.stream(matcher.group(1).split(",\\s*")).map(String::strip);
    }

    @Test
    void theGuideIsTheTextOfTheBuiltResource() {
        assertThat(Guide.text()).startsWith("# Improving Java with gabier").contains("gabier help guide");
    }

    @ParameterizedTest
    @MethodSource("sections")
    void everySectionHasItsWordsOnOneLineAndALimitsLine(String section) {
        var title = section.lines().findFirst().orElseThrow();

        assertThat(section.lines().filter(line -> line.startsWith("Words: ")))
                .as("the words of %s", title)
                .hasSize(1)
                .allSatisfy(line -> assertThat(line.length()).isGreaterThan("Words: ".length()));
        assertThat(section.lines().filter(line -> line.startsWith("Limits: ")))
                .as("the limits of %s", title)
                .hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("sections")
    void theWordsOfASectionAreOneLineBecauseTheRankingReadsNoOther(String section) {
        var title = section.lines().findFirst().orElseThrow();
        var lines = section.lines().toList();
        var words = lines.stream()
                .filter(line -> line.startsWith("Words: "))
                .findFirst()
                .orElseThrow();

        assertThat(lines.get(lines.indexOf(words) + 1))
                .as("the line after the words of %s", title)
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("sections")
    void everySectionSaysHowToAskOrThatItIsByHand(String section) {
        var title = section.lines().findFirst().orElseThrow();

        assertThat(section.lines().filter(line -> line.startsWith("Ask: ") || line.startsWith("By hand: ")))
                .as("the way to ask of %s", title)
                .hasSize(1);
    }

    @Test
    void noLineIsLongerThanOneHundredAndTwentyColumns() {
        assertThat(Guide.text().lines())
                .allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(120));
    }

    @ParameterizedTest
    @MethodSource("cleanUps")
    void everyCleanUpOfGabierHelpCleanupHasASectionThatAsksForIt(String cleanUp) {
        assertThat(sections()
                        .filter(section -> section.lines()
                                .anyMatch(line ->
                                        line.startsWith("Ask: `gabier cleanup --dry-run <file> " + cleanUp + "`"))))
                .as("the section of %s", cleanUp)
                .hasSize(1);
    }

    @Test
    void theCleanUpsAreReadFromTheNotesOfTheCommand() {
        assertThat(cleanUps())
                .hasSizeGreaterThan(10)
                .contains("stringConcatToTextBlock")
                .allSatisfy(id -> assertThat(id).matches("[a-zA-Z]+"));
    }

    @Test
    void theGuideStartsWithTheSituationsMetOnGabierItself() {
        var text = Guide.text();

        assertThat(text)
                .contains("### A concatenation that is not only literals")
                .contains("### Change one place, not the whole file")
                .contains("### Rename a component of a record")
                .contains("### The package of one source set");
        assertThat(text.indexOf("### A concatenation that is not only literals"))
                .isLessThan(text.indexOf("### A string built from literals with `+` and line breaks"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"rename", "organize-imports", "move", "references", "diagnostics", "create", "actions"})
    void everyCommandAskedForByItsTaskHasASectionThatAsksForIt(String command) {
        assertThat(sections())
                .as("a section whose Ask: line names gabier %s", command)
                .anySatisfy(section -> assertThat(section.lines())
                        .anyMatch(line -> line.startsWith("Ask: `gabier " + command + " ")
                                || line.startsWith("Ask: `gabier " + command + "`")));
    }

    @Test
    void aConcatenationThatIsNotOnlyLiteralsHasNoTextBlockButThreeActions() {
        var section = sectionOf("### A concatenation that is not only literals");

        assertThat(section)
                .contains("Use 'String.format' for string concatenation")
                .contains("Use 'StringBuilder' for string concatenation")
                .contains("Use 'MessageFormat' for string concatenation")
                .contains("nothing to change");
    }

    @Test
    void oneImprovementInOnePlaceGoesThroughActionsThenApplyAction() {
        var section = sectionOf("### Change one place, not the whole file");

        assertThat(section)
                .contains("gabier actions")
                .contains("gabier apply-action")
                .contains("Convert String concatenation to Text Block");
    }

    @Test
    void aRecordComponentSaysWhatJdtlsDoesWithACompactConstructor() {
        var section = sectionOf("### Rename a component of a record");

        assertThat(section).contains("compact constructor").contains("internal-server-error");
    }

    @Test
    void thePackageOfOneSourceSetIsASecondCall() {
        var section = sectionOf("### The package of one source set");

        assertThat(section).contains("second call").contains("src/test/java");
    }

    @Test
    void whatJdtlsDoesNotOfferIsSaidPlainlyAsByHand() {
        var text = Guide.text();

        assertThat(text)
                .contains("## What jdtls does not offer")
                .contains("### An `if` whose branches must trade places")
                .contains("### `if (c) return a; else return b;`")
                .contains("### Conditions to join or to split")
                .contains("### An `else` after a `return`");
        assertThat(sectionOf("### An `if` whose branches must trade places")).contains("By hand: ");
        assertThat(sectionOf("### Conditions to join or to split")).contains("By hand: ");
        assertThat(sectionOf("### A condition to turn round"))
                .contains("does nothing else")
                .contains("branches");
    }

    private static String sectionOf(String title) {
        return sections()
                .filter(section -> section.startsWith(title + "\n"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no section " + title));
    }
}
