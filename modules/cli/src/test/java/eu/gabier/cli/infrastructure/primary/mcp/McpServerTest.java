package eu.gabier.cli.infrastructure.primary.mcp;

import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.INITIALIZE;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.assertError;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.at;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.exchange;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.list;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.modern;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.modernCall;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.notification;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.number;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.object;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.request;
import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.DaemonStatus;
import eu.gabier.protocol.DiagnosedFiles;
import eu.gabier.protocol.JdtlsState;
import eu.gabier.protocol.Json;
import eu.gabier.protocol.ProtocolVersion;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
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
            assertThat(at(answer, "result", "instructions")).isEqualTo(McpExchange.INSTRUCTIONS);
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
    class WarmingUpTheDaemon {

        private final List<Runnable> begun = new ArrayList<>();
        private final WarmUp recorded = new WarmUp(() -> {}, McpExchange.silent(), begun::add);

        private List<@Nullable Object> exchangeWith(WarmUp warmUp, String... lines) {
            return exchange(McpExchange.serverOf(new StubDaemon(), warmUp), lines);
        }

        @Test
        void beginsTheWarmUpAtTheFirstServerDiscover() {
            exchangeWith(recorded, modern("1", "server/discover"));

            assertThat(begun).hasSize(1);
        }

        @Test
        void beginsTheWarmUpAtInitialize() {
            exchangeWith(recorded, INITIALIZE);

            assertThat(begun).hasSize(1);
        }

        @Test
        void beginsItOnceWhateverTheNumberOfHandshakes() {
            exchangeWith(recorded, modern("1", "server/discover"), modern("2", "server/discover"), INITIALIZE);

            assertThat(begun).hasSize(1);
        }

        @Test
        void beginsNothingForARequestThatIsNoHandshakeOrThatIsRefused() {
            exchangeWith(
                    recorded,
                    modern("1", "ping"),
                    modern("2", "tools/list"),
                    request("3", "server/discover", "{}"),
                    request("4", "initialize", "{}"));

            assertThat(begun).isEmpty();
        }

        @Test
        void answersTheHandshakeWhileTheDaemonIsStillStarting() throws InterruptedException {
            var go = new CountDownLatch(1);
            var warmUp = WarmUp.inBackground(() -> awaitQuietly(go), McpExchange.silent());

            var answers = exchangeWith(warmUp, modern("1", "server/discover"));

            assertThat(go.getCount()).isOne();
            assertThat(at(answers.getFirst(), "result", "resultType")).isEqualTo("complete");
            go.countDown();
            warmUp.awaitEnd();
        }

        @Test
        void answersTheHandshakeWhenTheDaemonCannotBeStarted() {
            var log = new ByteArrayOutputStream();
            var warmUp = WarmUp.inBackground(
                    () -> {
                        throw new IllegalStateException("gabier-daemon was not found");
                    },
                    new PrintStream(log, true, StandardCharsets.UTF_8));

            var answers = exchangeWith(warmUp, INITIALIZE, notification("notifications/initialized"));
            warmUp.awaitEnd();

            assertThat(answers).hasSize(1);
            assertThat(at(answers.getFirst(), "result", "protocolVersion")).isEqualTo("2025-11-25");
            assertThat(log.toString(StandardCharsets.UTF_8)).contains("the daemon could not be started");
        }

        @Test
        void makesACallWaitForTheStartOfTheDaemonSoThatItStartsNoSecondOne() throws Exception {
            var go = new CountDownLatch(1);
            var warmedUp = new AtomicBoolean();
            var warmedUpWhenAsked = new CopyOnWriteArrayList<Boolean>();
            var daemon = new StubDaemon()
                    .answering(new DiagnosedFiles(List.of()).toJson())
                    .whenAsked(() -> warmedUpWhenAsked.add(warmedUp.get()));
            var warmUp = WarmUp.inBackground(
                    () -> {
                        awaitQuietly(go);
                        warmedUp.set(true);
                    },
                    McpExchange.silent());

            var answers = CompletableFuture.supplyAsync(() -> exchange(
                    McpExchange.serverOf(daemon, warmUp),
                    modern("1", "server/discover"),
                    modernCall("diagnostics", "{\"files\":[\"/work/A.java\"]}")));
            Thread.sleep(200);
            go.countDown();

            assertThat(answers.get(10, TimeUnit.SECONDS)).hasSize(2);
            assertThat(warmedUpWhenAsked).containsExactly(true);
        }

        @Test
        void makesNeitherTheGuideNorACallWithWrongValuesNorTheStatusWaitForAStartThatDoesNotEnd() throws Exception {
            var go = new CountDownLatch(1);
            var status = new DaemonStatus("0.1", 4242, Duration.ofSeconds(1), Path.of("/work"), JdtlsState.STARTING);
            var daemon = new StubDaemon().answering(status.toJson());
            var warmUp = WarmUp.inBackground(() -> awaitQuietly(go), McpExchange.silent());

            try (var session = new McpSession(McpExchange.serverOf(daemon, warmUp))) {
                session.send(modern("1", "server/discover"));
                session.send(modernCall("2", "guide", "{\"task\":\"rename a method\"}"));
                session.send(modernCall("3", "diagnostics", "{}"));
                session.send(modernCall("4", "status", "{}"));

                var answers = new ArrayList<@Nullable Object>();
                for (var count = 0; count < 4; count++) {
                    answers.add(session.next());
                }

                assertThat(go.getCount()).isOne();
                assertThat(answers)
                        .extracting(answer -> at(answer, "id"))
                        .containsExactlyInAnyOrder(number(1), number(2), number(3), number(4));
                assertThat(daemon.asked()).extracting(StubDaemon.Asked::method).containsExactly("status");
                go.countDown();
            }
        }

        @Test
        void answersTheCallWhenTheWarmUpEndedWithAnErrorOfTheProcess() throws Exception {
            var daemon = new StubDaemon().answering(new DiagnosedFiles(List.of()).toJson());
            var warmUp = WarmUp.inBackground(
                    () -> {
                        throw new NoClassDefFoundError("java/lang/ProcessBuilder");
                    },
                    McpExchange.silent());

            var answers = exchange(
                    McpExchange.serverOf(daemon, warmUp),
                    modern("1", "server/discover"),
                    modernCall("2", "diagnostics", "{\"files\":[\"/work/A.java\"]}"));

            assertThat(answers).hasSize(2);
            assertThat(answers)
                    .anySatisfy(answer ->
                            assertThat(at(answer, "result", "isError")).isEqualTo(false));
        }

        private void awaitQuietly(CountDownLatch latch) {
            try {
                latch.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
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
            assertThat(at(answer, "result", "instructions")).isEqualTo(McpExchange.INSTRUCTIONS);
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

        /**
         * Revision 2026-07-28 removed {@code ping} and made {@code resultType} a required member of every result, so a
         * client of that era that still sends it reads what it reads in any other result.
         */
        @Test
        void saysTheResultOfAPingThatNamesTheModernEraIsComplete() {
            var answer = exchange(modern("5", "ping")).getFirst();

            assertThat(at(answer, "result", "resultType")).isEqualTo("complete");
            assertThat(at(answer, "result", SERVER_INFO)).isNull();
            assertThat(at(answer, "result", "_meta", SERVER_INFO))
                    .isEqualTo(Map.of("name", "gabier", "version", ProtocolVersion.CURRENT.value()));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2025-11-25\"}",
                    "\"_meta\":3",
                    "\"_meta\":{}"
                })
        void givesAPingThatDoesNotNameTheModernEraTheEmptyResult(String params) {
            var answer = exchange(request("5", "ping", "{" + params + "}")).getFirst();

            assertThat(at(answer, "result")).isEqualTo(Map.of());
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
        @ValueSource(strings = {"null", "1.5", "true", "[]", "{}", "1e-400"})
        void refusesAnIdThatIsNeitherTextNorAWholeNumber(String id) {
            var answer = exchange("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"ping\"}")
                    .getFirst();

            assertError(answer, -32600, "Invalid Request");
            assertThat(object(answer)).containsEntry("id", null);
        }

        /**
         * A whole number is given back as it was written, whatever its notation: the client matches the answer to its
         * request by the id, and {@code 1.0}, {@code 1e2} or {@code -0} read back as {@code 1}, {@code 1E+2} or {@code
         * 0} would be another id to a client that compares the text. The MCP schema says an id is a text or a number.
         */
        @ParameterizedTest
        @ValueSource(
                strings = {"3.0", "1e2", "1E+2", "-0", "100000000000000000000000000000", "1e400", "-9223372036854775809"
                })
        void givesAWholeNumberIdBackAsItWasWritten(String id) {
            var out = new ByteArrayOutputStream();

            McpExchange.serverOf(new StubDaemon())
                    .serve(
                            new ByteArrayInputStream(("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"ping\"}\n")
                                    .getBytes(StandardCharsets.UTF_8)),
                            new PrintStream(out, true, StandardCharsets.UTF_8));

            assertThat(out.toString(StandardCharsets.UTF_8))
                    .isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{}}\n");
        }

        @Test
        void givesATextIdBackWithTheSameValue() {
            var answer = exchange("{\"jsonrpc\":\"2.0\",\"id\":\"\\u0041/é\",\"method\":\"ping\"}")
                    .getFirst();

            assertThat(at(answer, "id")).isEqualTo("A/é");
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

            McpExchange.serverOf(new StubDaemon())
                    .serve(
                            new ByteArrayInputStream((line + "\n").getBytes(StandardCharsets.UTF_8)),
                            new PrintStream(out, true, StandardCharsets.UTF_8));

            var text = out.toString(StandardCharsets.UTF_8);
            assertThat(text).endsWith("\n").doesNotContain("\r");
            assertThat(text.lines()).hasSize(1);
            assertThat(at(Json.parse(text.strip()), "id")).isEqualTo("a\nb c");
        }

        @Test
        void answersALineOfAMebibyteOrMoreAndGoesOn() {
            var big = "x".repeat(1 << 20);

            var answers = exchange(modern("\"" + big + "\"", "ping"), "{\"not\":\"" + big + big, modern("2", "ping"));

            assertThat(answers).hasSize(3);
            assertThat(at(answers.get(0), "id")).isEqualTo(big);
            assertError(answers.get(1), -32700, "Parse error");
            assertThat(at(answers.get(2), "id")).isEqualTo(number(2));
        }

        @Test
        void answersALineThatTheInputEndsInTheMiddleOfAsAParseError() {
            var answers = exchange(modern("1", "ping"), "{\"jsonrpc\":\"2.0\",\"id\":2,\"meth");

            assertThat(answers).hasSize(2);
            assertThat(at(answers.get(0), "id")).isEqualTo(number(1));
            assertError(answers.get(1), -32700, "Parse error");
        }

        @Test
        void answersTheLastLineWhenTheInputEndsWithNoLineBreak() {
            var answers = exchange(modern("1", "ping"));

            assertThat(answers)
                    .singleElement()
                    .satisfies(answer -> assertThat(at(answer, "id")).isEqualTo(number(1)));
        }

        @Test
        void readsBytesThatAreNotUtf8AsReplacementCharactersAndGoesOn() {
            var bytes = new ByteArrayOutputStream();
            bytes.writeBytes("{\"jsonrpc\":\"2.0\",\"id\":\"a".getBytes(StandardCharsets.UTF_8));
            bytes.write(0xFF);
            bytes.write(0xC3);
            bytes.writeBytes("b\",\"method\":\"ping\"}\nÿþ not json\n".getBytes(StandardCharsets.ISO_8859_1));
            bytes.writeBytes(modern("3", "ping").getBytes(StandardCharsets.UTF_8));

            var answers = exchange(new ByteArrayInputStream(bytes.toByteArray()));

            assertThat(answers).hasSize(3);
            assertThat((String) at(answers.get(0), "id")).startsWith("a�").endsWith("b");
            assertError(answers.get(1), -32700, "Parse error");
            assertThat(at(answers.get(2), "id")).isEqualTo(number(3));
        }

        @Test
        void refusesAnArrayNestedDeeperThanItReadsAndGoesOn() {
            var deep = "[".repeat(65) + "]".repeat(65);

            var answers = exchange(
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"move\","
                            + "\"arguments\":{\"files\":" + deep + "}}}",
                    modern("2", "ping"));

            assertThat(answers).hasSize(2);
            assertError(answers.get(0), -32700, "Parse error");
            assertThat(at(answers.get(1), "id")).isEqualTo(number(2));
        }

        @Test
        void readsAnArrayNestedAsDeepAsItAllows() {
            // The message and its params are two levels of the 64 that are read.
            var deep = "[".repeat(62) + "]".repeat(62);
            var tooDeep = "[".repeat(63) + "]".repeat(63);

            var answers = exchange(
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\",\"params\":{\"x\":" + deep + "}}",
                    "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\",\"params\":{\"x\":" + tooDeep + "}}");

            assertThat(at(answers.get(0), "result")).isEqualTo(Map.of());
            assertError(answers.get(1), -32700, "Parse error");
        }

        @Test
        void readsAndWritesUtf8WhateverTheDefaultCharsetIs() {
            var answer = exchange(modern("\"café ☃\"", "server/discover")).getFirst();

            assertThat(at(answer, "id")).isEqualTo("café ☃");
        }
    }
}
