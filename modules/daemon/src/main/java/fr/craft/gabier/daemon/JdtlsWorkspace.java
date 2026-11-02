package fr.craft.gabier.daemon;

import fr.craft.gabier.protocol.Directories;
import fr.craft.gabier.protocol.WorkspaceLock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Where jdtls keeps its workspace (its {@code -data} folder) and what becomes of it when the session ends: a
 * temporary workspace is a folder of its own in the temporary folder, removed at the close, so that nothing is left
 * behind. The daemon holds a lock on the folder as long as it uses it ({@link WorkspaceLock}), which is how the
 * workspaces that nobody uses any more are told from the others, and removed when one is opened
 * ({@link StaleWorkspaces}).
 *
 * @param temporaryFolder where a temporary workspace is created, and where the ones of daemons that were killed are
 *     looked for
 */
record JdtlsWorkspace(Path temporaryFolder) {

    /**
     * A workspace in use.
     *
     * @param data the folder jdtls is started on
     * @param lock held until the workspace is released
     */
    record Opened(Path data, WorkspaceLock lock) {}

    /** A temporary workspace in the temporary folder of the system. */
    static JdtlsWorkspace temporary() {
        return new JdtlsWorkspace(Path.of(System.getProperty("java.io.tmpdir")));
    }

    /**
     * Makes the folder jdtls is started on and locks it, then removes the workspaces that nobody uses any more.
     *
     * @param grace the time jdtls may take to start: a folder younger than that may be one that is starting
     * @param log receives a line for each folder removed or that could not be
     */
    Opened open(Duration grace, Consumer<String> log) throws IOException {
        Path data = Files.createTempDirectory(temporaryFolder, StaleWorkspaces.TEMPORARY_PREFIX);
        // The folder has a name of its own, made just now: no other daemon can hold its lock.
        WorkspaceLock lock = WorkspaceLock.acquire(data).orElseThrow();
        // The folder was just made: who owns it is who may be cleaned up after.
        StaleWorkspaces.removeKilled(temporaryFolder, Files.getOwner(data), grace, log);

        return new Opened(data, lock);
    }

    /** Ends the use of the workspace: the folder is removed, then unlocked. */
    void release(Opened opened) throws IOException {
        try {
            Directories.delete(opened.data());
        } finally {
            opened.lock().close();
        }
    }
}
