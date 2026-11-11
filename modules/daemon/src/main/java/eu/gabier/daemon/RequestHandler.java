package eu.gabier.daemon;

import eu.gabier.protocol.InvalidRequestException;
import eu.gabier.protocol.JsonRpc;
import eu.gabier.protocol.MalformedJsonException;
import eu.gabier.protocol.RpcRequest;
import eu.gabier.protocol.UnknownMethodException;
import eu.gabier.shared.error.domain.GeneratorException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Turns a line of the client into the line to answer, whatever the line is: a line that is not JSON, not a request, or
 * for a method that does not exist, or whose parameters are not the ones the method takes, is answered with a JSON-RPC
 * error that holds the problem object, and the daemon goes on. No socket in here: the lines are strings.
 */
final class RequestHandler {

    /**
     * The answer to a request.
     *
     * @param line the line to write
     * @param afterReply what to do once it is written
     */
    record Reply(String line, Runnable afterReply) {

        static Reply of(String line) {
            return new Reply(line, () -> {});
        }
    }

    private final Map<String, DaemonMethod> methods = new LinkedHashMap<>();
    private final Optional<RuntimeException> refusal;
    private final Consumer<String> log;

    RequestHandler(Collection<DaemonMethod> methods) {
        this(methods, line -> {});
    }

    RequestHandler(Collection<DaemonMethod> methods, Consumer<String> log) {
        methods.forEach(method -> this.methods.put(method.name(), method));
        refusal = Optional.empty();
        this.log = log;
    }

    private RequestHandler(RuntimeException refusal) {
        this.refusal = Optional.of(refusal);
        this.log = line -> {};
    }

    /** A handler that runs no method: every well-formed request is answered with this refusal. */
    static RequestHandler refusing(RuntimeException refusal) {
        return new RequestHandler(refusal);
    }

    /** The answer to a line, for a request that runs for no client on a connection (a test of a method). */
    Reply handle(String line) {
        return handle(line, Peer.nobody());
    }

    /** The answer to a line, for a client that a method may listen for ({@link StreamingMethod}). */
    Reply handle(String line, Peer peer) {
        RpcRequest request;
        try {
            request = RpcRequest.parse(line);
        } catch (InvalidRequestException invalid) {
            return Reply.of(JsonRpc.failure(invalid.id(), invalid));
        } catch (MalformedJsonException malformed) {
            return Reply.of(JsonRpc.failure(null, malformed));
        }
        if (refusal.isPresent()) {
            return Reply.of(JsonRpc.failure(request.id(), refusal.get()));
        }
        var method = methods.get(request.method());
        if (method == null) {
            return Reply.of(JsonRpc.failure(request.id(), UnknownMethodException.of(request.method())));
        }

        return call(method, request, peer);
    }

    private Reply call(DaemonMethod method, RpcRequest request, Peer peer) {
        try {
            checkParameters(method, request.params());
            var outcome = method instanceof StreamingMethod streaming
                    ? streaming.call(request.params(), peer)
                    : method.call(request.params());

            return new Reply(JsonRpc.success(request.id(), outcome.resultJson()), outcome.afterReply());
        } catch (RuntimeException failure) {
            // Whatever a method throws, the client gets a problem object and the daemon goes on.
            if (!(failure instanceof GeneratorException) || failure.getCause() != null) {
                FailureLog.lines(request.method(), failure).forEach(log);
            }
            return Reply.of(JsonRpc.failure(request.id(), failure));
        }
    }

    /** Refuses the request that carries a parameter the method does not take, or lacks one it needs. */
    private static void checkParameters(DaemonMethod method, Map<String, String> params) {
        var needed = method.parameters();
        params.keySet().stream()
                .filter(name -> !needed.contains(name))
                .findFirst()
                .ifPresent(name -> {
                    throw ParameterException.unknown(method.name(), name);
                });
        needed.stream()
                .sorted()
                .filter(name -> !params.containsKey(name))
                .findFirst()
                .ifPresent(name -> {
                    throw ParameterException.missing(method.name(), name);
                });
    }
}
