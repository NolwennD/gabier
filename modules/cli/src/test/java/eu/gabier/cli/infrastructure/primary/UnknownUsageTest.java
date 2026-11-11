package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** The command lines that match no usage: the usage error on the standard error, nothing on the output, exit 2. */
class UnknownUsageTest extends CliTest {

    static Stream<List<String>> commandLines() {
        return Stream.of(
                        wordsOfACommandThatDoNotMatchItsUsage(),
                        diagnosticsWithoutAFile(),
                        renamesThatNameNeitherAPositionNorAnIdentifierCompletely(),
                        organizeImportsWithoutExactlyOneFile(),
                        cleanupWithoutAFileAndACleanUp(),
                        extractVariableWithoutAFileTwoPositionsAndAName(),
                        extractMethodWithoutAFileTwoPositionsAndAName(),
                        extractConstantWithoutAFileTwoPositionsAndAName(),
                        extractFieldWithoutAFileTwoPositionsAndAName(),
                        withoutAFileAndOnePosition("move-type-to-file"),
                        moveWithoutAFileAndAPackage(),
                        withoutAFileAndOnePosition("inline"),
                        createWithoutAFileAndAKind(),
                        referencesWithoutAFileAndOnePosition(),
                        actionsWithoutAFileAndTwoPositions(),
                        applyActionWithoutAFileTwoPositionsAndATitle())
                .flatMap(List::stream);
    }

    static Stream<List<String>> wordsThatAreNoCommand() {
        return Stream.of(
                List.of(),
                List.of("--bogus"),
                List.of("--version", "--bogus"),
                List.of("--bogus", "--version"),
                List.of("A.java", "diagnostics"));
    }

    private static List<List<String>> wordsOfACommandThatDoNotMatchItsUsage() {
        return List.of(List.of("status", "stop"), List.of("diagnostics", "--dry-run", "A.java"));
    }

    private static List<List<String>> diagnosticsWithoutAFile() {
        return List.of(List.of("diagnostics"));
    }

    private static List<List<String>> renamesThatNameNeitherAPositionNorAnIdentifierCompletely() {
        return List.of(
                List.of("rename"),
                List.of("rename", "A.java", "3:5", "name", "more"),
                List.of("rename", "A.java", "3:5"),
                List.of("rename", "--token", "A.java", "name"),
                List.of("rename", "--token", "A.java", "id", "name", "more"),
                List.of("rename", "--token", "A.java", "3", "5", "name"));
    }

    private static List<List<String>> organizeImportsWithoutExactlyOneFile() {
        return List.of(
                List.of("organize-imports"),
                List.of("organize-imports", "--dry-run"),
                List.of("organize-imports", "A.java", "B.java"),
                List.of("organize-imports", "--dry-run", "A.java", "B.java"),
                List.of("organize-imports", "--token", "A.java"));
    }

    private static List<List<String>> cleanupWithoutAFileAndACleanUp() {
        return List.of(
                List.of("cleanup"),
                List.of("cleanup", "--dry-run"),
                List.of("cleanup", "A.java"),
                List.of("cleanup", "--dry-run", "A.java"));
    }

    private static List<List<String>> extractVariableWithoutAFileTwoPositionsAndAName() {
        return List.of(
                List.of("extract-variable"),
                List.of("extract-variable", "--dry-run"),
                List.of("extract-variable", "A.java", "3:4", "3:9"),
                List.of("extract-variable", "A.java", "3:4", "3:9", "name", "more"),
                List.of("extract-variable", "--token", "A.java", "3:4", "3:9", "name"));
    }

    private static List<List<String>> extractMethodWithoutAFileTwoPositionsAndAName() {
        return List.of(
                List.of("extract-method"),
                List.of("extract-method", "--dry-run"),
                List.of("extract-method", "A.java", "3:4", "3:9"),
                List.of("extract-method", "A.java", "3:4", "3:9", "name", "more"));
    }

    private static List<List<String>> extractConstantWithoutAFileTwoPositionsAndAName() {
        return List.of(List.of("extract-constant"), List.of("extract-constant", "A.java", "3:4", "3:9"));
    }

    private static List<List<String>> extractFieldWithoutAFileTwoPositionsAndAName() {
        return List.of(List.of("extract-field"), List.of("extract-field", "A.java", "3:4", "3:9"));
    }

    private static List<List<String>> createWithoutAFileAndAKind() {
        return List.of(
                List.of("create"),
                List.of("create", "--dry-run"),
                List.of("create", "A.java"),
                List.of("create", "A.java", "class", "record"));
    }

    private static List<List<String>> referencesWithoutAFileAndOnePosition() {
        return List.of(
                List.of("references"),
                List.of("references", "--include-declaration"),
                List.of("references", "A.java"),
                List.of("references", "A.java", "3:4", "3:9"),
                List.of("references", "--dry-run", "A.java", "3:4"),
                List.of("references", "--token", "A.java", "3:4"));
    }

    private static List<List<String>> actionsWithoutAFileAndTwoPositions() {
        return List.of(
                List.of("actions"),
                List.of("actions", "A.java"),
                List.of("actions", "A.java", "3:4"),
                List.of("actions", "A.java", "3:4", "3:9", "3:10"),
                List.of("actions", "--dry-run", "A.java", "3:4", "3:9"));
    }

    private static List<List<String>> applyActionWithoutAFileTwoPositionsAndATitle() {
        return List.of(
                List.of("apply-action"),
                List.of("apply-action", "--dry-run"),
                List.of("apply-action", "A.java", "3:4", "3:9"),
                List.of("apply-action", "A.java", "3:4", "3:9", "title", "more"),
                List.of("apply-action", "--token", "A.java", "3:4", "3:9", "title"));
    }

    private static List<List<String>> moveWithoutAFileAndAPackage() {
        return List.of(
                List.of("move"),
                List.of("move", "--dry-run"),
                List.of("move", "A.java"),
                List.of("move", "--dry-run", "A.java"));
    }

    private static List<List<String>> withoutAFileAndOnePosition(String command) {
        return List.of(
                List.of(command),
                List.of(command, "--dry-run"),
                List.of(command, "A.java"),
                List.of(command, "A.java", "3:4", "3:9"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("commandLines")
    void aCommandLineThatMatchesNoUsageIsAnUnknownUsage(List<String> arguments) {
        assertThat(run(arguments.toArray(String[]::new))).isEqualTo(2);

        assertThat(out()).isEmpty();
        assertThat(err()).isEqualTo(usageText(String.join(" ", arguments)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wordsThatAreNoCommand")
    void aFirstWordThatNamesNoCommandPointsToTheGuideOnTheStandardError(List<String> arguments) {
        assertThat(run(arguments.toArray(String[]::new))).isEqualTo(2);

        assertThat(out()).isEmpty();
        assertThat(err()).isEqualTo(unknownCommandText(String.join(" ", arguments)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wordsThatAreNoCommand")
    void aFirstWordThatNamesNoCommandPointsToTheGuideInAFieldOfTheJsonAnswer(List<String> arguments) {
        var withJson = Stream.concat(Stream.of("--json"), arguments.stream()).toArray(String[]::new);

        assertThat(run(withJson)).isEqualTo(2);

        assertThat(err()).isEqualTo(unknownCommandJson(String.join(" ", arguments)));
    }

    @Test
    void aCommandWithTheWrongArgumentsDoesNotPointToTheGuide() {
        run("diagnostics");

        assertThat(err()).doesNotContain("--for");
    }
}
