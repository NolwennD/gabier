package eu.gabier.protocol;

import eu.gabier.shared.error.domain.ErrorStatus;
import org.jspecify.annotations.Nullable;

/**
 * The lines the daemon writes, in JSON-RPC 2.0, one JSON object per line. An error is a JSON-RPC error whose {@code
 * data} holds the problem object ({@link Problem}); its {@code message} is the title of the problem, and its {@code
 * code} says who is at fault, which is how a client gets back the category of the problem, that the JSON of the problem
 * does not carry: {@value #INTERNAL_ERROR} is a failure of gabier, any other code a refusal of the request.
 */
public final class JsonRpc {

    /** The member of a problem object that names the parameter a request is refused for. */
    public static final String PARAMETER = "parameter";

    static final String VERSION = "2.0";

    static final int PARSE_ERROR = -32700;
    static final int INVALID_REQUEST = -32600;
    static final int METHOD_NOT_FOUND = -32601;
    /**
     * "Invalid params": the refusal of a request because of one of its parameters (not an object of strings, unknown,
     * missing, or with a value that cannot be read). Such a problem object names the parameter in its {@value
     * #PARAMETER} member, which is how the code is told, whatever the key of the problem.
     */
    static final int INVALID_PARAMS = -32602;
    /** The first of the codes the specification leaves to the server: the request is refused, for another reason. */
    static final int REFUSED = -32000;

    static final int INTERNAL_ERROR = -32603;

    private JsonRpc() {}

    /** The answer to a request: its id and the JSON of the result. */
    public static String success(String id, String resultJson) {
        return "{\"jsonrpc\":\"" + VERSION + "\",\"id\":" + id + ",\"result\":" + resultJson + "}";
    }

    /** The answer to a request that failed, as the problem object that any exception gives. */
    public static String failure(@Nullable String id, Throwable cause) {
        return failure(id, ProblemMapper.toProblem(cause));
    }

    /**
     * The answer to a request that was refused or that failed.
     *
     * @param id the id of the request as JSON text, null when it could not be read
     */
    public static String failure(@Nullable String id, Problem problem) {
        return "{\"jsonrpc\":\"" + VERSION + "\",\"id\":" + (id == null ? "null" : id) + ",\"error\":{\"code\":"
                + code(problem) + ",\"message\":" + Json.quote(problem.title()) + ",\"data\":" + problem.toJson()
                + "}}";
    }

    private static int code(Problem problem) {
        if (problem.key().equals(ProtocolErrorKey.MALFORMED_JSON.get())) {
            return PARSE_ERROR;
        }
        if (problem.key().equals(ProtocolErrorKey.UNKNOWN_METHOD.get())) {
            return METHOD_NOT_FOUND;
        }
        if (problem.category() == ErrorStatus.INTERNAL_SERVER_ERROR) {
            return INTERNAL_ERROR;
        }
        if (problem.parameters().containsKey(PARAMETER)) {
            return INVALID_PARAMS;
        }

        return problem.key().equals(ProtocolErrorKey.INVALID_REQUEST.get()) ? INVALID_REQUEST : REFUSED;
    }
}
