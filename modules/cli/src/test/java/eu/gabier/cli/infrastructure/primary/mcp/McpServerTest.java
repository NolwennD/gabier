package eu.gabier.cli.infrastructure.primary.mcp;

import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.INITIALIZE;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.assertError;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.at;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.exchange;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.list;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.modern;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.notification;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.number;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.object;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.request;
import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.Json;
import eu.gabier.protocol.ProtocolVersion;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The server over its streams, one message per line as the stdio transport of MCP says, with no daemon: what it answers
 * to each message of the two eras of the protocol (revisions 2026-07-28 and 2025-11-25).
 */
class McpServerTest {

    private static final String SERVER_INFO = "io.modelcontextprotocol/serverInfo";

    @Nested
    class Discovering {

        @Test
        void answersWhatTheServerSupportsAndWhoItIsAsTheModernEraSays() {
            var answers = exchange(modern("\"discover-1\"", "server/discover"));

            assertThat(answers).hasSize(1);
            var answer = answers.getFirst();
            assertThat(at(answer, "jsonrpc")).isEqualTo("2.0");
            assertThat(at(answer, "id")).isEqualTo("discover-1");
            assertThat(at(answer, "result", "resultType")).isEqualTo("complete");
            assertThat(at(answer, "result", "supportedVersions")).isEqualTo(List.of("2026-07-28", "2025-11-25"));
            assertThat(at(answer, "result", "capabilities")).isEqualTo(Map.of("tools", Map.of()));
            assertThat(at(answer, "result", "_meta", SERVER_INFO))
                    .isEqualTo(Map.of("name", "gabier", "version", ProtocolVersion.CURRENT.value()));
            assertThat(at(answer, "result", "instructions")).isEqualTo("");
        }

        @Test
        void saysHowLongTheAnswerMayBeKeptSinceTheModernEraRequiresIt() {
            var answer = exchange(modern("1", "server/discover")).getFirst();

            assertThat(at(answer, "result", "ttlMs")).isEqualTo(number(3_600_000));
            assertThat(at(answer, "result", "cacheScope")).isEqualTo("public");
        }

        @Test
        void answersWhateverTheNumberOfTheRequestIsAndKeepsItAsItCame() {
            var answers = exchange(modern("7", "server/discover"), modern("\"seven\"", "server/discover"));

            assertThat(answers).extracting(answer -> at(answer, "id")).containsExactly(number(7), "seven");
        }

        @Test
        void refusesARequestThatNamesNoProtocolVersion() {
            var answer = exchange(request("1", "server/discover", "{}")).getFirst();

            assertError(answer, -32602, "Invalid params");
            assertThat(at(answer, "error", "message"))
                    .isEqualTo("Invalid params: _meta has no io.modelcontextprotocol/protocolVersion, and no initialize"
                            + " came before");
        }
    }

    @Nested
    class Initializing {

        @Test
        void answersTheHandshakeOfTheEarlierEraWithItsVersionItsCapabilitiesAndWhoItIs() {
            var answer = exchange(INITIALIZE).getFirst();

            assertThat(at(answer, "id")).isEqualTo(number(1));
            assertThat(at(answer, "result", "protocolVersion")).isEqualTo("2025-11-25");
            assertThat(at(answer, "result", "capabilities")).isEqualTo(Map.of("tools", Map.of()));
            assertThat(at(answer, "result", "serverInfo"))
                    .isEqualTo(Map.of("name", "gabier", "version", ProtocolVersion.CURRENT.value()));
            assertThat(at(answer, "result", "instructions")).isEqualTo("");
            assertThat(object(at(answer, "result"))).doesNotContainKeys("resultType", "ttlMs", "_meta");
        }

        @ParameterizedTest
        @ValueSource(strings = {"2024-11-05", "2025-03-26", "2025-06-18", "2025-11-25"})
        void answersWithTheVersionTheClientAsksWhenItIsOneOfTheEarlierEra(String version) {
            var answer = exchange(request("1", "initialize", "{\"protocolVersion\":\"" + version + "\"}"))
                    .getFirst();

            assertThat(at(answer, "result", "protocolVersion")).isEqualTo(version);
        }

        @ParameterizedTest
        @ValueSource(strings = {"2026-07-28", "2099-01-01", "1.0.0"})
        void answersWithItsLatestVersionOfTheEarlierEraWhenTheClientAsksForAnotherOne(String version) {
            var answer = exchange(request("1", "initialize", "{\"protocolVersion\":\"" + version + "\"}"))
                    .getFirst();

            assertThat(at(answer, "result", "protocolVersion")).isEqualTo("2025-11-25");
            assertThat(object(answer)).doesNotContainKey("error");
        }

        @ParameterizedTest
        @ValueSource(strings = {"{}", "{\"protocolVersion\":3}", "[]"})
        void refusesAnInitializationThatNamesNoVersionAsText(String params) {
            var answer = exchange(request("1", "initialize", params)).getFirst();

            assertError(answer, -32602, "Invalid params");
        }

        @Test
        void sayNothingToTheNotificationThatEndsTheHandshake() {
            assertThat(exchange(INITIALIZE, notification("notifications/initialized")))
                    .hasSize(1);
        }

