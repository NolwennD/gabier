package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.protocol.DaemonStatus;
import fr.craft.gabier.protocol.RpcResponse;
import fr.craft.gabier.shared.error.domain.ErrorKey;
import fr.craft.gabier.shared.error.domain.ErrorStatus;
import fr.craft.gabier.shared.error.domain.GeneratorException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RequestHandlerTest {

    private static final DaemonStatus STATUS = new DaemonStatus("0.1", 4242, Duration.ofSeconds(5));

    private final AtomicBoolean stopped = new AtomicBoolean();

    private final RequestHandler handler = new RequestHandler(List.of(
            new StatusMethod(() -> STATUS),
            new StopMethod(() -> stopped.set(true)),
            new Failing(
                    "refuses", GeneratorException.badRequest(RefusalKey.REFUSED).message("No, not this")),
            new Failing("breaks", null)));

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
        public Outcome call() {
            if (refusal == null) {
                throw new IllegalStateException("secret /home/someone/file");
            }

            throw refusal.build();
        }
    }
}
