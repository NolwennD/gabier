package eu.gabier.cli.infrastructure.secondary;

import eu.gabier.shared.error.domain.GeneratorException;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/** The cli cannot talk to the daemon. */
final class DaemonException extends GeneratorException {

    private DaemonException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    /** Nothing answers on the socket: no daemon, or one that was killed and left its socket file. */
    static DaemonException notRunning(Path socket) {
        return new DaemonException(GeneratorException.badRequest(DaemonErrorKey.DAEMON_NOT_RUNNING)
                .message("No daemon answers on " + socket + ": start it with gabier-daemon")
                .addParameter("socket", socket.toString()));
    }

    /**
     * The daemon the cli started did not come up: not found, not launched, exited, or silent for too long.
     *
     * @param log where its output went, when it was started
     */
    static DaemonException startFailed(String reason, Path socket, @Nullable Path log, @Nullable Exception cause) {
        var builder = GeneratorException.internalServerError(DaemonErrorKey.DAEMON_START_FAILED)
                .message("The daemon did not start: " + reason)
                .addParameter("socket", socket.toString())
                .cause(cause);
        if (log != null) {
            builder.addParameter("log", log.toString());
        }

        return new DaemonException(builder);
    }

    /** The daemon ended before it listened: its exit code is a parameter, its output is in the log. */
    static DaemonException exited(int exitCode, Path socket, Path log) {
        var reason = "gabier-daemon exited with code " + exitCode + " before it listened on " + socket
                + "; its output is in " + log;

        return new DaemonException(GeneratorException.internalServerError(DaemonErrorKey.DAEMON_START_FAILED)
                .message("The daemon did not start: " + reason)
                .addParameter("socket", socket.toString())
                .addParameter("log", log.toString())
                .addParameter("exitCode", String.valueOf(exitCode)));
    }

    /** The connection broke, or the daemon hung up without answering: a failure of the daemon, not of the request. */
    static DaemonException connectionFailed(String reason, @Nullable Exception cause) {
        return new DaemonException(GeneratorException.internalServerError(DaemonErrorKey.DAEMON_CONNECTION_FAILED)
                .message("The connection to the daemon failed: " + reason)
                .cause(cause));
    }
}
