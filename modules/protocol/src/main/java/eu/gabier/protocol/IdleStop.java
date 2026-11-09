package eu.gabier.protocol;

import eu.gabier.shared.error.domain.Assert;
import java.time.Duration;

/**
 * The rule by which a daemon stops itself when nobody uses it, as the {@code status} method says it: the member
 * {@code idle} of its result, present only when the rule is on.
 *
 * @param timeout how long the daemon waits with no connection before it stops
 * @param left what is left of that wait when the status was asked, between nothing and the whole delay
 */
public record IdleStop(Duration timeout, Duration left) {

    public IdleStop {
        Assert.notNull("timeout", timeout);
        Assert.field("timeout", timeout.toMillis()).min(1);
        Assert.notNull("left", left);
        Assert.field("left", left.toMillis()).min(0).max(timeout.toMillis());
    }

    /** The members of the result: the delay and the time left, in milliseconds. */
    String toJson() {
        return "{\"timeoutMillis\":%d,\"leftMillis\":%d}".formatted(timeout.toMillis(), left.toMillis());
    }
}
