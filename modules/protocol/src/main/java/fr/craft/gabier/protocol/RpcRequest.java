package fr.craft.gabier.protocol;

import fr.craft.gabier.shared.error.domain.Assert;
import java.math.BigDecimal;
import java.util.Map;

/**
 * A JSON-RPC 2.0 request, as one line of JSON. The members other than {@code id} and {@code method} ({@code params})
 * are ignored: no method of the daemon takes any yet. A request without an {@code id} (a notification) and a batch
 * (an array of requests) are not supported: they are invalid requests.
 *
 * @param id the id as JSON text: an integer, a quoted string, or {@code null}
 * @param method the name of the method
 */
public record RpcRequest(String id, String method) {

    public RpcRequest {
        Assert.notBlank("id", id);
        Assert.notBlank("method", method);
    }

    /** The request as one line of JSON, without its line break. */
    public String toLine() {
        return "{\"jsonrpc\":\"" + JsonRpc.VERSION + "\",\"id\":" + id + ",\"method\":" + Json.quote(method) + "}";
    }

    /**
     * Reads a request.
     *
     * @throws MalformedJsonException when the line is not JSON
     * @throws InvalidRequestException when it is JSON but not a request
     */
    public static RpcRequest parse(String line) {
        if (!(Json.parse(line) instanceof Map<?, ?> request)) {
            throw InvalidRequestException.of(null, "The request is not a JSON-RPC 2.0 request object");
        }
        var id = id(request);
        if (!JsonRpc.VERSION.equals(request.get("jsonrpc"))) {
            throw InvalidRequestException.of(id, "The member jsonrpc must be the string " + JsonRpc.VERSION);
        }
        if (!(request.get("method") instanceof String method) || method.isEmpty()) {
            throw InvalidRequestException.of(id, "The member method must be a non-empty string");
        }

        return new RpcRequest(id, method);
    }

    private static String id(Map<?, ?> request) {
        if (!request.containsKey("id")) {
            throw InvalidRequestException.of(null, "The member id is required");
        }

        return switch (request.get("id")) {
            case null -> "null";
            case String text -> Json.quote(text);
            case BigDecimal number
            when number.stripTrailingZeros().scale() <= 0 ->
                number.toBigInteger().toString();
            default -> throw InvalidRequestException.of(null, "The member id must be a string, an integer or null");
        };
    }
}
