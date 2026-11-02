package eu.gabier.protocol;

import eu.gabier.shared.error.domain.GeneratorException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The numeric id of the user, which Java does not give: it is the owner of {@code /proc/self}, a way that needs no
 * native code and that the cli native image supports. Linux only, like the socket paths of {@link SocketLocation}.
 */
public final class UserId {

    private static final Path SELF = Path.of("/proc/self");

    private UserId() {}

    /** The uid of this process, as text. */
    public static String current() {
        return ownerOf(SELF);
    }

    static String ownerOf(Path path) {
        try {
            return String.valueOf(Files.getAttribute(path, "unix:uid"));
        } catch (IOException | RuntimeException e) {
            throw GeneratorException.internalServerError(ProtocolErrorKey.USER_UNKNOWN)
                    .message("Cannot find out the id of the user")
                    .addParameter("path", path.toString())
                    .cause(e)
                    .build();
        }
    }
}
