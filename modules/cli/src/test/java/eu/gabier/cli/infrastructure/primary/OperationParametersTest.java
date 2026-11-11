package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.gabier.cli.domain.Operation;
import eu.gabier.cli.domain.Operations;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The parameters of an operation are written in three places: its descriptor ({@link Operations}), the usage line of
 * its command and its row of the table of {@code docs/protocol.md}. They must name the same parameters. The check is a
 * function of the three, so that each of the ways to break it is shown.
 */
class OperationParametersTest {

    private static final String POSITION = "<line>:<column>";

    /** What the usage line writes for a parameter, and the parameters it stands for. */
    private static final Map<String, List<String>> PARAMETERS_OF_A_WORD = Map.ofEntries(
            Map.entry("[--dry-run]", List.of("dryRun")),
            Map.entry("[--include-declaration]", List.of("includeDeclaration")),
            Map.entry("<file>", List.of("file")),
            Map.entry("<file>...", List.of("files")),
            Map.entry("<new-name>", List.of("newName")),
            Map.entry("<name>", List.of("name")),
            Map.entry("<identifier>", List.of("token")),
            Map.entry("<cleanup>...", List.of("cleanUps")),
            Map.entry("<package>", List.of("package")),
            Map.entry("<title>", List.of("title")),
            Map.entry("<class|interface|record|enum|package>", List.of("kind")));

    /** The words of a usage line that choose a form of the command line or tune it, and are no parameter. */
    private static final Set<String> COMMAND_LINE_ONLY = Set.of("--token", "--all", "--wait", "[--timeout=<seconds>]");

    private static final Pattern BACKTICKED = Pattern.compile("`([^`]+)`");

    /** The words of the usage line of an operation, and of its row, that name a parameter the descriptor lacks, or the other way. */
    static List<String> disagreements(Operation operation, String usageLine, String parametersOfTheRow) {
        var problems = new ArrayList<String>();
        var inTheDescriptor = new TreeSet<>(operation.parameterNames());
        var inTheUsage = new TreeSet<String>();
        var positions = 0;
        // Each line starts with "gabier [--json] <command>".
        var words = usageLine
                .lines()
                .flatMap(line -> Stream.of(line.strip().split("\\s+")).skip(3))
                .toList();
        for (var word : words) {
            if (word.equals(POSITION)) {
                positions++;
            } else if (PARAMETERS_OF_A_WORD.containsKey(word)) {
                inTheUsage.addAll(PARAMETERS_OF_A_WORD.get(word));
            } else if (!COMMAND_LINE_ONLY.contains(word)) {
                problems.add(operation.method() + ": the usage line has a word that no parameter stands for: " + word);
            }
        }
        inTheUsage.addAll(
                switch (positions) {
                    case 0 -> List.of();
                    case 1 -> List.of("line", "column");
                    default -> List.of("startLine", "startColumn", "endLine", "endColumn");
                });
        var inTheRow = new TreeSet<String>();
        // What the row says in parentheses explains a parameter or the method; it names none.
        var cell = BACKTICKED.matcher(parametersOfTheRow.replaceAll("\\([^)]*\\)", ""));
        while (cell.find()) {
            inTheRow.add(cell.group(1));
        }
        compare(operation.method(), "the descriptor", inTheDescriptor, "the usage line", inTheUsage, problems);
        compare(
                operation.method(),
                "the descriptor",
                inTheDescriptor,
                "the row of docs/protocol.md",
                inTheRow,
                problems);

        return problems;
    }

    private static void compare(
            String method, String left, Set<String> inLeft, String right, Set<String> inRight, List<String> problems) {
        inLeft.stream()
                .filter(name -> !inRight.contains(name))
                .forEach(name -> problems.add(method + ": " + name + " is in " + left + " and not in " + right));
        inRight.stream()
                .filter(name -> !inLeft.contains(name))
                .forEach(name -> problems.add(method + ": " + name + " is in " + right + " and not in " + left));
    }

    private static Map<String, String> rowsOfTheTable() throws IOException {
        var rows = new LinkedHashMap<String, String>();
        for (var line : Files.readAllLines(Path.of(System.getProperty("gabier.docs.protocol")))) {
            if (line.startsWith("| `")) {
                var cells = line.split("\\|");
                rows.put(cells[1].strip().replace("`", ""), cells[2]);
            }
        }

        return rows;
    }

    /** The usage line of the operation, in the usage of the command that runs it. */
    static String usageLineOf(Command command, Operation operation) {
        var lines = command.usage().lines().toList();
        if (lines.size() == command.operations().size()) {
            return lines.get(command.operations().indexOf(operation));
        }
        assertThat(command.operations())
                .as(
                        "the lines of the usage of %s are one per operation, or it has one operation",
                        command.commandName())
                .hasSize(1);

        return command.usage();
    }

