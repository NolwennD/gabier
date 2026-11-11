package eu.gabier.daemon;

import java.util.Map;

/**
 * A method that answers once, then goes on telling the client what changes on the same connection, as notifications
 * (JSON-RPC messages with no {@code id}): the handler gives it the {@link Peer} of the client it runs for.
 */
interface StreamingMethod extends DaemonMethod {

    /**
     * Runs the method for a client. The answer is written first; what the method listens to is written after it.
     *
     * @param params the parameters of the request, exactly those of {@link #parameters()}
     */
    Outcome call(Map<String, String> params, Peer peer);

    /** The method run for no client: nothing can listen, so a method of this kind fails. */
    @Override
    default Outcome call(Map<String, String> params) {
        return call(params, Peer.nobody());
    }
}
