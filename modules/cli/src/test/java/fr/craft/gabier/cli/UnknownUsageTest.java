package fr.craft.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** The command lines that match no usage: the usage error on the standard error, nothing on the output, exit 2. */
class UnknownUsageTest extends CliTest {

    static Stream<List<String>> commandLines() {
        return Stream.of(
                        wordsThatAreNoCommand(),
                        diagnosticsWithoutExactlyOneFile(),
                        renamesThatNameNeitherAPositionNorAnIdentifierCompletely(),
                        renamesWithALineOrAColumnThatIsNotANumberFromOne(),
                        organizeImportsWithoutExactlyOneFile(),
                        extractVariableWithoutAFileTwoPositionsAndAName(),
                        extractVariableWithAPositionThatIsNotLineColonColumnFromOne(),
                        extractMethodWithoutAFileTwoPositionsAndAName(),
                        extractConstantWithoutAFileTwoPositionsAndAName(),
                        extractFieldWithoutAFileTwoPositionsAndAName(),
                        withoutAFileAndOnePosition("move-type-to-file"),
                        moveWithoutAFileAndAPackage(),
                        withoutAFileAndOnePosition("inline"),
                        createWithoutAFileAndAKind(),
                        referencesWithoutAFileAndOnePosition())
                .flatMap(List::stream);
    }

    private static List<List<String>> wordsThatAreNoCommand() {
        return List.of(
                List.of(),
                List.of("--bogus"),
                List.of("--version", "--bogus"),
                List.of("--bogus", "--version"),
                List.of("status", "stop"),
                List.of("A.java", "diagnostics"),
                List.of("diagnostics", "--dry-run", "A.java"));
    }

    private static List<List<String>> diagnosticsWithoutExactlyOneFile() {
        return List.of(List.of("diagnostics"), List.of("diagnostics", "A.java", "B.java"));
    }

    private static List<List<String>> renamesThatNameNeitherAPositionNorAnIdentifierCompletely() {
        return List.of(
                List.of("rename"),
                List.of("rename", "A.java", "3", "5"),
                List.of("rename", "A.java", "3", "5", "name", "more"),
                List.of("rename", "--dry-run", "A.java", "3", "name"),
                List.of("rename", "--token", "A.java", "name"),
                List.of("rename", "--token", "A.java", "id", "name", "more"),
                List.of("rename", "--token", "A.java", "3", "5", "name"));
    }

    private static List<List<String>> renamesWithALineOrAColumnThatIsNotANumberFromOne() {
        return Stream.of(
                        List.of("0", "5"),
                        List.of("3", "0"),
                        List.of("-1", "5"),
                        List.of("three", "5"),
                        List.of("3", "five"),
                        List.of("3", "1.5"))
                .map(position -> List.of("rename", "A.java", position.get(0), position.get(1), "name"))
                .toList();
    }

    private static List<List<String>> organizeImportsWithoutExactlyOneFile() {
        return List.of(
                List.of("organize-imports"),
                List.of("organize-imports", "--dry-run"),
                List.of("organize-imports", "A.java", "B.java"),
                List.of("organize-imports", "--dry-run", "A.java", "B.java"),
                List.of("organize-imports", "--token", "A.java"));
    }

    private static List<List<String>> extractVariableWithoutAFileTwoPositionsAndAName() {
        return List.of(
                List.of("extract-variable"),
                List.of("extract-variable", "--dry-run"),
                List.of("extract-variable", "A.java", "3:4", "3:9"),
                List.of("extract-variable", "A.java", "3:4", "3:9", "name", "more"),
                List.of("extract-variable", "--token", "A.java", "3:4", "3:9", "name"));
    }

    private static List<List<String>> extractVariableWithAPositionThatIsNotLineColonColumnFromOne() {
        return Stream.of(
                        List.of("3", "3:9"),
                        List.of("3:4", "9"),
                        List.of("3:4:5", "3:9"),
                        List.of("0:4", "3:9"),
                        List.of("3:0", "3:9"),
                        List.of("3:4", "-3:9"),
                        List.of("three:4", "3:9"),
                        List.of("3:4", "3:nine"),
                        List.of("3:4", "3:1.5"))
                .map(positions -> List.of("extract-variable", "A.java", positions.get(0), positions.get(1), "name"))
                .toList();
    }

    private static List<List<String>> extractMethodWithoutAFileTwoPositionsAndAName() {
        return List.of(
                List.of("extract-method"),
                List.of("extract-method", "--dry-run"),
                List.of("extract-method", "A.java", "3:4", "3:9"),
                List.of("extract-method", "A.java", "3:4", "3:9", "name", "more"),
                List.of("extract-method", "A.java", "3", "3:9", "name"));
    }

    private static List<List<String>> extractConstantWithoutAFileTwoPositionsAndAName() {
        return List.of(
                List.of("extract-constant"),
                List.of("extract-constant", "A.java", "3:4", "3:9"),
                List.of("extract-constant", "A.java", "3", "3:9", "NAME"));
    }

    private static List<List<String>> extractFieldWithoutAFileTwoPositionsAndAName() {
        return List.of(
                List.of("extract-field"),
                List.of("extract-field", "A.java", "3:4", "3:9"),
                List.of("extract-field", "A.java", "3", "3:9", "name"));
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
                List.of("references", "A.java", "3"),
                List.of("references", "A.java", "0:4"),
                List.of("references", "A.java", "3:four"),
                List.of("references", "--dry-run", "A.java", "3:4"),
                List.of("references", "--token", "A.java", "3:4"));
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
                List.of(command, "A.java", "3:4", "3:9"),
                List.of(command, "A.java", "3"),
                List.of(command, "A.java", "0:4"),
                List.of(command, "A.java", "3:four"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("commandLines")
    void aCommandLineThatMatchesNoUsageIsAnUnknownUsage(List<String> arguments) {
        assertThat(run(arguments.toArray(String[]::new))).isEqualTo(2);

        assertThat(out()).isEmpty();
        assertThat(err()).isEqualTo(usageText(String.join(" ", arguments)));
    }
}