    static Stream<Command> commandsOfTheDaemon() {
        return Capabilities.all().stream()
                .map(Capability::command)
                .filter(command -> !command.operations().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("commandsOfTheDaemon")
    void theParametersOfAnOperationAreThoseOfItsUsageLineAndOfItsRow(Command command) throws IOException {
        var rows = rowsOfTheTable();

        for (var operation : command.operations()) {
            assertThat(rows)
                    .as("the table of docs/protocol.md has a row for %s", operation.method())
                    .containsKey(operation.method());
            assertThat(disagreements(operation, usageLineOf(command, operation), rows.get(operation.method())))
                    .isEmpty();
        }
    }

    @Test
    void everyMethodOfTheTableIsAnOperationAndTheOtherWayRound() throws IOException {
        assertThat(rowsOfTheTable().keySet())
                .containsExactlyElementsOf(
                        Operations.all().stream().map(Operation::method).toList());
    }

    @Test
    void everyOperationIsRunByACommand() {
        assertThat(commandsOfTheDaemon().flatMap(command -> command.operations().stream()))
                .containsExactlyInAnyOrderElementsOf(Operations.all());
    }

    @Nested
    class UsageLine {

        private static final String EXAMPLE = "$ gabier cut a";

        private Operation operation(String method) {
            return new Operation(method, "cut " + method, "", List.of(), List.of());
        }

        @Test
        void takesTheLineOfTheOperationWhenTheUsageHasOneLineForEach() {
            var command = Rename.COMMAND;

            assertThat(usageLineOf(command, Operations.RENAME))
                    .isEqualTo("gabier [--json] rename [--dry-run] <file> <line>:<column> <new-name>");
            assertThat(usageLineOf(command, Operations.RENAME_TOKEN))
                    .isEqualTo("gabier [--json] rename [--dry-run] --token <file> <identifier> <new-name>");
        }

        @Test
        void takesAllTheLinesOfTheUsageOfACommandThatHasOneOperation() {
            var command = Start.COMMAND;

            assertThat(command.usage().lines()).hasSize(2);
            assertThat(usageLineOf(command, Operations.START)).isEqualTo(command.usage());
        }

        @Test
        void refusesAUsageWhoseLinesAreNoneOfTheTwo() {
            var command = Command.called("cut")
                    .usage("""
                        gabier [--json] cut <file>
                        gabier [--json] cut --token <file>
                        gabier [--json] cut --all\
                        """)
                    .example(EXAMPLE)
                    .asksTheDaemon(operation("cut"), operation("cut-token"));

            assertThatThrownBy(() -> usageLineOf(command, command.operations().getFirst()))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining(
                            "the lines of the usage of cut are one per operation, or it has one operation");
        }
    }

    @Nested
    class Disagreement {

        private static final String USAGE = "gabier [--json] rename [--dry-run] <file> <line>:<column> <new-name>";
        private static final String ROW = " `file`, `line`, `column`, `newName`, `dryRun` ";

        private Operation without(String parameter) {
            var rename = Operations.RENAME;
            var kept = rename.parameters().stream()
                    .filter(candidate -> !candidate.name().equals(parameter))
                    .toList();

            return new Operation(rename.method(), rename.summary(), rename.notes(), rename.refusals(), kept);
        }

        @Test
        void saysNothingWhenTheThreeAgree() {
            assertThat(disagreements(Operations.RENAME, USAGE, ROW)).isEmpty();
        }

        @Test
        void tellsAParameterMissingFromTheDescriptor() {
            assertThat(disagreements(without("newName"), USAGE, ROW))
                    .containsExactly(
                            "rename: newName is in the usage line and not in the descriptor",
                            "rename: newName is in the row of docs/protocol.md and not in the descriptor");
        }

        @Test
        void tellsAParameterMissingFromTheUsageLine() {
            assertThat(disagreements(Operations.RENAME, USAGE.replace(" <new-name>", ""), ROW))
                    .containsExactly("rename: newName is in the descriptor and not in the usage line");
        }

        @Test
        void tellsAParameterMissingFromTheRow() {
            assertThat(disagreements(Operations.RENAME, USAGE, ROW.replace(" `newName`,", "")))
                    .containsExactly("rename: newName is in the descriptor and not in the row of docs/protocol.md");
        }

        @Test
        void tellsAPositionMissingFromTheUsageLine() {
            assertThat(disagreements(Operations.RENAME, USAGE.replace(" <line>:<column>", ""), ROW))
                    .containsExactly(
                            "rename: column is in the descriptor and not in the usage line",
                            "rename: line is in the descriptor and not in the usage line");
        }

        @Test
        void countsTheTwoPositionsOfARangeAsFourParameters() {
            var usage = "gabier [--json] actions <file> <line>:<column> <line>:<column>";
            var row = " `file`, `startLine`, `startColumn`, `endLine`, `endColumn` ";

            assertThat(disagreements(Operations.ACTIONS, usage, row)).isEmpty();
        }

        @Test
        void refusesAWordOfTheUsageLineThatNoParameterStandsFor() {
            assertThat(disagreements(Operations.RENAME, USAGE + " <mystery>", ROW))
                    .containsExactly("rename: the usage line has a word that no parameter stands for: <mystery>");
        }

        @Test
        void letsTheWordsOfTheCommandLineAloneBe() {
            assertThat(disagreements(Operations.START, "gabier [--json] start --wait [--timeout=<seconds>]", "none"))
                    .isEmpty();
        }
    }
}