        @Test
        void servesTheRequestsThatFollowWithoutTheMetadataOfTheModernEraAndTheShapeOfTheEarlierOne() {
            var answers = exchange(
                    INITIALIZE,
                    notification("notifications/initialized"),
                    request("2", "tools/list", "{}"),
                    "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\"}");

            assertThat(answers).hasSize(3);
            assertThat(at(answers.get(1), "result", "tools")).isNotNull();
            assertThat(object(at(answers.get(1), "result"))).doesNotContainKeys("resultType", "ttlMs", "_meta");
            assertThat(at(answers.get(2), "result", "tools")).isNotNull();
        }
    }

    @Nested
    class Listing {

        @Test
        void givesTheToolsToARequestOfTheModernEraWithTheFieldsThatEraRequires() {
            var answer = exchange(modern("2", "tools/list")).getFirst();

            assertThat(list(at(answer, "result", "tools"))).isNotEmpty();
            assertThat(at(answer, "result", "resultType")).isEqualTo("complete");
            assertThat(at(answer, "result", "ttlMs")).isEqualTo(number(3_600_000));
            assertThat(at(answer, "result", "cacheScope")).isEqualTo("public");
            assertThat(at(answer, "result", "_meta", SERVER_INFO))
                    .isEqualTo(Map.of("name", "gabier", "version", ProtocolVersion.CURRENT.value()));
        }

        @Test
        void givesTheToolsToARequestThatNamesTheVersionOfTheEarlierEraInItsMetadata() {
            var answer = exchange(request(
                            "2",
                            "tools/list",
                            "{\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2025-11-25\"}}"))
                    .getFirst();

            assertThat(list(at(answer, "result", "tools"))).isNotEmpty();
            assertThat(object(at(answer, "result"))).doesNotContainKeys("resultType", "ttlMs", "_meta");
        }

        @Test
        void giveTheSameToolsToBothEras() {
            var modernTools = at(exchange(modern("1", "tools/list")).getFirst(), "result", "tools");
            var earlierTools =
                    at(exchange(INITIALIZE, request("2", "tools/list", "{}")).get(1), "result", "tools");

            assertThat(modernTools).isEqualTo(earlierTools);
        }

        @Test
        void refusesARequestOfTheModernEraThatDeclaresNoCapabilities() {
            var answer = exchange(request(
                            "2",
                            "tools/list",
                            "{\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\"}}"))
                    .getFirst();

            assertError(answer, -32602, "Invalid params");
            assertThat(at(answer, "error", "message"))
                    .isEqualTo("Invalid params: _meta has no io.modelcontextprotocol/clientCapabilities");
        }

        @Test
        void refusesARequestWithNoMetadataBeforeAnyInitialization() {
            var answer = exchange(request("2", "tools/list", "{}")).getFirst();

            assertError(answer, -32602, "Invalid params");
        }

        @ParameterizedTest
        @ValueSource(strings = {"[]", "\"x\"", "3", "null"})
        void refusesParamsThatAreNotAnObject(String params) {
            var answer = exchange(request("2", "tools/list", params)).getFirst();

            assertError(answer, -32602, "Invalid params");
        }

        @ParameterizedTest
        @ValueSource(strings = {"[]", "\"x\"", "3", "null"})
        void refusesMetadataThatIsNotAnObject(String meta) {
            var answer = exchange(request("2", "tools/list", "{\"_meta\":" + meta + "}"))
                    .getFirst();

            assertError(answer, -32602, "Invalid params");
        }

        @ParameterizedTest
        @ValueSource(strings = {"3", "null", "true", "[]"})
        void refusesAVersionThatIsNotText(String version) {
            var answer = exchange(request(
                            "2",
                            "tools/list",
                            "{\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":" + version + "}}"))
                    .getFirst();

            assertError(answer, -32602, "Invalid params");
        }
    }

    @Nested
    class Refusing {

        @Test
        void tellsTheVersionsItSupportsToARequestThatNamesAnotherOne() {
            var answer = exchange(request(
                            "1",
                            "tools/list",
                            "{\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"1900-01-01\","
                                    + "\"io.modelcontextprotocol/clientCapabilities\":{}}}"))
                    .getFirst();

            assertError(answer, -32022, "Unsupported protocol version");
            assertThat(at(answer, "error", "data"))
                    .isEqualTo(Map.of("supported", List.of("2026-07-28", "2025-11-25"), "requested", "1900-01-01"));
            assertThat(at(answer, "id")).isEqualTo(number(1));
        }

        @Test
        void tellsTheVersionsItSupportsToServerDiscoverToo() {
            var answer = exchange(request(
                            "1",
                            "server/discover",
                            "{\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"1900-01-01\"}}"))
                    .getFirst();

            assertError(answer, -32022, "Unsupported protocol version");
        }

        @Test
        void saysAMethodItDoesNotKnowIsNotFound() {
            var answer = exchange(modern("4", "resources/list")).getFirst();

            assertError(answer, -32601, "Method not found");
            assertThat(at(answer, "id")).isEqualTo(number(4));
        }

