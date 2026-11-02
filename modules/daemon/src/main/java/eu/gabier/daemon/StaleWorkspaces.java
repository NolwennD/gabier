package eu.gabier.daemon;

import eu.gabier.protocol.Directories;
import eu.gabier.protocol.WorkspaceLock;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.UserPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The jdtls workspaces ({@code -data} folders) that nobody uses any more, removed when a daemon starts jdtls: a
 * workspace left in the temporary folder by a daemon that was killed (see {@link JdtlsWorkspace}). The temporary folder
 * is a memory file system on many machines, and a workspace is hundreds of megabytes.
 *
 * <p>Only what is certainly unused and the user's own is removed, and the failure to remove one is told, never thrown:
 * it must not stop the next jdtls from starting. A workspace is used while a daemon holds the lock of its folder
 * ({@link WorkspaceLock}), which the system releases when the daemon dies, killed or not. Links are never followed: a
 * link named like a data folder is not one.
 */
final class StaleWorkspaces {

    /** The start of the name of a temporary data folder. */
    static final String TEMPORARY_PREFIX = "gabier-jdtls-data-";

    private StaleWorkspaces() {}

    /**
     * Removes the data folders of gabier in the temporary folder that no daemon uses. One that has no lock file yet is a
     * folder that was just made (the daemon locks it at once): it stays, unless it is older than the grace, the time a
     * jdtls may take to start.
     *
     * @param folder the temporary folder
     * @param user whose folders may be removed: the others are not ours
     * @param grace how old a folder without lock file must be to be removed
     * @param log receives a line for each folder removed or that could not be
     */
    static void removeKilled(Path folder, UserPrincipal user, Duration grace, Consumer<String> log) {
        // A folder that is not there, or cannot be listed, has nothing to remove.
        for (File entry : Optional.ofNullable(folder.toFile().listFiles()).stream()
                .flatMap(Arrays::stream)
                .toList()) {
            Path workspace = entry.toPath();
            try {
                if (isDataFolder(workspace, user)
                        && notStarting(workspace, grace)
                        && !WorkspaceLock.isHeld(workspace)) {
                    Directories.delete(workspace);
                    log.accept("removed " + workspace + ", which no jdtls uses any more");
                }
            } catch (IOException | UncheckedIOException failure) {
                log.accept("could not clean up " + workspace + ": " + failure);
            }
        }
    }

    private static boolean isDataFolder(Path workspace, UserPrincipal user) throws IOException {
        return workspace.getFileName().toString().startsWith(TEMPORARY_PREFIX)
                && Files.isDirectory(workspace, LinkOption.NOFOLLOW_LINKS)
                && user.equals(Files.getOwner(workspace, LinkOption.NOFOLLOW_LINKS));
    }

    /** True once the folder has its lock file, or has waited longer than the grace for it. */
    private static boolean notStarting(Path workspace, Duration grace) throws IOException {
        return Files.exists(workspace.resolve(WorkspaceLock.FILE))
                || Files.getLastModifiedTime(workspace)
                        .toInstant()
                        .isBefore(Instant.now().minus(grace));
    }
}
