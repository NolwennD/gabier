package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.google.gson.JsonPrimitive;
import eu.gabier.protocol.DaemonStatus;
import eu.gabier.protocol.JdtlsState;
import eu.gabier.protocol.RpcResponse;
import eu.gabier.shared.error.domain.ErrorKey;
import eu.gabier.shared.error.domain.ErrorStatus;
import eu.gabier.shared.error.domain.GeneratorException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RequestHandlerTest {

    private static final DaemonStatus STATUS =
            new DaemonStatus("0.1", 4242, Duration.ofSeconds(5), Path.of("/work/demo"), JdtlsState.READY);

    private final AtomicBoolean stopped = new AtomicBoolean();
    private final Reading reading = new Reading(new ArrayList<>());

    private final RequestHandler handler = new RequestHandler(List.of(
            new StatusMethod(() -> STATUS),
            new StopMethod(() -> stopped.set(true)),
            new Failing(
                    "refuses", GeneratorException.badRequest(RefusalKey.REFUSED).message("No, not this")),
            new Failing("breaks", null),
            new ReadingTwo(),
            reading));

    private static String request(String id, String method) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\"}";
    }

    private static RpcResponse.Failure failure(String answer) {
        assertThat(answer).doesNotContain("\n");

        return (RpcResponse.Failure) RpcResponse.parse(answer);
    }

    @Nested
    class Status {

        @Test
        void answersWithTheStatusAndTheIdOfTheRequest() {
            var reply = handler.handle(request("7", "status"));

            assertThat(reply.line()).isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":" + STATUS.toJson() + "}");
        }

        @Test
        void echoesAStringId() {
            assertThat(handler.handle(request("\"abc\"", "status")).line()).contains("\"id\":\"abc\"");
        }

        @Test
        void hasNothingToDoAfterTheReply() {
            handler.handle(request("1", "status")).afterReply().run();

            assertThat(stopped).isFalse();
        }
    }

    @Nested
    class Stop {

        @Test
        void answersThatTheDaemonIsStopping() {
            assertThat(handler.handle(request("1", "stop")).line())
                    .isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"stopping\":true}}");
        }

        @Test
        void stopsOnlyOnceTheReplyIsWritten() {
            var reply = handler.handle(request("1", "stop"));

            assertThat(stopped).isFalse();

            reply.afterReply().run();

            assertThat(stopped).isTrue();
        }
    }

    @Nested
    class Refusals {

        @Test
        void anUnknownMethodIsMethodNotFoundAndKeepsTheId() {
            var line = handler.handle(request("5", "rename")).line();

            assertThat(line).contains("\"id\":5").contains("\"code\":-32601");
            assertThat(failure(line).problem().key()).isEqualTo("unknown-method");
        }

        @Test
        void aLineThatIsNotJsonIsAParseErrorWithoutId() {
            var line = handler.handle("{not json").line();

            assertThat(line).contains("\"id\":null").contains("\"code\":-32700");
            assertThat(failure(line).problem().key()).isEqualTo("malformed-json");
            assertThat(failure(line).problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
        }

        @Test
        void aNumberWithAnAbsurdExponentIsAParseError() {
            var line = handler.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"status\",\"x\":1e99999999999}")
                    .line();

            assertThat(line).contains("\"id\":null").contains("\"code\":-32700");
            assertThat(failure(line).problem().key()).isEqualTo("malformed-json");
        }

        @Test
        void anIdWithAnAbsurdExponentIsAParseError() {
            var line = handler.handle("{\"jsonrpc\":\"2.0\",\"id\":1e999999999,\"method\":\"status\"}")
                    .line();

            assertThat(line).contains("\"code\":-32700");
            assertThat(failure(line).problem().key()).isEqualTo("malformed-json");
        }

        @Test
        void aLineNestedTooDeepIsAParseError() {
            var line = handler.handle("[".repeat(100_000)).line();

            assertThat(line).contains("\"code\":-32700");
            assertThat(failure(line).problem().key()).isEqualTo("malformed-json");
        }

        @Test
        void anEmptyLineIsAParseError() {
            assertThat(handler.handle("").line()).contains("\"code\":-32700");
        }

        @Test
        void aLineThatIsNotARequestIsAnInvalidRequest() {
            var line = handler.handle("[1,2]").line();

            assertThat(line).contains("\"id\":null").contains("\"code\":-32600");
            assertThat(failure(line).problem().key()).isEqualTo("invalid-request");
        }

        @Test
        void anInvalidRequestKeepsTheIdItCouldRead() {
            var line = handler.handle("{\"jsonrpc\":\"2.0\",\"id\":9}").line();

            assertThat(line).contains("\"id\":9").contains("\"code\":-32600");
        }

        @Test
        void aRefusalOfAMethodIsAServerErrorWithItsProblemObject() {
            var line = handler.handle(request("2", "refuses")).line();

            assertThat(line).contains("\"id\":2").contains("\"code\":-32000");
            assertThat(failure(line).problem().key()).isEqualTo("test-refused");
            assertThat(failure(line).problem().detail()).isEqualTo("No, not this");
        }

        @Test
        void aHandlerThatRefusesEverythingAnswersEachRequestWithTheRefusalAndRunsNoMethod() {
            var refusing = RequestHandler.refusing(GeneratorException.badRequest(RefusalKey.REFUSED)
                    .message("No, not now")
                    .build());

            var known = refusing.handle(request("4", "status")).line();
            var unknown = refusing.handle(request("5", "rename")).line();

            assertThat(known).contains("\"id\":4");
            assertThat(failure(known).problem().key()).isEqualTo("test-refused");
            assertThat(failure(unknown).problem().key()).isEqualTo("test-refused");
            assertThat(failure(refusing.handle("{not json").line()).problem().key())
                    .isEqualTo("malformed-json");
        }

        @Test
        void aFailureOfAMethodIsAnInternalErrorThatLeaksNothing() {
            var line = handler.handle(request("3", "breaks")).line();

            assertThat(line).contains("\"id\":3").contains("\"code\":-32603").doesNotContain("secret");
            assertThat(failure(line).problem().category()).isEqualTo(ErrorStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        void goesOnAnsweringAfterEveryKindOfRefusal() {
            handler.handle("{not json");
            handler.handle("[]");
            handler.handle(request("1", "nope"));
            handler.handle(request("1", "breaks"));

            assertThat(handler.handle(request("1", "status")).line()).contains("\"result\"");
        }
    }

    @Nested
    class Logging {

        private final List<String> log = new ArrayList<>();

        private final RequestHandler logging = new RequestHandler(
                List.of(
                        new Failing("breaks", null),
                        new Failing(
                                "refuses",
                                GeneratorException.badRequest(RefusalKey.REFUSED)
                                        .message("No, not this")),
                        new Failing(
                                "refuses-for-a-cause",
                                GeneratorException.badRequest(RefusalKey.REFUSED)
                                        .message("No, because")
                                        .cause(new IllegalStateException("the cause of the refusal"))),
                        new BreakingOnJdtls(),
                        new BreakingOnAFile()),
                log::add);

        @Test
        void saysTheMethodTheTypeAndTheMessageOfAFailureThatBecomesAnInternalError() {
            logging.handle(request("3", "breaks"));

            assertThat(log)
                    .singleElement()
                    .satisfies(line -> assertThat(line)
                            .contains("breaks")
                            .contains("java.lang.IllegalStateException")
                            .contains("secret /home/someone/file"));
        }

        @Test
        void followsTheCausesAndGivesTheCodeTheMessageAndTheDataOfAnErrorOfJdtls() {
            logging.handle(request("4", "breaks-on-jdtls"));

            assertThat(log).hasSizeGreaterThan(2);
            assertThat(String.join("\n", log))
                    .contains("breaks-on-jdtls")
                    .contains("eu.gabier.daemon.JdtlsException")
                    .contains("Failed after 1.0 seconds")
                    .contains("org.eclipse.lsp4j.jsonrpc.ResponseErrorException")
                    .contains("-32603")
                    .contains("Internal error.")
                    .contains("java.lang.NullPointerException: element is null")
                    .contains("at org.eclipse.jdt.Rename.create(Rename.java:164)");
        }

        @Test
        void putsEachLineOfWhatJdtlsSaysOnALineOfTheLogAndWritesOutWhatATerminalWouldObey() {
            logging.handle(request("4", "breaks-on-jdtls"));

            assertThat(log)
                    .allSatisfy(line -> assertThat(line).doesNotContain("\n").doesNotContain("\u001b"));
            assertThat(String.join("\n", log)).contains("\\u001b[2Jclear");
        }

        @Test
        void leavesTheParametersOfTheRequestOutOfTheLog() {
            logging.handle("""
                {"jsonrpc":"2.0","id":5,"method":"breaks-on-a-file","params":{"file":"Secret.java"}}\
                """);

            assertThat(log).isNotEmpty().noneMatch(line -> line.contains("Secret.java"));
        }

        @Test
        void saysNothingOfARefusalThatHasNoCause() {
            logging.handle(request("2", "refuses"));
            logging.handle(request("2", "nope"));
            logging.handle("{not json");

            assertThat(log).isEmpty();
        }

        @Test
        void saysTheCauseOfARefusalThatHasOne() {
            var line = logging.handle(request("2", "refuses-for-a-cause")).line();

            assertThat(failure(line).problem().key()).isEqualTo("test-refused");
            assertThat(String.join("\n", log)).contains("refuses-for-a-cause").contains("the cause of the refusal");
        }

        @Test
        void answersTheSameWhetherOrNotItLogs() {
            var silent = new RequestHandler(List.of(new Failing("breaks", null)));

            assertThat(logging.handle(request("3", "breaks")).line())
                    .isEqualTo(silent.handle(request("3", "breaks")).line());
        }
    }

    @Nested
    class Parameters {

        private static String reads(String params) {
            return """
                {"jsonrpc":"2.0","id":6,"method":"reads","params":%s}\
                """.formatted(params);
        }

        @Test
        void aMethodReadsTheParametersOfTheRequest() {
            var line = handler.handle(reads("{\"file\":\"src/A.java\"}")).line();

            assertThat(line).isEqualTo("""
                {"jsonrpc":"2.0","id":6,"result":{"file":"src/A.java"}}\
                """);
            assertThat(reading.calls()).containsExactly(Map.of("file", "src/A.java"));
        }

        @Test
        void aMethodThatTakesNoneIsCalledWhenTheParamsAreEmpty() {
            var line = handler.handle("""
                {"jsonrpc":"2.0","id":1,"method":"status","params":{}}\
                """).line();

            assertThat(line).isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":" + STATUS.toJson() + "}");
        }

        @Test
        void aParameterTheMethodDoesNotKnowIsABadRequestThatNamesIt() {
            var line = handler.handle(reads("{\"file\":\"A.java\",\"extra\":\"x\"}"))
                    .line();

            assertThat(line).contains("\"id\":6").contains("\"code\":-32602");
            assertThat(failure(line).problem().key()).isEqualTo("unknown-parameter");
            assertThat(failure(line).problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
            assertThat(failure(line).problem().parameters()).containsOnly(entry("parameter", "extra"));
            assertThat(reading.calls()).isEmpty();
        }

        @Test
        void aParameterIsUnknownToAMethodThatTakesNone() {
            var line = handler.handle("""
                {"jsonrpc":"2.0","id":1,"method":"status","params":{"file":"A.java"}}\
                """).line();

            assertThat(failure(line).problem().key()).isEqualTo("unknown-parameter");
            assertThat(failure(line).problem().parameters()).containsOnly(entry("parameter", "file"));
        }

        @Test
        void aParameterTheMethodNeedsAndTheRequestLacksIsABadRequestThatNamesIt() {
            var line = handler.handle(reads("{}")).line();

            assertThat(line).contains("\"id\":6").contains("\"code\":-32602");
            assertThat(failure(line).problem().key()).isEqualTo("missing-parameter");
            assertThat(failure(line).problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
            assertThat(failure(line).problem().parameters()).containsOnly(entry("parameter", "file"));
            assertThat(reading.calls()).isEmpty();
        }

        @Test
        void aMethodThatNeedsParametersRefusesARequestWithoutParams() {
            var line = handler.handle(request("6", "reads")).line();

            assertThat(failure(line).problem().key()).isEqualTo("missing-parameter");
        }

        @ParameterizedTest
        @ValueSource(strings = {"[\"A.java\"]", "\"A.java\"", "3", "null", "true"})
        void paramsThatAreNotAnObjectAreInvalidParamsThatNameThem(String params) {
            var line = handler.handle(reads(params)).line();

            assertThat(line).contains("\"id\":6").contains("\"code\":-32602");
            assertThat(failure(line).problem().key()).isEqualTo("invalid-request");
            assertThat(failure(line).problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
            assertThat(failure(line).problem().parameters()).containsOnly(entry("parameter", "params"));
            assertThat(reading.calls()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"3", "true", "null", "[\"A.java\"]", "{\"a\":\"b\"}"})
        void aParameterThatIsNotAStringIsInvalidParamsThatNamesIt(String value) {
            var line = handler.handle(reads("{\"file\":\"A.java\",\"line\":" + value + "}"))
                    .line();

            assertThat(line).contains("\"id\":6").contains("\"code\":-32602");
            assertThat(failure(line).problem().key()).isEqualTo("invalid-request");
            assertThat(failure(line).problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
            assertThat(failure(line).problem().parameters()).containsOnly(entry("parameter", "line"));
            assertThat(reading.calls()).isEmpty();
        }

        @Test
        void aRepeatedKeyInParamsIsRefusedAsAParseErrorAndTheMethodIsNotCalled() {
            var line = handler.handle(reads("{\"file\":1,\"file\":\"A.java\"}")).line();

            assertThat(line).contains("\"id\":null").contains("\"code\":-32700");
            assertThat(failure(line).problem().key()).isEqualTo("malformed-json");
            assertThat(failure(line).problem().detail()).endsWith("a member is repeated");
            assertThat(reading.calls()).isEmpty();
        }

        @Test
        void aRepeatedKeyElsewhereInTheRequestIsRefusedToo() {
            var line = handler.handle("""
                {"jsonrpc":"2.0","id":1,"id":2,"method":"status"}\
                """).line();

            assertThat(line).contains("\"code\":-32700");
            assertThat(failure(line).problem().detail()).endsWith("a member is repeated");
        }

        @Test
        void aRepeatedKeyInAnIgnoredMemberIsRefusedToo() {
            var line = handler.handle("""
                {"jsonrpc":"2.0","id":1,"method":"status","extra":{"a":1,"a":2}}\
                """).line();

            assertThat(line).contains("\"code\":-32700");
        }

        @Test
        void aParameterNamedWithAControlCharacterIsAnsweredOnOneLineWithTheCharacterEscaped() {
            var line = handler.handle(reads("{\"file\":\"A.java\",\"a\\u0001b\\n\\u009b\":\"x\"}"))
                    .line();

            assertThat(line).doesNotContain("\n").doesNotContain("\u0001").doesNotContain("\u009b");
            assertThat(line).contains("takes no parameter a\\u0001b\\n\\u009b");
            assertThat(line).contains("\"code\":-32602");
            assertThat(failure(line).problem().parameters()).containsOnly(entry("parameter", "a\u0001b\n\u009b"));
        }

        @Test
        void aVeryLongParameterNameIsRefusedOnOneLineAndTheDaemonGoesOn() {
            var name = "n".repeat(1_000_000);

            var line = handler.handle(reads("{\"file\":\"A.java\",\"" + name + "\":\"x\"}"))
                    .line();

            assertThat(failure(line).problem().key()).isEqualTo("unknown-parameter");
            assertThat(failure(line).problem().parameters()).containsOnly(entry("parameter", name));
            assertThat(handler.handle(reads("{\"file\":\"A.java\"}")).line()).contains("\"result\"");
        }

        @Test
        void aVeryLongValueIsReadAsAnyOtherAndTheDaemonGoesOn() {
            var value = "v".repeat(1_000_000);

            var line = handler.handle(reads("{\"file\":\"" + value + "\"}")).line();

            assertThat(line).isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":6,\"result\":{\"file\":\"" + value + "\"}}");
            assertThat(handler.handle(reads("{\"file\":\"A.java\"}")).line()).contains("\"result\"");
        }

        @Test
        void anUnknownParameterIsNamedBeforeAMissingOne() {
            var line = handler.handle(reads("{\"zzz\":\"x\"}")).line();

            assertThat(failure(line).problem().key()).isEqualTo("unknown-parameter");
            assertThat(failure(line).problem().parameters()).containsOnly(entry("parameter", "zzz"));
        }

        @Test
        void ofSeveralUnknownParametersTheFirstByNameIsNamed() {
            var line = handler.handle(reads("{\"file\":\"A.java\",\"zeta\":\"1\",\"alpha\":\"2\",\"mu\":\"3\"}"))
                    .line();

            assertThat(failure(line).problem().parameters()).containsOnly(entry("parameter", "alpha"));
        }

        @Test
        void ofSeveralMissingParametersTheFirstByNameIsNamed() {
            var line = handler.handle("""
                {"jsonrpc":"2.0","id":6,"method":"reads-two","params":{}}\
                """).line();

            assertThat(failure(line).problem().key()).isEqualTo("missing-parameter");
            assertThat(failure(line).problem().parameters()).containsOnly(entry("parameter", "alpha"));
        }

        @Test
        void goesOnAnsweringAfterEveryRefusalOfParameters() {
            handler.handle(reads("[]"));
            handler.handle(reads("{\"file\":1}"));
            handler.handle(reads("{}"));
            handler.handle(reads("{\"file\":\"A.java\",\"other\":\"x\"}"));

            assertThat(handler.handle(reads("{\"file\":\"A.java\"}")).line()).contains("\"result\"");
        }
    }

    /** A method that needs the parameter {@code file}, and keeps the parameters it was called with. */
    private record Reading(List<Map<String, String>> calls) implements DaemonMethod {

        @Override
        public String name() {
            return "reads";
        }

        @Override
        public Set<String> parameters() {
            return Set.of("file");
        }

        @Override
        public Outcome call(Map<String, String> params) {
            calls.add(params);

            return Outcome.of("""
                {"file":"%s"}\
                """.formatted(params.get("file")));
        }
    }

    /** A method that needs the parameters {@code beta} and {@code alpha}, and reads nothing. */
    private record ReadingTwo() implements DaemonMethod {

        @Override
        public String name() {
            return "reads-two";
        }

        @Override
        public Set<String> parameters() {
            return new LinkedHashSet<>(List.of("beta", "alpha"));
        }

        @Override
        public Outcome call(Map<String, String> params) {
            return Outcome.of("{}");
        }
    }

    private enum RefusalKey implements ErrorKey {
        REFUSED;

        @Override
        public String get() {
            return "test-refused";
        }
    }

    /** A method that throws the exception it was given, or an unexpected one when it was given none. */
    private record Failing(String name, GeneratorException.GeneratorExceptionBuilder refusal) implements DaemonMethod {

        @Override
        public Outcome call(Map<String, String> params) {
            if (refusal == null) {
                throw new IllegalStateException("secret /home/someone/file");
            }

            throw refusal.build();
        }
    }

    /** A method that fails as the adapter does when jdtls answers an internal error, with the data of its answer. */
    private record BreakingOnJdtls() implements DaemonMethod {

        @Override
        public String name() {
            return "breaks-on-jdtls";
        }

        @Override
        public Outcome call(Map<String, String> params) {
            var data = new JsonPrimitive("""
                java.util.concurrent.CompletionException: java.lang.NullPointerException: element is null
                \tat org.eclipse.jdt.Rename.create(Rename.java:164)
                \u001b[2Jclear\
                """);
            var error = new ResponseErrorException(new ResponseError(-32603, "Internal error.", data));

            throw new JdtlsException("Failed after 1.0 seconds waiting for the rename", error);
        }
    }

    /** A method that takes the parameter {@code file} and fails whatever it is. */
    private record BreakingOnAFile() implements DaemonMethod {

        @Override
        public String name() {
            return "breaks-on-a-file";
        }

        @Override
        public Set<String> parameters() {
            return Set.of("file");
        }

        @Override
        public Outcome call(Map<String, String> params) {
            throw new IllegalStateException("something broke");
        }
    }
}
