package eu.gabier.editing.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.editing.domain.FileChange;
import eu.gabier.editing.domain.TextEdit;
import eu.gabier.editing.fixtures.Scenario.Answer;
import eu.gabier.editing.fixtures.Scenario.Comparison;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * How much the scenarios of the core are coupled to jdtls, as a number that only goes down. The replay compares the
 * effect of an answer ({@link Comparison#EFFECT}), the severity and the position of a diagnostic, and only the titles
 * of the offered actions exactly; the values observed from jdtls that the fixtures still hold are counted too. A ticket
 * that raises a number says why in its issue and raises the ceiling here. The figures are written to {@code
 * build/jdtls-coupling.txt}, which the stage {@code test} of {@code bin/ci} prints.
 */
class JdtlsCouplingTest {

    /** The scenarios whose replay compares the exact answer of the server: the titles of the offered actions. */
    private static final int MOST_EXACT_COMPARISONS = 4;

    /** The messages of diagnostics the fixtures hold, written as the Eclipse compiler words them (not compared). */
    private static final int MOST_COMPILER_MESSAGES = 5;

    /** The titles of actions the fixtures hold, written as jdtls words them (compared exactly). */
    private static final int MOST_ACTION_TITLES = 3;

    /** The edits that write a new line with an indentation: the text the server's formatter decided. */
    private static final int MOST_INDENTED_EDITS = 26;

    private static final Pattern INDENTED_LINE = Pattern.compile("\n[ \t]");

    private static List<Scenario> everyScenario() {
        List<Scenario> scenarios = new ArrayList<>(Scenarios.all());
        Scenarios.sequels().forEach(sequel -> {
            scenarios.add(sequel.first());
            scenarios.add(sequel.second());
        });

        return scenarios;
    }

    private static long countOf(List<Scenario> scenarios, Comparison comparison) {
        return scenarios.stream()
                .filter(scenario -> scenario.answer().comparison() == comparison)
                .count();
    }

    private static long compilerMessages(List<Scenario> scenarios) {
        return scenarios.stream()
                .map(Scenario::answer)
                .filter(Answer.Diagnosed.class::isInstance)
                .map(Answer.Diagnosed.class::cast)
                .mapToLong(diagnosed -> diagnosed.diagnostics().size())
                .sum();
    }

    private static long actionTitles(List<Scenario> scenarios) {
        return scenarios.stream()
                .map(Scenario::answer)
                .filter(Answer.Offered.class::isInstance)
                .map(Answer.Offered.class::cast)
                .mapToLong(offered -> offered.offered().actions().size())
                .sum();
    }

    private static long indentedEdits(List<Scenario> scenarios) {
        return scenarios.stream()
                .map(Scenario::answer)
                .filter(Answer.Changing.class::isInstance)
                .map(Answer.Changing.class::cast)
                .flatMap(changing -> changing.changes().changes().stream())
                .filter(FileChange.EditFile.class::isInstance)
                .map(FileChange.EditFile.class::cast)
                .flatMap(edit -> edit.edits().stream())
                .map(TextEdit::newText)
                .filter(text -> INDENTED_LINE.matcher(text).find())
                .count();
    }

    @Test
    void keepsTheCouplingToJdtlsUnderItsCeilingAndShowsIt() throws IOException {
        List<Scenario> scenarios = everyScenario();
        long exact = countOf(scenarios, Comparison.EXACT);
        long messages = compilerMessages(scenarios);
        long titles = actionTitles(scenarios);
        long indented = indentedEdits(scenarios);
        long changeSets = scenarios.stream()
                .filter(scenario -> scenario.answer() instanceof Answer.Changing)
                .count();
        String report = """
            coupling of the scenarios to jdtls (%d scenarios)
              compared by their effect:                   %d (of which change sets: %d)
              compared by severity and position:          %d
              compared exactly (the server's own words):  %d (ceiling %d)
              compiler messages held, not compared:       %d (ceiling %d)
              action titles held, compared exactly:       %d (ceiling %d)
              edits that write an indented line:          %d (ceiling %d)
            """.formatted(
                        scenarios.size(),
                        countOf(scenarios, Comparison.EFFECT),
                        changeSets,
                        countOf(scenarios, Comparison.SEVERITY_AND_POSITION),
                        exact,
                        MOST_EXACT_COMPARISONS,
                        messages,
                        MOST_COMPILER_MESSAGES,
                        titles,
                        MOST_ACTION_TITLES,
                        indented,
                        MOST_INDENTED_EDITS);
        Files.writeString(Path.of("build/jdtls-coupling.txt"), report);
        System.out.print(report);

        assertThat(exact).as("scenarios compared exactly").isLessThanOrEqualTo(MOST_EXACT_COMPARISONS);
        assertThat(messages).as("compiler messages held").isLessThanOrEqualTo(MOST_COMPILER_MESSAGES);
        assertThat(titles).as("action titles held").isLessThanOrEqualTo(MOST_ACTION_TITLES);
        assertThat(indented).as("indented edits held").isLessThanOrEqualTo(MOST_INDENTED_EDITS);
    }

    @Test
    void comparesTheEffectOfAChangeSetTheSeverityAndPositionOfADiagnosticAndTheTitlesOfActionsExactly() {
        assertThat(Scenarios.RENAME_METHOD.answer().comparison()).isEqualTo(Comparison.EFFECT);
        assertThat(Scenarios.ORGANIZE_IMPORTS.answer().comparison()).isEqualTo(Comparison.EFFECT);
        assertThat(Scenarios.REFERENCES_OF_A_TYPE.answer().comparison()).isEqualTo(Comparison.EFFECT);
        assertThat(Scenarios.RENAME_TO_AN_INVALID_NAME.answer().comparison()).isEqualTo(Comparison.EFFECT);
        assertThat(Scenarios.FILE_WITH_AN_ERROR.answer().comparison()).isEqualTo(Comparison.SEVERITY_AND_POSITION);
        assertThat(CodeActionScenarios.all().stream()
                        .filter(scenario -> scenario.answer() instanceof Answer.Offered)
                        .map(scenario -> scenario.answer().comparison()))
                .isNotEmpty()
                .containsOnly(Comparison.EXACT);
    }
}
