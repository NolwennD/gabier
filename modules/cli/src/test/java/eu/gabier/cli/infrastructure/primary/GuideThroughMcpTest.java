package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.cli.Gabier;
import eu.gabier.cli.domain.Guide;
import eu.gabier.cli.domain.GuideSection;
import eu.gabier.cli.domain.Operations;
import eu.gabier.cli.domain.Parameter;
import eu.gabier.protocol.Json;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * The {@code guide} tool of {@code gabier mcp} and {@code gabier help --for} answer from the same guide, the page of the
 * improvements, in the same words: a guide entry serves both. The tools the guide names are tools of the server.
 */
class GuideThroughMcpTest extends CliTest {

    private static final String META = """
        "_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}\
        """;

    private static final String TEXT_BLOCK = "turn this string concatenation into a text block";

    /** A command run as {@code gabier <command>}, in a text. */
    private static final Pattern INVOCATION = Pattern.compile("\\bgabier ([a-z][a-z-]*)\\b");

    /** A boolean parameter set to true, as the answers of the guide write the options of the command line. */
    private static final Pattern SET_TO_TRUE = Pattern.compile("\\b([a-z][A-Za-z]*)=true\\b");

    private static String guideCall(int id, String task) {
        return """
            {"jsonrpc":"2.0","id":%d,"method":"tools/call","params":{"name":"guide","arguments":{"task":%s},%s}}\
            """.formatted(id, Json.quote(task), META);
    }

    /** What {@code gabier mcp} answers to these lines, one JSON value for each answer; no daemon is asked. */
    private List<Object> mcp(String... lines) {
        out.reset();
        var exit = Gabier.run(
                new String[] {"mcp"},
                Map.of("GABIER_SOCKET", socket().toString()),
                new ByteArrayInputStream((String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8)),
                printStream(out),
                printStream(err));
        assertThat(exit).isZero();

        return out().lines().map(Json::parse).toList();
    }

    @SuppressWarnings("unchecked") // The server writes JSON objects, and the tests know where.
    private static String text(Object answer) {
        var result = (Map<String, Object>) ((Map<String, Object>) answer).get("result");
        var content = (List<Map<String, Object>>) result.get("content");

        return (String) content.getFirst().get("text");
    }

    @SuppressWarnings("unchecked") // The server lists its tools as an array of objects.
    private List<String> listedTools() {
        var list = """
            {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{%s}}\
            """.formatted(META);
        var result = (Map<String, Object>) ((Map<String, Object>) mcp(list).getFirst()).get("result");

        return ((List<Map<String, Object>>) result.get("tools"))
                .stream().map(tool -> (String) tool.get("name")).toList();
    }

    @Test
    void answersATaskInTheWordsOfHelpForWithTheCommandsWrittenAsTools() {
        run("help", "--for=" + TEXT_BLOCK);
        var theCommandLine = out().stripTrailing();
        var pointer = "\n\n`gabier help guide` prints the whole guide.";

        var theTool = text(mcp(guideCall(1, TEXT_BLOCK)).getFirst());

        assertThat(theCommandLine).endsWith(pointer);
        assertThat(theCommandLine).contains("Ask: `gabier cleanup --dry-run <file> stringConcatToTextBlock`");
        assertThat(theTool)
                .isEqualTo(theCommandLine
                        .substring(0, theCommandLine.length() - pointer.length())
                        .replace("`gabier cleanup --dry-run ", "`cleanup dryRun=true ")
                        .replace("`gabier ", "`"))
                .contains("Ask: `cleanup dryRun=true <file> stringConcatToTextBlock`")
                .doesNotContain("--dry-run");
    }

    @Test
    void answersATaskTheGuideDoesNotKnowAsHelpForDoesWithNothingMatching() {
        run("help", "--for=how do I bake bread");
        var firstLine = out().lines().findFirst().orElseThrow();

        var theTool = mcp(guideCall(1, "how do I bake bread"));

        assertThat(text(theTool.getFirst())).isEqualTo(firstLine);
        assertThat(((Map<?, ?>) ((Map<?, ?>) theTool.getFirst()).get("result")).get("isError"))
                .isEqualTo(false);
    }

    @Test
    void everyCommandTheGuideNamesInWhatToDoIsAToolOfTheServer() {
        var commands = Capabilities.all().stream()
                .map(capability -> capability.command().commandName())
                .toList();
        var tools = listedTools();

        var named = new Guide(GuideTopic.text())
                .sections().stream()
                        .flatMap(section -> INVOCATION.matcher(section.how()).results())
                        .map(match -> match.group(1))
                        .filter(commands::contains)
                        .distinct()
                        .toList();

        assertThat(named).isNotEmpty();
        assertThat(named).isSubsetOf(tools);
    }

    /** What {@code gabier mcp} answers to the task of each section of the guide: its title and its words. */
    private List<Object> answersToEverySection(List<GuideSection> sections) {
        var calls = IntStream.range(0, sections.size())
                .mapToObj(index -> guideCall(
                        index + 1,
                        sections.get(index).title() + " " + sections.get(index).words()))
                .toArray(String[]::new);

        return mcp(calls);
    }

    @Test
    void writesNoCommandOfTheCommandLineInTheAnswerToAnySituationOfTheGuide() {
        var commands = Capabilities.all().stream()
                .map(capability -> capability.command().commandName())
                .toList();
        var sections = new Guide(GuideTopic.text()).sections();

        var answers = answersToEverySection(sections);

        assertThat(answers).hasSameSizeAs(sections);
        assertThat(answers)
                .allSatisfy(
                        answer -> assertThat(INVOCATION.matcher(text(answer)).results())
                                .noneMatch(match -> commands.contains(match.group(1))));
    }

    @Test
    void writesNoOptionOfTheCommandLineInTheAnswerToAnySituationOfTheGuide() {
        var answers = answersToEverySection(new Guide(GuideTopic.text()).sections());

        assertThat(answers).allSatisfy(answer -> assertThat(text(answer)).doesNotContainPattern("(?<![\\w-])--[a-z]"));
    }

    @Test
    void setsOnlyBooleanParametersOfTheToolsInTheAnswerToAnySituationOfTheGuide() {
        var booleans = Operations.all().stream()
                .flatMap(operation -> operation.parameters().stream())
                .filter(parameter -> parameter.type() == Parameter.Type.BOOLEAN)
                .map(Parameter::name)
                .collect(Collectors.toSet());

        var answers = answersToEverySection(new Guide(GuideTopic.text()).sections());

        var set = answers.stream()
                .flatMap(answer -> SET_TO_TRUE.matcher(text(answer)).results())
                .map(match -> match.group(1))
                .distinct()
                .toList();
        assertThat(set).containsExactlyInAnyOrder("dryRun", "includeDeclaration");
        assertThat(set).isSubsetOf(booleans);
    }

    @Test
    void writesTheRenameByTokenAsTheToolRenameToken() {
        var tools = listedTools();

        var answers = answersToEverySection(new Guide(GuideTopic.text()).sections());

        assertThat(answers.stream().map(GuideThroughMcpTest::text))
                .anySatisfy(text -> assertThat(text).contains("`rename-token <file> <identifier> <new-name>`"));
        assertThat(tools).contains("rename-token");
    }
}
