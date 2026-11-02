package eu.gabier.protocol;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The lock that tells a jdtls workspace that is in use from one that nobody uses: the daemon takes a lock on the file
 * {@value #FILE} of the {@code -data} folder as long as it runs jdtls on it, and the system gives it up when the daemon
 * dies, killed or not.
 *
 * <p>The lock is the daemon's own because jdtls holds none: Eclipse locks a workspace ({@code .metadata/.lock}) in its
 * IDE application only, and the application of jdtls takes no lock on its instance area (checked on 1.63.0: no such
 * file in the data folder of a running jdtls, and none in its sources).
 *
 * <p>A lock of a file belongs to the process, not to the channel it was taken on: on Linux, closing any channel of the
 * file in the process that holds the lock gives it up. So a process never opens the lock file again while it holds the
 * lock (to check it, or to try to take it twice): it answers from the registry of the locks it holds, and only a file
 * it does not hold is opened, which cannot cost it a lock.
 */
public final class WorkspaceLock implements AutoCloseable {

    /** The file of the workspace that is locked. */
    public static final String FILE = ".gabier-lock";

    /** The lock files this process holds, by real path: what is asked of them is answered without opening them. */
    private static final Set<Path> HELD = ConcurrentHashMap.newKeySet();

    private final Path file;
    private final FileChannel channel;

    private WorkspaceLock(Path file, FileChannel channel) {
        this.file = file;
        this.channel = channel;
    }

    /**
     * Takes the lock of the workspace, which the daemon keeps until it closes the lock.
     *
     * @return the lock, or nothing when a process holds it already (this one included)
     */
    public static Optional<WorkspaceLock> acquire(Path workspace) throws IOException {
        Path file = workspace.toRealPath().resolve(FILE);
        if (!HELD.add(file)) {
            return Optional.empty();
        }
        try {
            FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            // Nothing when another process holds it.
            Optional<FileLock> lock = Optional.ofNullable(channel.tryLock());
            if (lock.isEmpty()) {
                channel.close();
                HELD.remove(file);
            }

            return lock.map(held -> new WorkspaceLock(file, channel));
        } catch (IOException | RuntimeException failure) {
            HELD.remove(file);
            throw failure;
        }
    }

    /**
     * Whether a process holds the lock of the workspace. A workspace with no lock file is held by nobody. A lock that
     * cannot be tried is held: nothing shows that the workspace is not in use. It never costs this process a lock it
     * holds.
     */
    public static boolean isHeld(Path workspace) {
        if (!Files.isRegularFile(workspace.resolve(FILE), LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try {
            if (HELD.contains(workspace.toRealPath().resolve(FILE))) {
                return true;
            }
            Optional<WorkspaceLock> taken = acquire(workspace);
            if (taken.isPresent()) {
                taken.get().close();
            }

            return taken.isEmpty();
        } catch (IOException unreadable) {
            return true;
        }
    }

    /** Gives the lock up. */
    @Override
    public void close() throws IOException {
        try {
            channel.close();
        } finally {
            HELD.remove(file);
        }
    }
}
