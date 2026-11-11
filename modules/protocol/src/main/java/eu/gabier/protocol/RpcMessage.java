package eu.gabier.protocol;

import java.util.Map;

/**
 * A line the daemon writes: the answer to a request ({@link RpcResponse}), or a notification it sends on its own
 * ({@link RpcNotification}) to a client that asked to follow something.
 */
public sealed interface RpcMessage permits RpcResponse, RpcNotification {

    /**
     * Reads a line of the daemon.
     *
     * @throws InvalidResponseException when the line is not a message of gabier
     */
    static RpcMessage parse(String line) {
        Object json;
        try {
            json = Json.parse(line);
        } catch (MalformedJsonException e) {
            throw InvalidResponseException.of("The daemon wrote something that is not JSON", e);
        }
        if (json instanceof Map<?, ?> message && message.containsKey("method") && !message.containsKey("id")) {
            return RpcNotification.of(message);
        }

        return RpcResponse.parse(line);
    }
}