        @Test
        void saysANotificationThatIsKnownIsNotAnAnswerableRequest() {
            var answer =
                    exchange(request("4", "notifications/initialized", "{}")).getFirst();

            assertError(answer, -32601, "Method not found");
        }

        @Test
        void answersNothingToANotificationItDoesNotKnow() {
            assertThat(exchange(notification("notifications/cancelled"), notification("whatever/it/is")))
                    .isEmpty();
        }

        @Test
        void answersNothingToAnswersThatAClientSends() {
            assertThat(exchange(
                            "{\"jsonrpc\":\"2.0\",\"id\":9,\"result\":{}}",
                            "{\"jsonrpc\":\"2.0\",\"id\":9,\"error\":{\"code\":-32601,\"message\":\"no\"}}"))
                    .isEmpty();
        }

        @Test
        void answersPingWithAnEmptyResultWhateverTheEra() {
            var answers = exchange(request("5", "ping", "{}"), INITIALIZE, request("6", "ping", "{}"));

            assertThat(at(answers.get(0), "result")).isEqualTo(Map.of());
            assertThat(at(answers.get(2), "result")).isEqualTo(Map.of());
            assertThat(at(answers.get(0), "id")).isEqualTo(number(5));
        }

        @Test
        void answersPingWhenItHasNoParams() {
            var answer = exchange("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"ping\"}")
                    .getFirst();

            assertThat(at(answer, "result")).isEqualTo(Map.of());
        }
    }

    @Nested
    class ReadingTheLines {

        @Test
        void answersALineThatIsNotJsonAsAParseErrorWithNoIdAndGoesOn() {
            var answers = exchange("{not json", modern("1", "server/discover"));

            assertThat(answers).hasSize(2);
            assertError(answers.getFirst(), -32700, "Parse error");
            assertThat(object(answers.getFirst())).containsEntry("id", null);
            assertThat(at(answers.get(1), "result", "resultType")).isEqualTo("complete");
        }

        static Stream<String> valuesThatAreNoRequest() {
            return Stream.of(
                    "[]", "[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}]", "3", "\"x\"", "null", "true");
        }

        @ParameterizedTest
        @MethodSource("valuesThatAreNoRequest")
        void answersJsonThatIsNoMessageAsAnInvalidRequestWithNoId(String line) {
            var answers = exchange(line);

            assertThat(answers).hasSize(1);
            assertError(answers.getFirst(), -32600, "Invalid Request");
            assertThat(object(answers.getFirst())).containsEntry("id", null);
        }

        @Test
        void keepsTheIdWhenItIsReadableAndTheMessageIsNoRequest() {
            var answers = exchange(
                    "{\"jsonrpc\":\"1.0\",\"id\":8,\"method\":\"ping\"}",
                    "{\"jsonrpc\":\"2.0\",\"id\":\"b\",\"method\":3}",
                    "{\"jsonrpc\":\"2.0\",\"id\":9}");

            assertThat(answers).hasSize(3);
            answers.forEach(answer -> assertError(answer, -32600, "Invalid Request"));
            assertThat(answers).extracting(answer -> at(answer, "id")).containsExactly(number(8), "b", number(9));
        }

        @ParameterizedTest
        @ValueSource(strings = {"null", "1.5", "true", "[]", "{}", "1e400"})
        void refusesAnIdThatIsNeitherTextNorAnInteger(String id) {
            var answer = exchange("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"ping\"}")
                    .getFirst();

            assertError(answer, -32600, "Invalid Request");
            assertThat(object(answer)).containsEntry("id", null);
        }

        @Test
        void takesAnIntegerWrittenWithAFractionOfZeroAsAnInteger() {
            var answer = exchange("{\"jsonrpc\":\"2.0\",\"id\":3.0,\"method\":\"ping\"}")
                    .getFirst();

            assertThat(at(answer, "id")).isEqualTo(number(3));
        }

        @Test
        void skipsBlankLinesAndEndsAtTheEndOfTheInput() {
            assertThat(exchange("", "   ", modern("1", "server/discover"), "")).hasSize(1);
            assertThat(exchange()).isEmpty();
        }

        @Test
        void writesOneLineForEachAnswerWhateverTheIdHolds() throws IOException {
            var out = new ByteArrayOutputStream();
            var line = modern("\"a\\nb\\u2028c\"", "server/discover");

            new McpServer()
                    .serve(
                            new ByteArrayInputStream((line + "\n").getBytes(StandardCharsets.UTF_8)),
                            new PrintStream(out, true, StandardCharsets.UTF_8));

            var text = out.toString(StandardCharsets.UTF_8);
            assertThat(text).endsWith("\n").doesNotContain("\r");
            assertThat(text.lines()).hasSize(1);
            assertThat(at(Json.parse(text.strip()), "id")).isEqualTo("a\nb c");
        }

        @Test
        void readsAndWritesUtf8WhateverTheDefaultCharsetIs() {
            var answer = exchange(modern("\"café ☃\"", "server/discover")).getFirst();

            assertThat(at(answer, "id")).isEqualTo("café ☃");
        }
    }
}
