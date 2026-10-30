package fr.craft.gabier.daemon;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * What the daemon checks on the disk before it takes its socket: the folder is the user's own and private to them, a
 * file left in its place is a socket of theirs that nobody answers on, and the socket it creates is private. It never
 * follows a link and never removes anything but such a socket: {@code GABIER_SOCKET} can point anywhere.
 *
 * <p>The numbers of the {@code unix:mode} attribute are those of {@code stat(2)}: the file type in the bits of {@code
 * S_IFMT}.
 */
final class SocketGuard {

    private static final Set<PosixFilePermission> PRIVATE_FOLDER = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> PRIVATE_FILE = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> OTHER_USERS = PosixFilePermissions.fromString("---rwxrwx");

    private static final int FILE_TYPE_MASK = 0170000;
    private static final int SOCKET = 0140000;
    private static final int FOLDER = 0040000;

    private final String uid;

    /**
     * Guards the socket of the user.
     *
     * @param uid the numeric id of the user the daemon runs as, as text
     */
    SocketGuard(String uid) {
        this.uid = uid;
    }

    /**
     * Makes sure the folder exists, is the user's own and is private to them: an existing one is judged and never
     * changed, since it may be a folder the user shares on purpose, and a missing one is created with its parents.
     */
    void prepareFolder(Path folder) throws IOException {
        try {
            Files.createDirectories(folder.getParent());
            Files.createDirectory(folder, PosixFilePermissions.asFileAttribute(PRIVATE_FOLDER));
        } catch (FileAlreadyExistsException exists) {
            // Judged below.
        }
        if (!is(folder, FOLDER)) {
            throw DaemonSocketException.notPrivate(folder, "it is not a folder");
        }
        checkOwner(folder);
        var permissions = Files.getPosixFilePermissions(folder, LinkOption.NOFOLLOW_LINKS);
        if (permissions.stream().anyMatch(OTHER_USERS::contains)) {
            throw DaemonSocketException.notPrivate(
                    folder,
                    "other users can use it (mode " + PosixFilePermissions.toString(permissions)
                            + "): make it private to you, or choose another place with GABIER_SOCKET");
        }
    }

    /**
     * Removes the file that is in the place of the socket, when it is a socket of the user's own that nobody answers
     * on, which is what a daemon that was killed leaves.
     *
     * @throws DaemonSocketException when another daemon answers on it, or when the file is not a socket of the user
     */
    void clearStale(Path socket) throws IOException {
        if (!exists(socket)) {
            return;
        }
        if (!is(socket, SOCKET)) {
            throw DaemonSocketException.notPrivate(socket, "it is not a socket");
        }
        checkOwner(socket);
        if (answers(socket)) {
            throw DaemonSocketException.alreadyRunning(socket);
        }
        Files.delete(socket);
    }

    /** Makes the socket file readable and writable by the user only. */
    void restrict(Path socket) throws IOException {
        Files.setPosixFilePermissions(socket, PRIVATE_FILE);
    }

    private static boolean exists(Path path) {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean is(Path path, int type) throws IOException {
        return (mode(path) & FILE_TYPE_MASK) == type;
    }

    private void checkOwner(Path path) throws IOException {
        var owner = String.valueOf(Files.getAttribute(path, "unix:uid", LinkOption.NOFOLLOW_LINKS));
        if (!owner.equals(uid)) {
            throw DaemonSocketException.notPrivate(path, "it is owned by someone else (uid " + owner + ")");
        }
    }

    private static int mode(Path path) throws IOException {
        return (int) Files.getAttribute(path, "unix:mode", LinkOption.NOFOLLOW_LINKS);
    }

    /** Whether something accepts connections on the socket: a refused or failed connection means nobody does. */
    private static boolean answers(Path socket) {
        try (var channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(socket));

            return true;
        } catch (IOException nobodyAnswers) {
            return false;
        }
    }
}
