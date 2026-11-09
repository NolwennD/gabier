package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.cli.Gabier;
import eu.gabier.protocol.Json;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@code gabier mcp}: the server on the standard input and output, started by the command line, with no daemon. */
class ServingMcpTest extends CliTest {

    private static final String DISCOVER = """
        {"jsonrpc":"2.0","id":1,"method":"server/discover","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}}}\
        """;

    private int runWithInput(String input, String... args) {
        // A socket that nothing listens on: the server must never look for the daemon.
        return Gabier.run(
                args,
                Map.of("GABIER_SOCKET", socket().toString()),
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                printStream(out),
                printStream(err));
    }

    @Test
    void answersTheMessagesOfTheInputOnTheStandardOutputAndNothingElse() {
        var exit = runWithInput(DISCOVER + "\n" + "{not json" + "\n", "mcp");

        assertThat(exit).isZero();
        assertThat(out().lines()).hasSize(2);
        assertThat(out().lines().map(Json::parse))
                .allSatisfy(answer -> assertThat(answer).isInstanceOf(Map.class));
        assertThat(out().lines().findFirst().orElseThrow()).startsWith("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{");
        assertThat(err()).isEmpty();
    }

    @Test
    void endsWhenTheInputEnds() {
        assertThat(runWithInput("", "mcp")).isZero();

        assertThat(out()).isEmpty();
        assertThat(err()).isEmpty();
    }

    @Test
    void hasNoInputWhenTheCommandLineIsRunWithNone() {
        assertThat(run("mcp")).isZero();

        assertThat(out()).isEmpty();
    }

    @Test
    void takesNoArgument() {
        assertThat(runWithInput(DISCOVER + "\n", "mcp", "now")).isEqualTo(2);

        assertThat(out()).isEmpty();
        assertThat(err()).startsWith("Usage error: Unsupported arguments;").contains("arguments=mcp now");
    }

    @Test
    void isHelpedAsAnyCommandIs() {
        assertThat(run("help", "mcp")).isZero();

        assertThat(out())
                .contains("gabier [--json] mcp")
                .contains("MCP")
                .contains("usage-error")
                .contains("$ gabier mcp");
    }

    @Test
    void isListedByTheHelpOfTheCommands() {
        assertThat(run("help")).isZero();

        assertThat(out()).contains("mcp");
    }
}
