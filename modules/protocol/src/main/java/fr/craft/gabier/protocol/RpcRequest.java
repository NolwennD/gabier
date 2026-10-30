package fr.craft.gabier.protocol;

import fr.craft.gabier.shared.error.domain.Assert;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * A JSON-RPC 2.0 request, as one line of JSON. Its {@code params} are an object whose members are strings, the way
 * every method of the daemon takes its parameters; the members other than {@code id}, {@code method} and {@code params}
 * are ignored. A request without an {@code id} (a notification) and a batch (an array of requests) are not supported:
 * they are invalid requests.
 *
 * @param id the id as JSON text: an integer, a quoted string, or {@code null}
 * @param method the name of the method
 * @param params the parameters by name, none when the request carries no {@code params}
 */
public record RpcRequest(String id, String method, Map<String, String> params) {

    public RpcRequest {
        Assert.notBlank("id", id);
        Assert.notBlank("method", method);
        Assert.notNull("params", params);
        params = Collections.unmodifiableMap(new TreeMap<>(params));
    }

    /** A request that carries no parameters. */
    public RpcRequest(String id, String method) {
        this(id, method, Map.of());
    }

    /** The request as one line of JSON, without its line break; the parameters are written sorted by name. */
    public String toLine() {
        return """
            {"jsonrpc":"%s","id":%s,"method":%s%s}\
            """.formatted(JsonRpc.VERSION, id, Json.quote(method), paramsJson());
    }

    private String paramsJson() {
        if (params.isEmpty()) {
            return "";
        }

        return params.entrySet().stream()
                .map(param -> Json.quote(param.getKey()) + ":" + Json.quote(param.getValue()))
                .collect(Collectors.joining(",", ",\"params\":{", "}"));
    }

    /**
     * Reads a request.
     *
     * @throws MalformedJsonException when the line is not JSON
     * @throws InvalidRequestException when it is JSON but not a request, or when its {@code params} are not an object
     *     of strings
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

        return new RpcRequest(id, method, params(id, request));
    }

    private static Map<String, String> params(String id, Map<?, ?> request) {
        if (!request.containsKey("params")) {
            return Map.of();
        }
        if (!(request.get("params") instanceof Map<?, ?> members)) {
            throw InvalidRequestException.of(id, "The member params must be an object", "params");
        }
        var params = new TreeMap<String, String>();
        members.forEach((name, value) -> {
            if (!(value instanceof String text)) {
                throw InvalidRequestException.of(
                        id, "The parameter " + Json.quote(name.toString()) + " must be a string", name.toString());
            }
            params.put(name.toString(), text);
        });

        return params;
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
