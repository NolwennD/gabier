package fr.craft.gabier.protocol;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

/**
 * Where the socket of the daemon is: one place, for the daemon and for the cli, so that they cannot disagree.
 *
 * <p>The socket is per user and not per checkout: a path under a worktree is easily longer than the {@code sun_path} of
 * a Unix domain socket address, which holds 108 bytes on Linux, one of them for the terminating zero. The default is
 * {@code $XDG_RUNTIME_DIR/gabier/gabier.sock} (that is {@code /run/user/<uid>}), else {@code
 * /tmp/gabier-<uid>/gabier.sock}, and {@value #OVERRIDE} replaces it. Whatever the source, a path over {@value
 * #MAXIMUM_BYTES} bytes is refused here, before any bind, because Java refuses to bind it with a message that does not
 * say why.
 */
public final class SocketLocation {

    /** The environment variable that replaces the default path. */
    public static final String OVERRIDE = "GABIER_SOCKET";

    /** The longest path, in bytes: the 108 of {@code sun_path} less the terminating zero. */
    static final int MAXIMUM_BYTES = 107;

    private static final String RUNTIME_DIRECTORY = "XDG_RUNTIME_DIR";
    private static final String SOCKET_NAME = "gabier.sock";

    private SocketLocation() {}

    /**
     * The path of the socket, absolute and normalized.
     *
     * @param environment the environment variables of the process
     * @param uid the numeric id of the user, for the default path when there is no runtime directory
     * @throws SocketPathTooLongException when the path is over the limit
     */
    public static Path resolve(Map<String, String> environment, String uid) {
        var path = candidate(environment, uid).toAbsolutePath().normalize();
        // The kernel counts bytes, and Java encodes the path before it binds it: so do we, in UTF-8.
        var length = path.toString().getBytes(StandardCharsets.UTF_8).length;
        if (length > MAXIMUM_BYTES) {
            throw SocketPathTooLongException.of(path.toString(), length, MAXIMUM_BYTES, OVERRIDE);
        }

        return path;
    }

    private static Path candidate(Map<String, String> environment, String uid) {
        var override = environment.get(OVERRIDE);
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        var runtimeDirectory = environment.get(RUNTIME_DIRECTORY);
        // The XDG specification says a relative path is invalid, and to be ignored.
        if (runtimeDirectory != null
                && !runtimeDirectory.isBlank()
                && Path.of(runtimeDirectory).isAbsolute()) {
            return Path.of(runtimeDirectory, "gabier", SOCKET_NAME);
        }

        return Path.of("/tmp", "gabier-" + uid, SOCKET_NAME);
    }
}
