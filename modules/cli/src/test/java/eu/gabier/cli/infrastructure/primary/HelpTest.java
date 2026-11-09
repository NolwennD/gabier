package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.Json;
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
            "actions",
            "apply-action",
            "start",
            "status",
            "stop",
            "init-agent",
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
    void theListSaysWhereARefusalPointsTo() {
        run("help");

        assertThat(out())
                .contains("A command whose operation is declined ends its refusal with the line that points to"
                        + " gabier help <command>,")
                .contains("an unknown command with gabier help --for=\"<task>\".");
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
                .contains("gabier [--json] rename [--dry-run] <file> <line>:<column> <new-name>")
                .contains("gabier [--json] rename [--dry-run] --token <file> <identifier> <new-name>")
                .contains("Example")
                .contains("$ gabier rename --dry-run src/main/java/demo/Greeter.java 6:19 welcome")
                .contains("Refusals")
                .contains("language-server-invalid-name");
    }

    @Test
    void helpOfRenameListsTheRefusalOfAPackageThatHoldsNoClass() {
        assertThat(run("help", "rename")).isZero();

        assertThat(out())
                .contains("language-server-empty-package: the package named up to the position holds no class")
                .contains("put the position on its last segment");
    }

    @Test
    void helpOfRenameSaysItRenamesAPackageFromItsPackageLineAndWhatItLeavesToATextEdit() {
        assertThat(run("help", "rename")).isZero();

        assertThat(out())
                .contains("Notes:")
                .contains("package")
                .contains("Sub-packages")
                .contains("another source set")
                .contains("module-info.java")
                .contains("string literals")
                .contains("build files");
    }

    @Test
    void helpOfCleanupSaysItAppliesToTheWholeFileAndHowToChangeOnePlaceOnly() {
        assertThat(run("help", "cleanup")).isZero();

        assertThat(out())
                .contains("applies to the whole file")
                .contains("for one place only")
                .contains("gabier actions")
                .contains("gabier apply-action")
                .contains("text block");
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
             move-type-to-file, move, inline, create, references, actions, apply-action, start, status, stop, init-agent,\
             help\
             (usage-error; command=frobnicate)\
            """ + System.lineSeparator() + FOR_A_TASK + System.lineSeparator());
    }

    @Test
    void helpOfAnUnknownCommandAsJsonIsTheProblemObject() {
        assertThat(run("--json", "help", "frobnicate")).isEqualTo(2);

        assertThat(err())
                .startsWith("{\"type\":\"urn:gabier:error:usage-error\"")
                .contains("\"parameters\":{\"command\":\"frobnicate\"}")
                .endsWith(",\"help\":\"" + CliTest.FOR_A_TASK_AS_JSON + "\"}" + System.lineSeparator());
    }

    @Test
    void helpOfTwoCommandsIsAUsageError() {
        assertThat(run("help", "rename", "stop")).isEqualTo(2);

        assertThat(err()).isEqualTo(usageText("help rename stop"));
    }

    @Test
    void dashDashHelpFollowedByAnotherArgumentIsAUsageError() {
        assertThat(run("--help", "rename")).isEqualTo(2);

        assertThat(err()).isEqualTo(unknownCommandText("--help rename"));
    }

    @Test
    void helpGuidePrintsTheGuideOnTheStandardOutputWithExitZero() {
        assertThat(run("help", "guide")).isZero();

        assertThat(err()).isEmpty();
        assertThat(out())
                .startsWith("# Improving Java with gabier")
                .contains("stringConcatToTextBlock")
                .contains("By hand: ")
                .endsWith(System.lineSeparator());
    }

    @Test
    void helpGuideAsJsonIsAnObjectWithTheTextOfTheGuide() {
        assertThat(run("--json", "help", "guide")).isZero();

        assertThat(out().lines()).hasSize(1);
        var guide = object(Json.parse(out()));
        assertThat(guide).containsOnlyKeys("name", "summary", "text");
        assertThat(guide.get("name")).isEqualTo("guide");
        assertThat((String) guide.get("summary")).contains("problem");
        assertThat((String) guide.get("text")).isEqualTo(GuideTopic.text());
    }

    @Test
    void theListPointsToTheGuide() {
        run("help");

        assertThat(out())
                .contains("gabier help guide")
                .contains("which problem each improvement of the code solves")
                .contains("gabier help --for=<task> gives the sections of that guide that answer a task");
    }

    @Test
    void helpOfTwoWordsAfterGuideIsAUsageError() {
        assertThat(run("help", "guide", "rename")).isEqualTo(2);

        assertThat(err()).isEqualTo(usageText("help guide rename"));
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
