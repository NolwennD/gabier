package fr.craft.gabier.daemon;

import org.jspecify.annotations.Nullable;

/** Jdtls cannot be started, does not become ready, or stops answering: what the adapter says when it gives up. */
final class JdtlsException extends RuntimeException {

    JdtlsException(String message) {
        super(message);
    }

    JdtlsException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
