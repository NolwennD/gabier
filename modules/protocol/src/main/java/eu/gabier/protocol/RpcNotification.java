package eu.gabier.protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A JSON-RPC 2.0 notification of the daemon: a message with no {@code id}, which a client does not answer. The daemon
 * sends them on the connection of a client that asked to follow something (the method {@code watch}), after the answer
 * to that request, in the order of what they tell.
 *
 * @param method what the notification is: {@value JsonRpc#PROBLEMS} (the params are a {@link DiagnosedFiles}), {@value
 *     JsonRpc#NOTICE} or {@value JsonRpc#ENDED}
 * @param params the members of its {@code params}
 */
public record RpcNotification(String method, Map<String, @Nullable Object> params) implements RpcMessage {

    public RpcNotification(String method, Map<String, @Nullable Object> params) {
        this.method = method;
        this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    static RpcNotification of(Map<?, ?> message) {
        if (!(message.get("method") instanceof String method) || method.isEmpty()) {
            throw InvalidResponseException.of("The method of a notification is not a name", null);
        }
        if (!(message.get("params") instanceof Map<?, ?> given)) {
            throw InvalidResponseException.of("The notification " + method + " has no params object", null);
        }
        var params = new LinkedHashMap<String, @Nullable Object>();
        given.forEach((name, value) -> params.put((String) name, value));

        return new RpcNotification(method, params);
    }

    /**
     * The problems of the files, for a notification of the method {@value JsonRpc#PROBLEMS}.
     *
     * @throws InvalidResponseException when the notification is another one, or the files are not the expected shape
     */
    public DiagnosedFiles problems() {
        if (!JsonRpc.PROBLEMS.equals(method)) {
            throw InvalidResponseException.of("The notification " + method + " does not tell problems", null);
        }

        return DiagnosedFiles.of(params);
    }

    /**
     * What to say to the person who reads, for a notification of the method {@value JsonRpc#NOTICE}.
     *
     * @throws InvalidResponseException when the notification is another one, or has no message
     */
    public String notice() {
        if (!JsonRpc.NOTICE.equals(method) || !(params.get("message") instanceof String message)) {
            throw InvalidResponseException.of("The notification " + method + " is not a notice with a message", null);
        }

        return message;
    }

    /**
     * The reason a subscription ended, for a notification of the method {@value JsonRpc#ENDED}: the problem the params
     * carry as the error object of a failed request.
     *
     * @throws InvalidResponseException when the notification is another one, or the problem is not the expected shape
     */
    public Problem ended() {
        if (!JsonRpc.ENDED.equals(method)) {
            throw InvalidResponseException.of("The notification " + method + " does not end a subscription", null);
        }

        return RpcResponse.problem(params);
    }
}
