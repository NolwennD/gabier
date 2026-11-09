package eu.gabier.cli.infrastructure.primary.mcp;

import eu.gabier.protocol.Json;
import eu.gabier.protocol.MalformedJsonException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * One line the client wrote, read as what the server does with it: a request to answer, a notification, a message to
 * leave without an answer (an answer of the client), or a line to refuse.
 */
sealed interface Incoming {

    /**
     * A request: a method asked, and an id to answer under.
     *
     * @param id the id as JSON, written as it came (a text, or a number such as {@code 1.0} or {@code 1e2})
     * @param key what tells this id from another, so that {@code 1} and {@code 1.0}, which are the same number, are one
     * @param method the name of the method
     * @param params the members of its {@code params}, none when it has none
     */
    record Request(String id, String key, String method, Map<String, @Nullable Object> params) implements Incoming {}

    /**
     * A notification: a method with no id, which is never answered.
     *
     * @param method the name of the method
     * @param params the members of its {@code params}, none when it has none or when they are not an object
     */
    record Notification(String method, Map<String, @Nullable Object> params) implements Incoming {}

    /** A message that is never answered and says nothing the server reads: an answer of the client. */
    record Silent() implements Incoming {}

    /**
     * A line that is refused, and answered with the error.
     *
     * @param id the id as JSON when the line gave a readable one, else none
     * @param failure the error to answer
     */
    record Refused(@Nullable String id, McpFailure failure) implements Incoming {}

    /** Reads a line. */
    static Incoming of(String line) {
        Object json;
        try {
            json = Json.parse(line);
        } catch (MalformedJsonException notJson) {
            return new Refused(null, McpFailure.parseError());
        }
        if (!(json instanceof Map<?, ?> message)) {
            return new Refused(null, McpFailure.invalidRequest());
        }
        @Nullable String id = null;
        if (message.containsKey("id")) {
            id = jsonIdOf(message.get("id"));
            if (id == null) {
                return new Refused(null, McpFailure.invalidRequest());
            }
        }
        if (!"2.0".equals(message.get("jsonrpc"))) {
            return new Refused(id, McpFailure.invalidRequest());
        }
        if (!message.containsKey("method") && (message.containsKey("result") || message.containsKey("error"))) {
            return new Silent();
        }
        if (!(message.get("method") instanceof String method)) {
            return new Refused(id, McpFailure.invalidRequest());
        }
        if (id == null) {
            return new Notification(
                    method, message.get("params") instanceof Map<?, ?> params ? members(params) : Map.of());
        }
        var key = keyOf(message.get("id")).orElseThrow();
        if (!message.containsKey("params")) {
            return new Request(id, key, method, Map.of());
        }
        if (!(message.get("params") instanceof Map<?, ?> params)) {
            return new Refused(id, McpFailure.invalidParams("params must be an object"));
        }

        return new Request(id, key, method, members(params));
    }

    /**
     * What tells a request id, or the {@code requestId} of a cancellation, from another: the text itself, or the value
     * of the number, whatever its notation; nothing for a value that is neither.
     */
    static Optional<String> keyOf(@Nullable Object id) {
        if (id instanceof String text) {
            return Optional.of(Json.quote(text));
        }
        if (id instanceof BigDecimal number) {
            return Optional.of(number.stripTrailingZeros().toPlainString());
        }

        return Optional.empty();
    }

    /**
     * The id as JSON when it is a text or a whole number, else none: written as it came, so that {@code 1.0} and {@code
     * 1e2}, or a number too large for a {@code long}, are given back unchanged.
     */
    static @Nullable String jsonIdOf(@Nullable Object id) {
        if (id instanceof String text) {
            return Json.quote(text);
        }
        if (id instanceof BigDecimal number && number.stripTrailingZeros().scale() <= 0) {
            return Json.sourceOf(number);
        }

        return null;
    }

    private static Map<String, @Nullable Object> members(Map<?, ?> given) {
        var members = new LinkedHashMap<String, @Nullable Object>();
        given.forEach((name, value) -> members.put((String) name, value));

        return members;
    }
}
