package fr.craft.gabier.protocol;

import fr.craft.gabier.shared.error.domain.Assert;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The result of the {@code status} method: what a running daemon says about itself.
 *
 * @param version the protocol version the daemon speaks
 * @param pid the process id of the daemon
 * @param uptime the time since the daemon started listening
 */
public record DaemonStatus(String version, long pid, Duration uptime) {

    public DaemonStatus {
        Assert.notBlank("version", version);
        Assert.field("pid", pid).min(1);
        Assert.notNull("uptime", uptime);
        Assert.field("uptime", uptime.toMillis()).min(0);
    }

    /** The result as JSON, the members in a stable order. */
    public String toJson() {
        return "{\"version\":" + Json.quote(version) + ",\"pid\":" + pid + ",\"uptimeMillis\":" + uptime.toMillis()
                + "}";
    }

    /**
     * Reads the result of a {@code status} response.
     *
     * @throws InvalidResponseException when a member is missing or has the wrong type
     */
    public static DaemonStatus of(Map<String, @Nullable Object> result) {
        if (!(result.get("version") instanceof String version)) {
            throw member("version");
        }

        return new DaemonStatus(version, integer(result, "pid"), Duration.ofMillis(integer(result, "uptimeMillis")));
    }

    private static long integer(Map<String, @Nullable Object> result, String name) {
        try {
            if (result.get(name) instanceof BigDecimal number) {
                return number.longValueExact();
            }
        } catch (ArithmeticException e) {
            throw InvalidResponseException.of("The member " + name + " of the status is not an integer", e);
        }

        throw member(name);
    }

    private static InvalidResponseException member(String name) {
        return InvalidResponseException.of("The status has no member " + name + " of the right type", null);
    }
}
