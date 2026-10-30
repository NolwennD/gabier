package fr.craft.gabier.daemon;

/**
 * A method a client can call on the daemon: one entry of its method table, which {@code DaemonServer} builds. A method
 * takes no parameters yet.
 */
interface DaemonMethod {

    /** The name in the {@code method} member of the request. */
    String name();

    /** Runs the method. A refusal or a failure is an exception: the handler answers it as a problem object. */
    Outcome call();

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
