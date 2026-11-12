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

    /** The method of the notification that ends a subscription: its params are the error object of the reason. */
    public static final String ENDED = "ended";

    /** The method of the notification that tells the problems of some files: its params are a {@link DiagnosedFiles}. */
    public static final String PROBLEMS = "problems";

    /**
     * The method of the notification that tells the tests a change affects: its params are a {@link SelectedTests}, never
     * with no test.
     */
    public static final String AFFECTED_TESTS = "affected-tests";

    /**
     * The method of the notification that tells a refusal the subscription goes on after: its params are the error object
     * of the refusal, as for {@value #ENDED}.
     */
    public static final String REFUSAL = "refusal";

    /** The method of the notification that says something to the person who reads: its params hold a {@code message}. */
    public static final String NOTICE = "notice";

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
        return "{\"jsonrpc\":\"" + VERSION + "\",\"id\":" + (id == null ? "null" : id) + ",\"error\":"
                + errorObject(problem) + "}";
    }

    /**
     * A message that is not the answer to a request: a method and its {@code params}, with no {@code id}, so that the
     * client answers nothing. The daemon sends them on the connection of a client that asked to follow something, after
     * the answer to its request.
     *
     * @param paramsJson the {@code params}, a JSON object
     */
    public static String notification(String method, String paramsJson) {
        return "{\"jsonrpc\":\"" + VERSION + "\",\"method\":" + Json.quote(method) + ",\"params\":" + paramsJson + "}";
    }

    /** The notification that tells the problems of some files, as the result of {@code diagnostics} does. */
    public static String problems(DiagnosedFiles diagnosed) {
        return notification(PROBLEMS, diagnosed.toJson());
    }

    /** The notification that tells the tests a change affects, as the result of {@code affected-tests} gives them. */
    public static String affectedTests(SelectedTests selected) {
        return notification(AFFECTED_TESTS, selected.toJson());
    }

    /** The notification that tells a refusal and leaves the subscription going: the error object a failed request carries. */
    public static String refusal(Problem problem) {
        return notification(REFUSAL, errorObject(problem));
    }

    /** The notification that says something to the person who reads, such as why a delay is longer than usual. */
    public static String notice(String message) {
        return notification(NOTICE, "{\"message\":" + Json.quote(message) + "}");
    }

    /** The notification that ends a subscription for a reason: the error object a failed request carries. */
    public static String ended(Problem problem) {
        return notification(ENDED, errorObject(problem));
    }

    private static String errorObject(Problem problem) {
        return "{\"code\":" + code(problem) + ",\"message\":" + Json.quote(problem.title()) + ",\"data\":"
                + problem.toJson() + "}";
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
