package fr.craft.gabier.protocol;

/**
 * Where the jdtls of the daemon is, as {@code status} says it: it is started by the first request that needs it, and
 * stays up until the daemon stops. A start that failed stays {@link #FAILED} until the next request tries again.
 */
public enum JdtlsState {
    NOT_STARTED("not-started"),
    STARTING("starting"),
    READY("ready"),
    FAILED("failed");

    private final String text;

    JdtlsState(String text) {
        this.text = text;
    }

    /** The name written in the result and shown to the user. */
    public String text() {
        return text;
    }
}
