package fr.craft.gabier.daemon;

import fr.craft.gabier.shared.error.domain.GeneratorException;
import java.nio.file.Path;

/** The daemon cannot take the socket: someone else's or another daemon's, or the system refuses. */
final class DaemonSocketException extends GeneratorException {

    private DaemonSocketException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    /** The folder or the file is not the user's own, or not private to the user: it is left as it is. */
    static DaemonSocketException notPrivate(Path path, String reason) {
        return new DaemonSocketException(GeneratorException.badRequest(DaemonErrorKey.SOCKET_NOT_PRIVATE)
                .message("Refusing to use " + path + ": " + reason)
                .addParameter("path", path.toString()));
    }

    static DaemonSocketException alreadyRunning(Path socket) {
        return new DaemonSocketException(GeneratorException.badRequest(DaemonErrorKey.ALREADY_RUNNING)
                .message("Another daemon answers on " + socket)
                .addParameter("socket", socket.toString()));
    }

    static DaemonSocketException unavailable(Path path, Exception cause) {
        return new DaemonSocketException(GeneratorException.internalServerError(DaemonErrorKey.SOCKET_UNAVAILABLE)
                .message("Cannot set up the socket " + path)
                .addParameter("path", path.toString())
                .cause(cause));
    }
}
