package eu.gabier.daemon;

import eu.gabier.shared.generation.ExcludeFromGeneratedCodeCoverage;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * The events of the folders registered with the system, from the {@link WatchService} of the JDK: inotify on Linux, where
 * an event comes within a millisecond. Where the JDK has no such service it polls, slowly (about every ten seconds on
 * macOS); the delays of the daemon are measured on Linux. The service is made at the first registration. One thread
 * registers and waits, the one of {@link FolderWatch}: nothing here is shared.
 */
final class NioFolderEvents implements FolderEvents {

    private final Map<WatchKey, Path> folders = new ConcurrentHashMap<>();
    private @Nullable WatchService service;

    @Override
    public void register(Path folder) throws IOException {
        if (service == null) {
            service = FileSystems.getDefault().newWatchService();
        }
        var key = folder.register(
                service,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        folders.put(key, folder);
    }

    @Override
    public Optional<Batch> next(Duration timeout) throws InterruptedException {
        var watching = service;
        if (watching == null) {
            return Optional.empty();
        }
        var key = watching.poll(timeout.toNanos(), TimeUnit.NANOSECONDS);
        if (key == null) {
            return Optional.empty();
        }
        Set<Path> paths = new HashSet<>();
        boolean overflow = false;
        // Everything that is there already is one batch: the files of a rename are not told one by one.
        while (key != null) {
            // A key is registered and polled by the same thread, so its folder is known by the time its events come.
            overflow |= take(key, Objects.requireNonNull(folders.get(key)), paths);
            key = watching.poll();
        }

        return Optional.of(new Batch(paths, overflow));
    }

    /** Takes the events of a folder into the paths; says whether the system lost some. */
    static boolean take(WatchKey key, Path folder, Set<Path> paths) {
        boolean overflow = false;
        for (var event : key.pollEvents()) {
            if (StandardWatchEventKinds.OVERFLOW.equals(event.kind())) {
                overflow = true;
            } else {
                paths.add(folder.resolve((Path) event.context()));
            }
        }
        key.reset();

        return overflow;
    }

    @Override
    @ExcludeFromGeneratedCodeCoverage(
            reason =
                    "The close of a WatchService of the JDK cannot be made to fail from a test, and nothing can be done"
                            + " about it if it does")
    public void close() {
        var closing = service;
        service = null;
        if (closing != null) {
            try {
                closing.close();
            } catch (IOException e) {
                // Nothing is left to give back that this could fail on: the JDK closes its registrations itself.
            }
        }
    }
}
