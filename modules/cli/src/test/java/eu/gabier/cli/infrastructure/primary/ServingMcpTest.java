package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.cli.Gabier;
import eu.gabier.protocol.Json;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

/**
 * {@code gabier mcp}: the server on the standard input and output, started by the command line. It asks no daemon to
 * begin with; its first handshake starts the daemon in the background, so that jdtls imports the project while the host
 * loads the tools.
 */
class ServingMcpTest extends CliTest {

    private static final String DISCOVER = """
        {"jsonrpc":"2.0","id":1,"method":"server/discover","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}}}\
        """;

    private static final Duration PATIENCE = Duration.ofSeconds(10);

    private int runWithInput(String input, String... args) {
        // No daemon of the PATH to start: the warm-up of the handshake is the only thing that looks for one.
        return Gabier.run(
                args,
                Map.of(
                        "GABIER_SOCKET",
                        socket().toString(),
                        "PATH",
                        directory.resolve("no-bin").toString()),
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                printStream(out),
                printStream(err));
    }

    /** Waits for what a thread in the background does, which the end of the server does not wait for. */
    private static void eventually(BooleanSupplier happened) throws InterruptedException {
        var deadline = System.nanoTime() + PATIENCE.toNanos();
        while (!happened.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(happened.getAsBoolean())
                .as("it happened within %s", PATIENCE)
                .isTrue();
    }

    @Test
    void answersTheMessagesOfTheInputOnTheStandardOutputAndNothingElse() throws Exception {
        try (var daemon = daemonAnswering("{}")) {
            var exit = runWithInput(DISCOVER + "\n" + "{not json" + "\n", "mcp");
            eventually(() -> !daemon.requests().isEmpty());

            assertThat(exit).isZero();
            assertThat(out().lines()).hasSize(2);
            assertThat(out().lines().map(Json::parse))
                    .allSatisfy(answer -> assertThat(answer).isInstanceOf(Map.class));
            assertThat(out().lines().findFirst().orElseThrow())
                    .startsWith("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{");
            assertThat(err()).isEmpty();
        }
    }

    @Test
    void startsTheDaemonOfTheProjectInTheBackgroundAtTheFirstHandshakeAsStartDoes() throws Exception {
        try (var daemon = daemonAnswering("{}")) {
            runWithInput(DISCOVER + "\n" + DISCOVER + "\n", "mcp");
            eventually(() -> !daemon.requests().isEmpty());

            assertThat(daemon.requests())
                    .singleElement()
                    .satisfies(request -> assertThat(Json.parse(request))
                            .asInstanceOf(InstanceOfAssertFactories.MAP)
                            .containsEntry("method", "start"));
        }
    }

    @Test
    void asksNoDaemonForAnInputThatHoldsNoHandshake() throws Exception {
        try (var daemon = daemonAnswering("{}")) {
            runWithInput("{not json\n", "mcp");

            assertThat(out().lines()).hasSize(1);
            assertThat(daemon.requests()).isEmpty();
            assertThat(err()).isEmpty();
        }
    }

    @Test
    void tellsACallOfStatusWhenNoDaemonRunsAsAFailedCallAndStartsNone() {
        var status = """
            {"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"status","arguments":{},"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}}}
            """;

        var exit = runWithInput(status, "mcp");

        assertThat(exit).isZero();
        assertThat(out().lines())
                .singleElement()
                .satisfies(answer -> assertThat(Json.parse(answer))
                        .asInstanceOf(InstanceOfAssertFactories.MAP)
                        .extractingByKey("result")
                        .asInstanceOf(InstanceOfAssertFactories.MAP)
                        .containsEntry("isError", true)
                        .extractingByKey("structuredContent")
                        .asInstanceOf(InstanceOfAssertFactories.MAP)
                        .containsEntry("key", "daemon-not-running"));
        assertThat(err()).isEmpty();
    }

    @Test
    void tellsOnTheStandardErrorThatTheDaemonCouldNotBeStartedAndAnswersTheHandshakeAnyway() throws Exception {
        var exit = runWithInput(DISCOVER + "\n", "mcp");
        eventually(() -> err().contains("gabier mcp: the daemon could not be started"));

        assertThat(exit).isZero();
        assertThat(out().lines())
                .singleElement()
                .satisfies(answer -> assertThat(answer)
                        .startsWith("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{")
                        .doesNotContain("could not be started"));
    }

    @Test
    void endsWhenTheInputEnds() {
        assertThat(runWithInput("", "mcp")).isZero();

        assertThat(out()).isEmpty();
        assertThat(err()).isEmpty();
    }

    @Test
    void doesNotStopTheDaemonWhenTheHostDisconnects() throws Exception {
        try (var daemon = daemonAnswering("{}")) {
            var exit = runWithInput(DISCOVER + "\n", "mcp");
            eventually(() -> !daemon.requests().isEmpty());

            assertThat(exit).isZero();
            // The daemon is shared with the other sessions and the command line, and stops by itself when idle.
            assertThat(daemon.requests()).noneMatch(request -> request.contains("\"method\":\"stop\""));
        }
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
