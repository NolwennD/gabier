package eu.gabier.cli.infrastructure.primary.mcp;

import eu.gabier.protocol.Json;
import eu.gabier.protocol.MalformedJsonException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One line the client wrote, read as what the server does with it: a request to answer, a message to leave without an
 * answer (a notification, an answer of the client), or a line to refuse.
 */
sealed interface Incoming {

    /**
     * A request: a method asked, and an id to answer under.
     *
     * @param id the id as JSON, a text or an integer, kept as it came
     * @param method the name of the method
     * @param params the members of its {@code params}, none when it has none
     */
    record Request(String id, String method, Map<String, @Nullable Object> params) implements Incoming {}

    /** A message that is never answered: a notification (it has no id), or an answer of the client. */
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
            id = idOf(message.get("id"));
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
            return new Silent();
        }
        if (!message.containsKey("params")) {
            return new Request(id, method, Map.of());
        }
        if (!(message.get("params") instanceof Map<?, ?> params)) {
            return new Refused(id, McpFailure.invalidParams("params must be an object"));
        }

        return new Request(id, method, members(params));
    }

    /** The id as JSON when it is a text or an integer, else none. */
    private static @Nullable String idOf(@Nullable Object id) {
        if (id instanceof String text) {
            return Json.quote(text);
        }
        if (id instanceof BigDecimal number) {
            try {
                return String.valueOf(number.longValueExact());
            } catch (ArithmeticException notAnInteger) {
                return null;
            }
        }

        return null;
    }

    private static Map<String, @Nullable Object> members(Map<?, ?> given) {
        var members = new LinkedHashMap<String, @Nullable Object>();
        given.forEach((name, value) -> members.put((String) name, value));

        return members;
    }
}
