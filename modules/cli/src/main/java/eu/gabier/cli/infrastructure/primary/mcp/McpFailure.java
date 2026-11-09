package eu.gabier.cli.infrastructure.primary.mcp;

import eu.gabier.protocol.Json;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * A request that is answered with a JSON-RPC error: what the transport and the protocol of MCP call a protocol error,
 * as opposed to a tool that ran and failed. It carries the code, the message and, for the error that has one, the JSON
 * of its {@code data}. It is thrown and caught inside the server, so it has no stack trace.
 */
final class McpFailure extends RuntimeException {

    private static final int PARSE_ERROR = -32700;
    private static final int INVALID_REQUEST = -32600;
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;
    /** {@code UnsupportedProtocolVersion}, the code that revision 2026-07-28 of MCP reserves for it. */
    private static final int UNSUPPORTED_PROTOCOL_VERSION = -32022;

    private final int code;
    private final String message;
    private final @Nullable String data;

    private McpFailure(int code, String message, @Nullable String data) {
        super(message, null, false, false);
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** The line is not JSON. */
    static McpFailure parseError() {
        return new McpFailure(PARSE_ERROR, "Parse error", null);
    }

    /** The line is JSON, and is not a message of JSON-RPC 2.0 (or one that MCP allows: a batch is not). */
    static McpFailure invalidRequest() {
        return new McpFailure(INVALID_REQUEST, "Invalid Request", null);
    }

    /** The message is a request, and says what is wrong with it. */
    static McpFailure invalidRequest(String detail) {
        return new McpFailure(INVALID_REQUEST, "Invalid Request: " + detail, null);
    }

    /** No method of that name. */
    static McpFailure methodNotFound() {
        return new McpFailure(METHOD_NOT_FOUND, "Method not found", null);
    }

    /** A parameter is missing or is not what the method reads. */
    static McpFailure invalidParams(String detail) {
        return new McpFailure(INVALID_PARAMS, "Invalid params: " + detail, null);
    }

    /** The request names a protocol version the server does not serve; the data lists the ones it does. */
    static McpFailure unsupportedProtocolVersion(String requested, List<String> supported) {
        var versions = supported.stream().map(Json::quote).collect(Collectors.joining(",", "[", "]"));

        return new McpFailure(
                UNSUPPORTED_PROTOCOL_VERSION,
                "Unsupported protocol version",
                "{\"supported\":" + versions + ",\"requested\":" + Json.quote(requested) + "}");
    }

    /** The error of JSON-RPC, answering the request of this id (its JSON, {@code null} when it has none). */
    String toJson(@Nullable String id) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + (id == null ? "null" : id) + ",\"error\":{\"code\":" + code
                + ",\"message\":" + Json.quote(message) + (data == null ? "" : ",\"data\":" + data) + "}}";
    }
}
