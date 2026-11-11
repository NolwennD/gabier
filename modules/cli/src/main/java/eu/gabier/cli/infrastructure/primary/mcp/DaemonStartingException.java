package eu.gabier.cli.infrastructure.primary.mcp;

import eu.gabier.shared.error.domain.GeneratorException;

/** A call of {@code status} came while the daemon that the server started for its launch project is not yet listening. */
final class DaemonStartingException extends GeneratorException {

    private DaemonStartingException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    /** No daemon answers yet, because the one the server started when the host connected is still coming up. */
    static DaemonStartingException started() {
        return new DaemonStartingException(GeneratorException.badRequest(McpErrorKey.DAEMON_STARTING)
                .message("The daemon is starting: the server started it when the host connected and it does not"
                        + " answer yet; call status again in a moment"));
    }
}
