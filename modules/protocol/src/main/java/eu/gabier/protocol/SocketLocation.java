package eu.gabier.protocol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Where the socket of the daemon is: one place, for the daemon and for the cli, so that they cannot disagree.
 *
 * <p>The socket belongs to a project, so that a command run in any folder of a worktree reaches the daemon of that
 * worktree and never the one of another project. The project is the folder {@value #PROJECT} names, else the nearest
 * folder, from the working directory up, that holds a {@code .git} (a folder in a clone, a file in a worktree), else the
 * working directory; it is taken as its real path. The socket is {@code <folder>/<name>-<hash>.sock}: the name of the
 * project folder (plain characters, 40 at most) so that a person can tell the sockets apart, and the first 8
 * hexadecimal digits of a hash of its path so that two worktrees of one name do not share one. The folder of the socket
 * is {@code $XDG_RUNTIME_DIR/gabier} (that is {@code /run/user/<uid>}), else {@code /tmp/gabier-<uid>}, a short place
 * because a path under a worktree is easily longer than the {@code sun_path} of a Unix domain socket address, which
 * holds 108 bytes on Linux, one of them for the terminating zero. {@value #OVERRIDE} replaces the whole path. Whatever
 * the source, a path over {@value #MAXIMUM_BYTES} bytes is refused here, before any bind, because Java refuses to bind
 * it with a message that does not say why.
 */
public final class SocketLocation {

    /** The environment variable that replaces the default path. */
    public static final String OVERRIDE = "GABIER_SOCKET";

    /** The environment variable that names the folder of the project. */
    public static final String PROJECT = "GABIER_PROJECT";

    /** The longest path, in bytes: the 108 of {@code sun_path} less the terminating zero. */
    static final int MAXIMUM_BYTES = 107;

    private static final String RUNTIME_DIRECTORY = "XDG_RUNTIME_DIR";
    private static final int NAME_LENGTH = 40;
    private static final int HASH_LENGTH = 8;

    private SocketLocation() {}

    /**
     * The path of the socket, absolute and normalized.
     *
     * @param environment the environment variables of the process
     * @param uid the numeric id of the user, for the default path when there is no runtime directory
     * @param workingDirectory where the process runs: the project, with the variable and Git, unless a variable says
     * @throws SocketPathTooLongException when the path is over the limit
     */
    public static Path resolve(Map<String, String> environment, String uid, Path workingDirectory) {
        var path =
                candidate(environment, uid, workingDirectory).toAbsolutePath().normalize();
        // The kernel counts bytes, and Java encodes the path before it binds it: so do we, in UTF-8.
        var length = path.toString().getBytes(StandardCharsets.UTF_8).length;
        if (length > MAXIMUM_BYTES) {
            throw SocketPathTooLongException.of(path.toString(), length, MAXIMUM_BYTES, OVERRIDE);
        }

        return path;
    }

    /**
     * The folder of the project a process works on: {@value #PROJECT} (a relative one is relative to the working
     * directory), else the nearest folder from the working directory up that holds a {@code .git}, else the working
     * directory; the real path of that folder, or its absolute path when it does not exist.
     */
    public static Path projectRoot(Map<String, String> environment, Path workingDirectory) {
        var named = Optional.ofNullable(environment.get(PROJECT)).filter(value -> !value.isBlank());
        var folder = named.map(workingDirectory::resolve).orElseGet(() -> nearestGitFolder(workingDirectory));

        try {
            return folder.toRealPath();
        } catch (IOException gone) {
            return folder.toAbsolutePath().normalize();
        }
    }

    private static Path nearestGitFolder(Path workingDirectory) {
        for (var folder = workingDirectory.toAbsolutePath(); folder != null; folder = folder.getParent()) {
            if (Files.exists(folder.resolve(".git"))) {
                return folder;
            }
        }

        return workingDirectory;
    }

    private static Path candidate(Map<String, String> environment, String uid, Path workingDirectory) {
        var override = environment.get(OVERRIDE);
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }

        return folder(environment, uid).resolve(name(projectRoot(environment, workingDirectory)));
    }

    private static Path folder(Map<String, String> environment, String uid) {
        var runtimeDirectory = environment.get(RUNTIME_DIRECTORY);
        // The XDG specification says a relative path is invalid, and to be ignored.
        if (runtimeDirectory != null
                && !runtimeDirectory.isBlank()
                && Path.of(runtimeDirectory).isAbsolute()) {
            return Path.of(runtimeDirectory, "gabier");
        }

        return Path.of("/tmp", "gabier-" + uid);
    }

    /** {@code <name of the folder>-<hash of its path>.sock}: the name in plain characters, and short. */
    private static String name(Path root) {
        var folderName =
                Optional.ofNullable(root.getFileName()).map(Path::toString).orElse("root");
        var plain = folderName.replaceAll("[^A-Za-z0-9._-]", "_");
        var hash = UUID.nameUUIDFromBytes(root.toString().getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "")
                .substring(0, HASH_LENGTH);

        return plain.substring(0, Math.min(plain.length(), NAME_LENGTH)) + "-" + hash + ".sock";
    }
}
