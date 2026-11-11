package eu.gabier.daemon;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * What the system tells of the folders that were registered with it (inotify on Linux, through {@code
 * java.nio.file.WatchService}): the one seam between {@link FolderWatch} and the system. A registration covers one
 * folder, not what is below it; the events of a folder come as the path of what changed in it.
 */
interface FolderEvents extends AutoCloseable {

    /**
     * What the system said since the last call.
     *
     * @param paths the files and folders that were created, changed or deleted
     * @param overflow whether the system lost events: nothing can be said of what changed
     */
    record Batch(Set<Path> paths, boolean overflow) {}

    /**
     * Asks the system to tell what changes in a folder.
     *
     * @throws IOException when it cannot (the limit of watches of the user is reached, the folder is gone)
     */
    void register(Path folder) throws IOException;

    /** Waits for events for the time given; empty when there are none. */
    Optional<Batch> next(Duration timeout) throws InterruptedException;

    /** Gives back what the system holds for the registrations. */
    @Override
    void close();
}
