package fr.craft.gabier.cli;

import fr.craft.gabier.shared.error.domain.GeneratorException;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/** The cli cannot talk to the daemon. */
final class DaemonException extends GeneratorException {

    private DaemonException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    /** Nothing answers on the socket: no daemon, or one that was killed and left its socket file. */
    static DaemonException notRunning(Path socket) {
        return new DaemonException(GeneratorException.badRequest(CliErrorKey.DAEMON_NOT_RUNNING)
                .message("No daemon answers on " + socket + ": start it with gabier-daemon")
                .addParameter("socket", socket.toString()));
    }

    /** The connection broke, or the daemon hung up without answering: a failure of the daemon, not of the request. */
    static DaemonException connectionFailed(String reason, @Nullable Exception cause) {
        return new DaemonException(GeneratorException.internalServerError(CliErrorKey.DAEMON_CONNECTION_FAILED)
                .message("The connection to the daemon failed: " + reason)
                .cause(cause));
    }
}
