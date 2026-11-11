package eu.gabier.daemon;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Follows the disk of the project while a client watches: every folder of the project is registered with the system
 * ({@link FolderEvents}), because a registration covers one folder and not what is below it, and a folder that is
 * created later is registered when its creation is told. An event on a file that jdtls watches is a change when the
 * files that jdtls watches no longer look as they did (the fingerprint: their modification time and size, the same
 * look as {@link WatchedFiles} takes), so an event that changes nothing, such as a file touched or jdtls writing its own
 * settings back, does not read the project again. Events that come close together, such as the files of a rename, are
 * one change: the watch waits for a quiet of the debounce.
 *
 * <p>When the system cannot follow (it refuses a registration, which is the limit {@code fs.inotify.max_user_watches}
 * on Linux, or says that it lost events), no change can be missed silently: the watch says {@link
 * DiskChanges.Signal#LOST} once and from then on looks at the fingerprint at a fixed pace. It is not recursive and
 * does not follow links, as the scan of the files of the project does not.
 */
final class FolderWatch implements DiskChanges {

    /** How long the events must stop before the files are compared. */
    static final Duration DEBOUNCE = Duration.ofMillis(100);

    /** How often the files are compared once the system cannot follow (the notice of {@link ProblemFeed} says it). */
    static final Duration POLL_PACE = Duration.ofSeconds(1);

    private final FolderEvents events;
    private final Supplier<?> fingerprint;
    private final Predicate<Path> watched;
    private final Duration debounce;
    private final Duration pace;
    private Object last;
    private boolean polling;
    private boolean toTell;

    /**
     * Registers the folders, then takes the fingerprint the changes are compared to.
     *
     * @param root the folder of the project
     * @param fingerprint what the files that jdtls watches look like on disk now: equal while none of them changed
     * @param watched whether jdtls watches a file (its globs)
     * @param debounce how long the events must stop before the files are compared
     * @param pace how often the files are compared once the system cannot follow
     */
    FolderWatch(
            Path root,
            FolderEvents events,
            Supplier<?> fingerprint,
            Predicate<Path> watched,
            Duration debounce,
            Duration pace) {
        this.events = events;
        this.fingerprint = fingerprint;
        this.watched = watched;
        this.debounce = debounce;
        this.pace = pace;
        if (!registerTree(root)) {
            // Nobody has been told yet: the first wait says so.
            lose();
            toTell = true;
        }
        this.last = fingerprint.get();
    }

    @Override
    public Signal await(Duration timeout) throws InterruptedException {
        if (toTell) {
            toTell = false;

            return Signal.LOST;
        }
        if (polling) {
            Thread.sleep(pace);

            return changed() ? Signal.CHANGED : Signal.QUIET;
        }
        var batch = events.next(timeout);
        boolean relevant = false;
        while (batch.isPresent()) {
            if (batch.get().overflow()) {
                return lose();
            }
            for (var path : batch.get().paths()) {
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    if (!registerTree(path)) {
                        return lose();
                    }
                    relevant = true;
                } else if (watched.test(path)) {
                    relevant = true;
                }
            }
            batch = events.next(debounce);
        }

        return relevant && changed() ? Signal.CHANGED : Signal.QUIET;
    }

    @Override
    public void close() {
        events.close();
    }

    /** The system cannot follow: it is given back and the files are looked at from now on. */
    private Signal lose() {
        polling = true;
        events.close();

        return Signal.LOST;
    }

    private boolean changed() {
        var now = fingerprint.get();
        if (now.equals(last)) {
            return false;
        }
        last = now;

        return true;
    }

    /** Registers a folder and the folders below it, none of them a link; false when the system refuses one. */
    private boolean registerTree(Path folder) {
        try {
            Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                        throws IOException {
                    events.register(directory);

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException failure) {
                    // A folder that is gone, or that cannot be read, holds nothing that can be followed.
                    return FileVisitResult.CONTINUE;
                }
            });

            return true;
        } catch (IOException refused) {
            return false;
        }
    }
}
