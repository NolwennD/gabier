package fr.craft.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.entry;

import fr.craft.gabier.shared.error.domain.ErrorKey;
import fr.craft.gabier.shared.error.domain.ErrorStatus;
import fr.craft.gabier.shared.error.domain.GeneratorException;
import fr.craft.gabier.shared.error.domain.MissingMandatoryValueException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonRpcTest {

    private static final Problem PROBLEM = new Problem(
            "urn:gabier:error:some-key",
            "Some key",
            "It went \"wrong\"",
            "some-key",
            Map.of("a", "1"),
            ErrorStatus.BAD_REQUEST);

    private static final String PROBLEM_JSON =
            "{\"type\":\"urn:gabier:error:some-key\",\"title\":\"Some key\",\"detail\":\"It went \\\"wrong\\\"\","
                    + "\"key\":\"some-key\",\"parameters\":{\"a\":\"1\"}}";

    @Nested
    class Requests {

        @Test
        void areWrittenAsOneLineOfJson() {
            var line = new RpcRequest("1", "status").toLine();

            assertThat(line).isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"status\"}");
            StrictJson.parse(line);
        }

        @Test
        void readWhatTheyWrite() {
            assertThat(RpcRequest.parse(new RpcRequest("7", "stop").toLine())).isEqualTo(new RpcRequest("7", "stop"));
        }

        @Test
        void keepAnIntegerIdAsItIs() {
            assertThat(parse("{\"jsonrpc\":\"2.0\",\"id\":42,\"method\":\"status\"}")
                            .id())
                    .isEqualTo("42");
        }

        @Test
        void keepAStringIdQuoted() {
            assertThat(parse("{\"jsonrpc\":\"2.0\",\"id\":\"a\\\"b\",\"method\":\"status\"}")
                            .id())
                    .isEqualTo("\"a\\\"b\"");
        }

        @Test
        void keepANullIdAsTheLiteral() {
            assertThat(parse("{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"status\"}")
                            .id())
                    .isEqualTo("null");
        }

        @Test
        void ignoreTheMembersTheyDoNotKnow() {
            assertThat(parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"status\",\"params\":[1]}")
                            .method())
                    .isEqualTo("status");
        }

        @Test
        void refuseMalformedJsonWithoutAnId() {
            var failure = catchThrowableOfType(MalformedJsonException.class, () -> RpcRequest.parse("{\"jsonrpc\":"));

            assertThat(failure.key()).isEqualTo(ProtocolErrorKey.MALFORMED_JSON);
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "[]",
                    "\"status\"",
                    "null",
                    "{}",
                    "{\"id\":1,\"method\":\"status\"}",
                    "{\"jsonrpc\":\"1.0\",\"id\":1,\"method\":\"status\"}",
                    "{\"jsonrpc\":2.0,\"id\":1,\"method\":\"status\"}",
                    "{\"jsonrpc\":\"2.0\",\"id\":1}",
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":1}",
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"\"}",
                    "{\"jsonrpc\":\"2.0\",\"id\":1.5,\"method\":\"status\"}",
                    "{\"jsonrpc\":\"2.0\",\"id\":[1],\"method\":\"status\"}",
                    "{\"jsonrpc\":\"2.0\",\"id\":true,\"method\":\"status\"}",
                    "{\"jsonrpc\":\"2.0\",\"method\":\"status\"}",
                    "[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"status\"}]"
                })
        void refuseAnInvalidRequest(String line) {
            var failure = catchThrowableOfType(InvalidRequestException.class, () -> RpcRequest.parse(line));

            assertThat(failure.key()).isEqualTo(ProtocolErrorKey.INVALID_REQUEST);
            assertThat(failure.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
        }

        @Test
        void refuseAnInvalidRequestButKeepTheIdItCouldRead() {
            var failure = catchThrowableOfType(
                    InvalidRequestException.class, () -> RpcRequest.parse("{\"jsonrpc\":\"2.0\",\"id\":9}"));

            assertThat(failure.id()).isEqualTo("9");
        }

        @Test
        void refuseAnInvalidRequestWithoutAnIdWhenTheIdIsUnreadable() {
            var failure = catchThrowableOfType(
                    InvalidRequestException.class,
                    () -> RpcRequest.parse("{\"jsonrpc\":\"2.0\",\"id\":[1],\"method\":\"status\"}"));

            assertThat(failure.id()).isNull();
        }

        @Test
        void refuseARequestThatIsNotAnObject() {
            var failure = catchThrowableOfType(InvalidRequestException.class, () -> RpcRequest.parse("[]"));

            assertThat(failure.getMessage()).isEqualTo("The request is not a JSON-RPC 2.0 request object");
            assertThat(failure.id()).isNull();
        }

        private RpcRequest parse(String line) {
            return RpcRequest.parse(line);
        }
    }

    @Nested
    class Responses {

        @Test
        void aSuccessIsOneLineOfJson() {
            var line = JsonRpc.success("3", "{\"pid\":1}");

            assertThat(line).isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"pid\":1}}");
            StrictJson.parse(line);
        }

        @Test
        void aFailureCarriesTheProblemObjectInTheData() {
            var line = JsonRpc.failure("3", PROBLEM);

            assertThat(line)
                    .isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":3,\"error\":{\"code\":-32000,\"message\":\"Some key\","
                            + "\"data\":" + PROBLEM_JSON + "}}");
            StrictJson.parse(line);
        }

        @Test
        void aFailureWithoutAnIdHasANullId() {
            assertThat(JsonRpc.failure(null, PROBLEM)).startsWith("{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":");
        }

        @Test
        void aMalformedRequestIsAParseError() {
            assertThat(JsonRpc.failure("1", MalformedJsonException.at(0, "x"))).contains("\"code\":-32700");
        }

        @Test
        void anInvalidRequestIsAnInvalidRequest() {
            assertThat(JsonRpc.failure("1", InvalidRequestException.of(null, "x")))
                    .contains("\"code\":-32600");
        }

        @Test
        void anUnknownMethodIsMethodNotFound() {
            assertThat(JsonRpc.failure("1", UnknownMethodException.of("m"))).contains("\"code\":-32601");
        }

        @Test
        void anyOtherRefusalIsAServerError() {
            assertThat(JsonRpc.failure(
                            "1", GeneratorException.badRequest(CustomKey.OTHER).build()))
                    .contains("\"code\":-32000");
        }

        @Test
        void aFailureOfGabierIsAnInternalError() {
            assertThat(JsonRpc.failure("1", new IllegalStateException("secret")))
                    .contains("\"code\":-32603")
                    .contains("\"key\":\"internal-server-error\"")
                    .doesNotContain("secret");
        }

        @Test
        void aSuccessIsRead() {
            var response = RpcResponse.parse(JsonRpc.success("1", "{\"pid\":12}"));

            assertThat(response)
                    .isInstanceOfSatisfying(
                            RpcResponse.Success.class,
                            success ->
                                    assertThat(success.result()).containsExactly(entry("pid", new BigDecimal("12"))));
        }

        @Test
        void aSuccessKeepsTheResultItWasGivenWhateverTheCallerDoesWithTheMapAfterwards() {
            var given = new HashMap<String, Object>();
            given.put("pid", 12);
            var success = new RpcResponse.Success(given);

            given.put("later", 13);

            assertThat(success.result()).containsExactly(entry("pid", 12));
            assertThatThrownBy(() -> success.result().put("other", 14))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void aRefusalIsReadAsABadRequestProblem() {
            var response = RpcResponse.parse(JsonRpc.failure("1", PROBLEM));

            assertThat(response).isEqualTo(new RpcResponse.Failure(PROBLEM));
        }

        @Test
        void anInternalErrorIsReadAsAnInternalServerErrorProblem() {
            var response = RpcResponse.parse(JsonRpc.failure("1", new IllegalStateException("x")));

            assertThat(response)
                    .isInstanceOfSatisfying(
                            RpcResponse.Failure.class,
                            failure -> assertThat(failure.problem().category())
                                    .isEqualTo(ErrorStatus.INTERNAL_SERVER_ERROR));
        }

        @Test
        void everyKindOfRefusalIsReadAsABadRequest() {
            for (var failure : new GeneratorException[] {
                MalformedJsonException.at(0, "x"), InvalidRequestException.of(null, "x"), UnknownMethodException.of("m")
            }) {
                assertThat(RpcResponse.parse(JsonRpc.failure("1", failure)))
                        .isInstanceOfSatisfying(
                                RpcResponse.Failure.class,
                                read -> assertThat(read.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST));
            }
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "",
                    "not json",
                    "[]",
                    "{}",
                    "{\"jsonrpc\":\"1.0\",\"id\":1,\"result\":{}}",
                    "{\"jsonrpc\":\"2.0\",\"id\":1}",
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":1}",
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":1}",
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"data\":{}}}",
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32000}}",
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":\"x\",\"data\":{}}}",
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32000,\"data\":1}}",
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32000,\"data\":{}}}",
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32000,\"data\":{\"type\":\"t\",\"title\":\"T\","
                            + "\"detail\":\"d\",\"key\":\"k\",\"parameters\":{\"a\":1}}}}",
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32000,\"data\":{\"type\":\"t\",\"title\":\"T\","
                            + "\"detail\":\"d\",\"key\":\"k\",\"parameters\":[]}}}",
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32000,\"data\":{\"type\":\"t\",\"title\":\"T\","
                            + "\"detail\":\"d\",\"key\":1,\"parameters\":{}}}}",
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32000,\"data\":{\"type\":\"t\",\"title\":\"T\","
                            + "\"detail\":\" \",\"key\":\"k\",\"parameters\":{}}}}"
                })
        void anInvalidResponseIsAnInternalError(String line) {
            var failure = catchThrowableOfType(GeneratorException.class, () -> RpcResponse.parse(line));

            assertThat(failure.key()).isEqualTo(ProtocolErrorKey.INVALID_RESPONSE);
            assertThat(failure.status()).isEqualTo(ErrorStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        void aProblemWithABlankMemberKeepsTheRefusalOfTheProblemAsItsCause() {
            var failure = catchThrowableOfType(
                    InvalidResponseException.class,
                    () -> RpcResponse.parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32000,\"data\":"
                            + "{\"type\":\"t\",\"title\":\"T\",\"detail\":\" \",\"key\":\"k\",\"parameters\":{}}}}"));

            assertThat(failure.getCause()).isInstanceOf(MissingMandatoryValueException.class);
        }

        @Test
        void aResponseThatIsNotJsonKeepsTheParseErrorAsItsCause() {
            var failure = catchThrowableOfType(GeneratorException.class, () -> RpcResponse.parse("nope"));

            assertThat(failure.getCause()).isInstanceOf(MalformedJsonException.class);
        }
    }

    private enum CustomKey implements ErrorKey {
        OTHER;

        @Override
        public String get() {
            return "other";
        }
    }
}
