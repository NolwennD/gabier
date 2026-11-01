package fr.craft.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.protocol.Json;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class HelpTest extends CliTest {

    private static final List<String> COMMANDS = List.of(
            "diagnostics",
            "rename",
            "organize-imports",
            "cleanup",
            "extract-variable",
            "extract-method",
            "extract-constant",
            "extract-field",
            "move-type-to-file",
            "move",
            "inline",
            "create",
            "references",
            "status",
            "stop",
            "help");

    private static Map<String, Object> object(Object json) {
        var members = new LinkedHashMap<String, Object>();
        ((Map<?, ?>) json).forEach((name, value) -> members.put((String) name, value));

        return members;
    }

    private static List<Map<String, Object>> objects(Object json) {
        return ((List<?>) json).stream().map(HelpTest::object).toList();
    }

    private static List<String> strings(Object json) {
        return ((List<?>) json).stream().map(String.class::cast).toList();
    }

    @ParameterizedTest
    @ValueSource(strings = {"help", "--help", "-h"})
    void helpHelpDashDashHelpAndDashHListTheCommandsOnTheStandardOutputWithExitZero(String spelling) {
        assertThat(run(spelling)).isZero();

        assertThat(err()).isEmpty();
        assertThat(out().lines().map(String::strip).map(line -> line.split(" ")[0]))
                .containsAll(COMMANDS);
    }

    @Test
    void theListGivesOneLineAndTheProtocolVersionPointerToHelpOfACommand() {
        run("help");

        assertThat(out())
                .contains("gabier help <command>")
                .contains("diagnostics ")
                .contains("the errors and warnings of a Java file");
    }

    @Test
    void theListAsJsonIsAnObjectWithEveryCommand() {
        assertThat(run("--json", "help")).isZero();

        assertThat(out().lines()).hasSize(1);
        assertThat(err()).isEmpty();
        var commands = objects(object(Json.parse(out())).get("commands"));
        assertThat(commands).extracting(command -> (String) command.get("name")).containsExactlyElementsOf(COMMANDS);
        assertThat(commands)
                .allSatisfy(command -> assertThat(command)
                        .containsOnlyKeys("name", "summary", "usage")
                        .allSatisfy((key, value) -> assertThat((String) value).isNotBlank()));
    }

    @Test
    void dashDashHelpAsJsonIsTheSameObject() {
        run("help", "--json");
        var help = out();
        out.reset();

        run("--json", "-h");

        assertThat(out()).isEqualTo(help);
    }

    @Test
    void helpOfACommandGivesTheUsageAnExampleAndTheRefusals() {
        assertThat(run("help", "rename")).isZero();

        assertThat(err()).isEmpty();
        assertThat(out())
                .contains("gabier [--json] rename [--dry-run] <file> <line> <column> <new-name>")
                .contains("gabier [--json] rename [--dry-run] --token <file> <identifier> <new-name>")
                .contains("Example")
                .contains("$ gabier rename --dry-run src/main/java/demo/Greeter.java 6 19 welcome")
                .contains("Refusals")
                .contains("language-server-invalid-name");
    }

    static Stream<String> commandNames() {
        return Capabilities.all().stream()
                .map(capability -> capability.command().commandName());
    }

    @ParameterizedTest
    @MethodSource("commandNames")
    void helpOfEveryCommandIsAvailable(String command) {
        assertThat(run("help", command)).isZero();

        assertThat(out()).contains("gabier [--json] " + command);
    }

    @Test
    void helpOfACommandAsJsonIsAnObjectWithItsFields() {
        assertThat(run("help", "--json", "extract-variable")).isZero();

        var help = object(Json.parse(out()));
        assertThat(help).containsOnlyKeys("name", "summary", "usage", "example", "refusals");
        assertThat(help.get("name")).isEqualTo("extract-variable");
        assertThat((String) help.get("usage")).contains("extract-variable [--dry-run] <file>");
        assertThat((String) help.get("example")).startsWith("$ gabier extract-variable");
        assertThat(strings(help.get("refusals")))
                .anySatisfy(refusal -> assertThat(refusal).startsWith("language-server-not-extractable"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"extract-variable", "extract-method", "extract-constant", "extract-field"})
    void helpOfTheFourExtractionsListsTheSameRefusalOfASelectionThatCannotBeExtracted(String command) {
        assertThat(run("help", "--json", command)).isZero();

        assertThat(strings(object(Json.parse(out())).get("refusals")))
                .contains("language-server-not-extractable: the selection is not an expression or statements that can"
                        + " be extracted, and jdtls does not say why")
                .noneSatisfy(refusal -> assertThat(refusal).startsWith("language-server-not-an-expression"));
    }

    @Test
    void helpOfACommandWithNotesAsJsonHasThemAndTheOthersDoNot() {
        assertThat(run("help", "--json", "move-type-to-file")).isZero();

        var help = object(Json.parse(out()));
        assertThat(help).containsOnlyKeys("name", "summary", "usage", "example", "notes", "refusals");
        assertThat((String) help.get("notes")).contains("@Nested").contains("extend the shared base class");
    }

    @Test
    void helpOfAnUnknownCommandIsAUsageErrorThatListsTheKnownOnes() {
        assertThat(run("help", "frobnicate")).isEqualTo(2);

        assertThat(out()).isEmpty();
        assertThat(err()).startsWith("""
            Usage error: Unknown command; the commands are diagnostics, rename, organize-imports,\
             cleanup, extract-variable, extract-method, extract-constant, extract-field,\
             move-type-to-file, move, inline, create, references, status, stop, help\
             (usage-error; command=frobnicate)\
            """);
    }

    @Test
    void helpOfAnUnknownCommandAsJsonIsTheProblemObject() {
        assertThat(run("--json", "help", "frobnicate")).isEqualTo(2);

        assertThat(err())
                .startsWith("{\"type\":\"urn:gabier:error:usage-error\"")
                .contains("\"parameters\":{\"command\":\"frobnicate\"}");
    }

    @Test
    void helpOfTwoCommandsIsAUsageError() {
        assertThat(run("help", "rename", "stop")).isEqualTo(2);

        assertThat(err()).isEqualTo(usageText("help rename stop"));
    }

    @Test
    void dashDashHelpFollowedByAnotherArgumentIsAUsageError() {
        assertThat(run("--help", "rename")).isEqualTo(2);

        assertThat(err()).isEqualTo(usageText("--help rename"));
    }

    @Test
    void helpNeverStartsADaemonNorOpensTheSocket() throws IOException {
        var marker = directory.resolve("started");
        var bin = directory.resolve("bin");
        FakeDaemonScripts.install(bin, "echo started > '" + marker + "'");
        var socket = directory.resolve("gabier.sock");
        var environment = Map.of("GABIER_SOCKET", socket.toString(), "PATH", bin.toString());

        assertThat(run(environment, "help")).isZero();
        assertThat(run(environment, "help", "diagnostics")).isZero();
        assertThat(run(environment, "--help")).isZero();
        assertThat(run(environment, "-h")).isZero();

        assertThat(marker).doesNotExist();
        assertThat(socket).doesNotExist();
    }
}
