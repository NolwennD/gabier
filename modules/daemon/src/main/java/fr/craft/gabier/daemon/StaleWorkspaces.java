package fr.craft.gabier.daemon;

import fr.craft.gabier.protocol.Directories;
import fr.craft.gabier.protocol.WorkspaceLock;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
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
 * workspace left in the temporary folder by a daemon that was killed, and a kept workspace whose project is gone (see
 * {@link JdtlsWorkspace}). The temporary folder is a memory file system on many machines, and a workspace is hundreds
 * of megabytes.
 *
 * <p>Only what is certainly unused and the user's own is removed, and the failure to remove one is told, never thrown:
 * it must not stop the next jdtls from starting. A workspace is used while a daemon holds the lock of its folder
 * ({@link WorkspaceLock}), which the system releases when the daemon dies, killed or not. Links are never followed: a
 * link named like a workspace is not one.
 */
final class StaleWorkspaces {

    /** The start of the name of a temporary data folder. */
    static final String TEMPORARY_PREFIX = "gabier-jdtls-data-";

    /** The file of a kept workspace that holds the folder of its project. */
    static final String ROOT_MARKER = ".gabier-root";

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
        sweep(
                folder,
                user,
                workspace -> {
                    if (!workspace.getFileName().toString().startsWith(TEMPORARY_PREFIX)) {
                        return Optional.empty();
                    }
                    boolean lockedOnce = Files.exists(workspace.resolve(WorkspaceLock.FILE));

                    return lockedOnce || isOlderThan(workspace, grace)
                            ? Optional.of("which no jdtls uses any more")
                            : Optional.empty();
                },
                log);
    }

    /**
     * Removes the kept workspaces whose project no longer exists (a deleted worktree). A folder without the marker of
     * its project is not a kept workspace of gabier and stays.
     *
     * @param folder the folder the workspaces are kept in
     * @param user whose workspaces may be removed: the others are not ours
     * @param log receives a line for each workspace removed or that could not be
     */
    static void removeOfMissingRoots(Path folder, UserPrincipal user, Consumer<String> log) {
        sweep(
                folder,
                user,
                workspace -> {
                    Path marker = workspace.resolve(ROOT_MARKER);
                    if (!Files.isRegularFile(marker)) {
                        return Optional.empty();
                    }
                    String project = Files.readString(marker).strip();

                    return Optional.of("whose project " + project + " no longer exists")
                            .filter(gone -> !Files.isDirectory(Path.of(project)));
                },
                log);
    }

    private static boolean isOlderThan(Path workspace, Duration age) throws IOException {
        return Files.getLastModifiedTime(workspace)
                .toInstant()
                .isBefore(Instant.now().minus(age));
    }

    /** Why a workspace is stale, or nothing when it is not (or not certainly): it may fail to read the disk. */
    @FunctionalInterface
    private interface Staleness {

        Optional<String> of(Path workspace) throws IOException;
    }

    private static void sweep(Path folder, UserPrincipal user, Staleness staleness, Consumer<String> log) {
        // A folder that is not there, or cannot be listed, has nothing to remove.
        for (File entry : Optional.ofNullable(folder.toFile().listFiles()).stream()
                .flatMap(Arrays::stream)
                .toList()) {
            Path workspace = entry.toPath();
            try {
                if (Files.isDirectory(workspace, LinkOption.NOFOLLOW_LINKS)
                        && user.equals(Files.getOwner(workspace, LinkOption.NOFOLLOW_LINKS))) {
                    Optional<String> reason = staleness.of(workspace);
                    if (reason.isPresent() && !WorkspaceLock.isHeld(workspace)) {
                        Directories.delete(workspace);
                        log.accept("removed " + workspace + ", " + reason.get());
                    }
                }
            } catch (IOException | UncheckedIOException | InvalidPathException failure) {
                log.accept("could not clean up " + workspace + ": " + failure);
            }
        }
    }
}
