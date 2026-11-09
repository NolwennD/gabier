package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.cli.Gabier;
import eu.gabier.protocol.Json;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * What {@code gabier mcp} tells a host about using its tools: the resource {@code mcp-instructions.md}, next to the one of
 * {@code gabier help agents}. A tool is written in the text between backticks, as {@code `rename`}; these tests keep the
 * text short, in step with the tools the server lists, and carried by the handshake of both eras.
 */
class McpInstructionsTest extends CliTest {

    private static final String DISCOVER = """
        {"jsonrpc":"2.0","id":1,"method":"server/discover","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}}}\
        """;

    private static final String INITIALIZE = """
        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25"}}\
        """;

    private static final String TOOLS_LIST = """
        {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}}}\
        """;

    /** A tool named in the text: a word of lower case letters and hyphens between backticks. */
    private static final Pattern NAMED = Pattern.compile("`([a-z][a-z-]*)`");

    /** The tools a text names, so that a test can compare them to the ones the server lists. */
    static List<String> named(String text) {
        return NAMED.matcher(text)
                .results()
                .map(match -> match.group(1))
                .distinct()
                .toList();
    }

    /** The names in the text that are not among these tools. */
    static List<String> unknown(String text, List<String> tools) {
        return named(text).stream().filter(name -> !tools.contains(name)).toList();
    }

    /** What {@code gabier mcp} answers to these lines, one JSON value for each answer. */
    private List<Object> mcp(String... lines) {
        var input = String.join("\n", lines) + "\n";
        var exit = Gabier.run(
                new String[] {"mcp"},
                Map.of(
                        "GABIER_SOCKET",
                        socket().toString(),
                        "PATH",
                        directory.resolve("no-bin").toString()),
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                printStream(out),
                printStream(err));
        assertThat(exit).isZero();

        return out().lines().map(Json::parse).toList();
    }

    @SuppressWarnings("unchecked") // The server writes JSON objects, and the tests know where.
    private static Map<String, Object> result(Object answer) {
        return (Map<String, Object>) ((Map<String, Object>) answer).get("result");
    }

    @SuppressWarnings("unchecked") // The server lists its tools as an array of objects.
    private List<String> listedTools() {
        var tools =
                (List<Map<String, Object>>) result(mcp(TOOLS_LIST).getFirst()).get("tools");

        return tools.stream().map(tool -> (String) tool.get("name")).toList();
    }

    @Test
    void isAResourceOfTheCliThatIsNotBlank() {
        assertThat(McpInstructions.text()).isNotBlank();
    }

    @Test
    void tellsTheAgentToUseTheToolsInsteadOfEditingJavaAsTextAndHowToUseThem() {
        assertThat(McpInstructions.text())
                .contains("instead of editing")
                .contains("counted from 1")
                .contains("dryRun")
                .contains("`guide`")
                .contains("`diagnostics`")
                .contains("`organize-imports`")
                .contains("`actions`")
                .contains("`apply-action`");
    }

    @Test
    void everyToolTheTextNamesIsOneTheServerLists() {
        var listed = listedTools();

        assertThat(named(McpInstructions.text())).isNotEmpty();
        assertThat(unknown(McpInstructions.text(), listed)).isEmpty();
    }

    @Test
    void aTextThatNamesAToolTheServerDoesNotListIsCaught() {
        assertThat(unknown(
                        "Use `rename`, then `frobnicate` and `start`; dryRun is a value, not `dryRun`.", listedTools()))
                .containsExactly("frobnicate", "start");
    }

    @Test
    void isTheInstructionsOfTheHandshakeOfTheModernEra() {
        var discovered = mcp(DISCOVER).getFirst();

        assertThat(result(discovered)).containsEntry("instructions", McpInstructions.text());
    }

    @Test
    void isTheInstructionsOfTheHandshakeOfTheEarlierEra() {
        var initialized = mcp(INITIALIZE).getFirst();

        assertThat(result(initialized)).containsEntry("instructions", McpInstructions.text());
    }
}
