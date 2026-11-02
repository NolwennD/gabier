package fr.craft.gabier.protocol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The jdtls workspaces ({@code -data} folders) that are kept between daemons, one per project, in the folder that
 * {@value #VARIABLE} names: where they are, for the daemon that uses one and for the command that removes one with no
 * daemon running, so that both compute the same folder.
 */
public final class KeptWorkspaces {

    /** The environment variable that names the folder the workspaces are kept in; no variable, none is kept. */
    public static final String VARIABLE = "GABIER_JDTLS_WORKSPACES";

    private KeptWorkspaces() {}

    /** The folder the workspaces are kept in, absolute and normalized; none when the variable is not set. */
    public static Optional<Path> folder(Map<String, String> environment) {
        return Optional.ofNullable(environment.get(VARIABLE))
                .filter(value -> !value.isBlank())
                .map(value -> Path.of(value).toAbsolutePath().normalize());
    }

    /** The workspace of the project: the folder named by a hash of the root, so that it stays short. */
    public static Path of(Path folder, Path root) {
        return folder.resolve(UUID.nameUUIDFromBytes(root.toString().getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", ""));
    }

    /**
     * The root of a project as the daemon names it: the real path of its folder; the absolute normalized path when the
     * folder is gone (a deleted worktree).
     */
    public static Path rootOf(Path folder) {
        try {
            return folder.toRealPath();
        } catch (IOException gone) {
            return folder.toAbsolutePath().normalize();
        }
    }

    /**
     * Removes the kept workspace of the project, with everything jdtls left in it. It needs no daemon, and nothing is
     * an error when there is nothing to remove.
     *
     * @param folder the folder the workspaces are kept in
     * @param root the root of the project
     * @return whether a workspace was removed
     * @throws WorkspaceInUseException when a daemon holds the lock of the workspace: it is left as it is
     */
    public static boolean remove(Path folder, Path root) throws IOException {
        Path workspace = of(folder, root);
        if (!Files.isDirectory(workspace, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        // Holding the lock while deleting keeps a daemon that starts meanwhile from using what is being removed.
        try (var lock =
                WorkspaceLock.acquire(workspace).orElseThrow(() -> WorkspaceInUseException.of(root, workspace))) {
            Directories.delete(workspace);
        }

        return true;
    }
}
