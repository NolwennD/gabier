package fr.craft.gabier.protocol;

import fr.craft.gabier.shared.error.domain.AssertionException;
import fr.craft.gabier.shared.error.domain.ErrorStatus;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** What the daemon answered to a request: a result, or the problem object of an error. */
public sealed interface RpcResponse {

    /**
     * The request succeeded.
     *
     * @param result the members of the result object
     */
    record Success(Map<String, @Nullable Object> result) implements RpcResponse {

        public Success {
            result = Collections.unmodifiableMap(new LinkedHashMap<>(result));
        }
    }

    /**
     * The request was refused, or failed.
     *
     * @param problem the problem object found in the {@code data} of the error, its category given by the code
     */
    record Failure(Problem problem) implements RpcResponse {}

    /**
     * Reads a response.
     *
     * @throws InvalidResponseException when the line is not a response of gabier
     */
    static RpcResponse parse(String line) {
        Object json;
        try {
            json = Json.parse(line);
        } catch (MalformedJsonException e) {
            throw InvalidResponseException.of("The daemon answered with something that is not JSON", e);
        }
        if (!(json instanceof Map<?, ?> response) || !JsonRpc.VERSION.equals(response.get("jsonrpc"))) {
            throw invalid("The answer is not a JSON-RPC 2.0 response object");
        }
        if (response.get("result") instanceof Map<?, ?> result) {
            return new Success(members(result));
        }
        if (response.get("error") instanceof Map<?, ?> error) {
            return new Failure(problem(error));
        }

        throw invalid("The answer has neither a result object nor an error object");
    }

    private static Problem problem(Map<?, ?> error) {
        if (!(error.get("code") instanceof BigDecimal code) || !(error.get("data") instanceof Map<?, ?> data)) {
            throw invalid("The error of the answer has no code or no problem object in its data");
        }
        var category =
                code.intValue() == JsonRpc.INTERNAL_ERROR ? ErrorStatus.INTERNAL_SERVER_ERROR : ErrorStatus.BAD_REQUEST;
        var parameters = new LinkedHashMap<String, String>();
        if (!(data.get("parameters") instanceof Map<?, ?> given)) {
            throw invalid("The problem object has no parameters object");
        }
        given.forEach((name, value) -> parameters.put((String) name, text(value, "a parameter")));
        try {
            return new Problem(
                    text(data.get("type"), "type"),
                    text(data.get("title"), "title"),
                    text(data.get("detail"), "detail"),
                    text(data.get("key"), "key"),
                    parameters,
                    category);
        } catch (AssertionException e) {
            // A blank member: a problem object that the daemon should not have written.
            throw InvalidResponseException.of("The problem object has a blank member", e);
        }
    }

    private static String text(@Nullable Object value, String name) {
        if (value instanceof String text) {
            return text;
        }

        throw invalid("The problem object has no string " + name);
    }

    private static Map<String, @Nullable Object> members(Map<?, ?> given) {
        var members = new LinkedHashMap<String, @Nullable Object>();
        given.forEach((name, value) -> members.put((String) name, value));

        return members;
    }

    private static InvalidResponseException invalid(String message) {
        return InvalidResponseException.of(message, null);
    }
}
