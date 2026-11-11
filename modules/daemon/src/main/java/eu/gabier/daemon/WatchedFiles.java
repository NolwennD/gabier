package eu.gabier.daemon;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.Unregistration;

/**
 * The files of the project that jdtls asks its client to watch, and what changed in them since it was last told.
 *
 * <p>jdtls does not look at the disk by itself: it registers the globs it wants watched ({@code
 * client/registerCapability} for {@code workspace/didChangeWatchedFiles}, which it does only for a client that declares
 * {@code workspace.didChangeWatchedFiles.dynamicRegistration}, see {@link JdtlsCapabilities}), and expects the client to
 * send {@code workspace/didChangeWatchedFiles} when a file that matches changes. The daemon is called now and then, not
 * all the time, so it does not run a watcher thread (whose events could still be on their way when a request comes):
 * it remembers the modification time and the size of every file of the project, and compares them with the disk when
 * {@link #changes} is called, right before a request.
 *
 * <p>The state of the disk is read when this object is made, before jdtls is started, so that a file changed while
 * jdtls imports the project is reported with the first request, as one jdtls may already know (an event for a file
 * jdtls has already read is harmless). A relative pattern ({@code baseUri} and {@code pattern}) is ignored: jdtls
 * registers one for the folder of each project, for its deletion only, which a scan of the files never sees.
 */
final class WatchedFiles {

    private static final int CREATE = 1;
    private static final int CHANGE = 2;
    private static final int DELETE = 4;
    private static final int ALL = CREATE | CHANGE | DELETE;

    /** A glob jdtls asks to have watched, and the kinds of change it wants to hear of (a mask). */
    private record Watcher(PathMatcher glob, int kinds) {}

    /** What tells that a file changed. */
    private record Stamp(long modified, long size) {}

    private final Path root;
    private final Map<String, List<Watcher>> registrations = new HashMap<>();
    private Map<Path, Stamp> known;

    private WatchedFiles(Path root, Map<Path, Stamp> known) {
        this.root = root;
        this.known = known;
    }

    /**
     * Reads the state of the project folder, which is the state the changes are compared to.
     */
    static WatchedFiles of(Path root) {
        Path folder = root.toAbsolutePath().normalize();
        return new WatchedFiles(folder, scan(folder));
    }

    /** Takes the watchers of a registration of {@code workspace/didChangeWatchedFiles}; any other method is not ours. */
    synchronized void register(Registration registration) {
        if (!"workspace/didChangeWatchedFiles".equals(registration.getMethod())) {
            return;
        }
        List<Watcher> watchers = new ArrayList<>();
        var options = new Gson().toJsonTree(registration.getRegisterOptions()).getAsJsonObject();
        for (JsonElement element : options.getAsJsonArray("watchers")) {
            var watcher = element.getAsJsonObject();
            var glob = watcher.get("globPattern");
            if (glob.isJsonPrimitive()) {
                int kinds = watcher.has("kind") ? watcher.get("kind").getAsInt() : ALL;
                watchers.add(new Watcher(FileSystems.getDefault().getPathMatcher("glob:" + glob.getAsString()), kinds));
            }
        }
        registrations.put(registration.getId(), watchers);
    }

    /**
     * True once jdtls has registered a watcher. It does so near the end of its initialization, shortly before the
     * first publication of the problems of the whole workspace (checked on the real jdtls).
     */
    synchronized boolean registered() {
        return !registrations.isEmpty();
    }

    synchronized void unregister(Unregistration unregistration) {
        registrations.remove(unregistration.getId());
    }

    /**
     * What changed in the files jdtls watches since the last call (or since this object was made), and is now known:
     * created, changed (modification time or size) and deleted files, each kind only if a watcher that matches the file
     * asks for it. Nothing is read, and nothing is forgotten, while jdtls has registered no watcher: what changes
     * before it does is reported once it has.
     */
    synchronized List<FileEvent> changes() {
        if (registrations.isEmpty()) {
            return List.of();
        }
        Map<Path, Stamp> now = scan(root);
        List<FileEvent> events = new ArrayList<>();
        now.forEach((file, stamp) -> {
            Stamp before = known.get(file);
            if (before == null) {
                add(events, file, FileChangeType.Created, CREATE);
            } else if (!before.equals(stamp)) {
                add(events, file, FileChangeType.Changed, CHANGE);
            }
        });
        known.keySet().stream()
                .filter(file -> !now.containsKey(file))
                .forEach(file -> add(events, file, FileChangeType.Deleted, DELETE));
        known = now;
        return events;
    }

    /**
     * Whether a glob that jdtls registered matches the file, for any kind of change: the files whose change is worth
     * reading the project again for. Nothing is watched while jdtls has registered no watcher.
     */
    synchronized boolean watches(Path file) {
        return registrations.values().stream()
                .flatMap(List::stream)
                .anyMatch(watcher -> watcher.glob().matches(file));
    }

    /**
     * What the watched files look like on disk now (their names, modification times and sizes): equal for two calls
     * while none of them was created, changed or deleted. The disk is scanned, so it costs what {@link #changes} does,
     * and it does not move what {@link #changes} reports.
     */
    synchronized Object fingerprint() {
        Map<Path, Stamp> watched = scan(root);
        watched.keySet().removeIf(file -> !watches(file));

        return watched;
    }

    private void add(List<FileEvent> events, Path file, FileChangeType type, int kind) {
        boolean watched = registrations.values().stream()
                .flatMap(List::stream)
                .anyMatch(watcher ->
                        (watcher.kinds() & kind) != 0 && watcher.glob().matches(file));
        if (watched) {
            events.add(new FileEvent(file.toUri().toString(), type));
        }
    }

    /**
     * Every regular file below the folder (a link is not followed, a folder that cannot be listed is not seen: it holds
     * nothing as far as this goes, so does a folder that does not exist).
     */
    private static Map<Path, Stamp> scan(Path folder) {
        Map<Path, Stamp> files = new TreeMap<>();
        scan(folder, files);
        return files;
    }

    private static void scan(Path folder, Map<Path, Stamp> files) {
        File[] children = folder.toFile().listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            Path path = child.toPath();
            if (Files.isSymbolicLink(path)) {
                continue;
            }
            if (child.isDirectory()) {
                scan(path, files);
            } else if (child.isFile()) {
                files.put(path, new Stamp(child.lastModified(), child.length()));
            }
        }
    }
}
