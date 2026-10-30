package fr.craft.gabier.daemon;

import fr.craft.gabier.protocol.DaemonStatus;
import java.util.function.Supplier;

/** {@code status}: the version, the pid and the uptime of the daemon. */
record StatusMethod(Supplier<DaemonStatus> status) implements DaemonMethod {

    @Override
    public String name() {
        return "status";
    }

    @Override
    public Outcome call() {
        return Outcome.of(status.get().toJson());
    }
}
