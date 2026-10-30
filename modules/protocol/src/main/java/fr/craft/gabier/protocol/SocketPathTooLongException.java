package fr.craft.gabier.protocol;

import fr.craft.gabier.shared.error.domain.GeneratorException;

/** The path of the socket does not fit in the {@code sun_path} of a Unix domain socket address. */
public final class SocketPathTooLongException extends GeneratorException {

    private SocketPathTooLongException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    static SocketPathTooLongException of(String path, int length, int limit, String override) {
        return new SocketPathTooLongException(GeneratorException.badRequest(ProtocolErrorKey.SOCKET_PATH_TOO_LONG)
                .message("The socket path is " + length + " bytes long, the limit is " + limit
                        + ": use a shorter one, or set " + override + " to a shorter path")
                .addParameter("path", path)
                .addParameter("length", String.valueOf(length))
                .addParameter("limit", String.valueOf(limit))
                .addParameter("override", override));
    }
}
