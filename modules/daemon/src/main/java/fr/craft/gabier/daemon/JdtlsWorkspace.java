package fr.craft.gabier.daemon;

import fr.craft.gabier.protocol.Directories;
import fr.craft.gabier.protocol.KeptWorkspaces;
import fr.craft.gabier.protocol.WorkspaceInUseException;
import fr.craft.gabier.protocol.WorkspaceLock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Where jdtls keeps its workspace (its {@code -data} folder) and what becomes of it when the session ends.
 *
 * <p>By default the workspace is temporary: a folder of its own in the temporary folder, removed at the close, so that
 * nothing is left behind. When a folder is given ({@value KeptWorkspaces#VARIABLE}), the workspace is kept: {@code
 * <folder>/<hash of the root of the project>} ({@link KeptWorkspaces}), the same one for each daemon of the project and
 * left at the close, so that jdtls reuses the projects it imported valid in it ({@code
 * ProjectsManager.initializeProjects} of Eclipse JDT LS) instead of importing them again. It tells its project in the
 * file {@value StaleWorkspaces#ROOT_MARKER}, so that the one of a project that is gone is removed.
 *
 * <p>The daemon holds a lock on the folder as long as it uses it ({@link WorkspaceLock}): a kept workspace has one
 * daemon at a time, and the workspaces that nobody uses any more are told from the others and removed when one is
 * opened ({@link StaleWorkspaces}).
 *
 * @param temporaryFolder where a temporary workspace is created, and where the ones of daemons that were killed are
 *     looked for
 * @param keptIn the folder the workspaces are kept in; none when they are temporary
 */
record JdtlsWorkspace(Path temporaryFolder, Optional<Path> keptIn) {

    /**
     * A workspace in use.
     *
     * @param data the folder jdtls is started on
     * @param lock held until the workspace is released
     */
    record Opened(Path data, WorkspaceLock lock) {}

    /** A temporary workspace in the temporary folder of the system. */
    static JdtlsWorkspace temporary() {
        return temporary(Path.of(System.getProperty("java.io.tmpdir")));
    }

    /** A temporary workspace in the folder. */
    static JdtlsWorkspace temporary(Path folder) {
        return new JdtlsWorkspace(folder, Optional.empty());
    }

    /** The same workspace, kept in the folder instead of being temporary. */
    JdtlsWorkspace keptIn(Path folder) {
        return new JdtlsWorkspace(temporaryFolder, Optional.of(folder));
    }

    boolean isKept() {
        return keptIn.isPresent();
    }

    /**
     * Makes the folder jdtls is started on and locks it, then removes the workspaces that nobody uses any more.
     *
     * @param root the folder of the project, which a kept workspace is for
     * @param grace the time jdtls may take to start: a folder younger than that may be one that is starting
     * @param log receives a line for each folder removed or that could not be
     * @throws WorkspaceInUseException when the workspace is kept and a daemon holds its lock
     */
    Opened open(Path root, Duration grace, Consumer<String> log) throws IOException {
        Path data = keptIn.isPresent()
                ? Files.createDirectories(KeptWorkspaces.of(keptIn.get(), root))
                : Files.createTempDirectory(temporaryFolder, StaleWorkspaces.TEMPORARY_PREFIX);
        WorkspaceLock lock = WorkspaceLock.acquire(data).orElseThrow(() -> WorkspaceInUseException.of(root, data));
        // The folder was just made, or is the user's own: who owns it is who may be cleaned up after.
        var user = Files.getOwner(data);
        StaleWorkspaces.removeKilled(temporaryFolder, user, grace, log);
        keptIn.ifPresent(folder -> StaleWorkspaces.removeOfMissingRoots(folder, user, log));
        if (keptIn.isPresent()) {
            Files.writeString(data.resolve(StaleWorkspaces.ROOT_MARKER), root.toString());
        }

        return new Opened(data, lock);
    }

    /** Ends the use of the workspace: a temporary one is removed, a kept one stays as jdtls left it; then it is unlocked. */
    void release(Opened opened) throws IOException {
        try {
            if (keptIn.isEmpty()) {
                Directories.delete(opened.data());
            }
        } finally {
            opened.lock().close();
        }
    }
}
