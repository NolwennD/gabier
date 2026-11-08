package eu.gabier.daemon;

import eu.gabier.protocol.DaemonStatus;
import java.util.Map;
import java.util.function.Supplier;

/**
 * {@code start}: starts the jdtls of the project in the background and answers at once with the status the daemon is in
 * then, the same result as {@code status}. A jdtls that is starting or ready is left alone.
 */
record StartMethod(Runnable warmUp, Supplier<DaemonStatus> status) implements DaemonMethod {

    @Override
    public String name() {
        return "start";
    }

    @Override
    public Outcome call(Map<String, String> params) {
        warmUp.run();

        return Outcome.of(status.get().toJson());
    }
}
