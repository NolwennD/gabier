package eu.gabier.daemon;

import java.util.Map;
import java.util.Set;

/**
 * A method a client can call on the daemon: one entry of its method table, which {@code DaemonServer} builds. A method
 * declares the parameters it needs ({@link #parameters()}); the handler refuses the request that lacks one or carries
 * another before the method is called, so {@link #call(Map)} reads them without checking.
 */
interface DaemonMethod {

    /** The name in the {@code method} member of the request. */
    String name();

    /** The names of the parameters the method needs, all of them required: none by default. */
    default Set<String> parameters() {
        return Set.of();
    }

    /**
     * Runs the method. A refusal or a failure is an exception: the handler answers it as a problem object.
     *
     * @param params the parameters of the request, exactly those of {@link #parameters()}
     */
    Outcome call(Map<String, String> params);

    /**
     * What a method gives back.
     *
     * @param resultJson the {@code result} of the response, as JSON
     * @param afterReply runs once the response is written (the daemon stops after it said it would)
     */
    record Outcome(String resultJson, Runnable afterReply) {

        static Outcome of(String resultJson) {
            return new Outcome(resultJson, () -> {});
        }
    }
}
