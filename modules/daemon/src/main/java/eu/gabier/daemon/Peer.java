package eu.gabier.daemon;

import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.protocol.Problem;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The client at the other end of a connection, as a method that follows something sees it: the one thing a method can
 * do with it is to listen, which makes the connection write lines that are not answers. Nothing else of the connection
 * is reachable from a method.
 */
interface Peer {

    /**
     * Makes the connection write notifications from now on, after the answer to the request that is being run. What the
     * method offers to the listener is written by the writer of the connection, never by the caller, and a client that
     * does not read does not hold up who offers.
     *
     * @param notifications the lines to write for a state that was offered (none when it tells nothing): the writer
     *     calls it, one state at a time
     * @param whenGone runs once when the connection ends, whatever the reason, with the listener that was returned
     * @throws eu.gabier.shared.error.domain.GeneratorException when the connection is already listening
     * @throws IllegalStateException when there is no client to write to
     */
    Listener listen(Function<List<DiagnosedFile>, List<String>> notifications, Consumer<Listener> whenGone);

    /** A request that is not run for a client on a connection, as in a test of a method: nothing can listen. */
    static Peer nobody() {
        return (notifications, whenGone) -> {
            throw new IllegalStateException("There is no client to write to");
        };
    }

    /** What the method keeps to tell the client. Neither call ever blocks. */
    interface Listener {

        /**
         * Offers the state of the project to write. A state that the writer has not taken yet is replaced by this one,
         * so a client that reads slowly is told the latest state and not every state: the memory stays bounded and the
         * order is kept. Ignored once the connection ended or the subscription was ended.
         */
        void offer(List<DiagnosedFile> state);

        /** Says something to the person who reads: written before the state that is offered after it, in the order given. */
        void say(String notice);

        /** Ends the subscription for this reason, once the last state offered is written; nothing is written after. */
        void end(Problem reason);
    }
}
