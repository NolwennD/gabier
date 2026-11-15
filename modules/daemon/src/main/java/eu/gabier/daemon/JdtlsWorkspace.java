package eu.gabier.daemon;

import eu.gabier.daemon.JdtlsRuntimes.Jdk;
import eu.gabier.daemon.WatchedFiles.Stamp;
import eu.gabier.protocol.Directories;
import eu.gabier.protocol.WorkspaceLock;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Where jdtls keeps its workspace (its {@code -data} folder) and what becomes of it when the session ends. Two kinds:
 *
 * <ul>
 *   <li>a temporary workspace is a folder of its own in the temporary folder, removed at the close, so that nothing is
 *       left behind;
 *   <li>a kept workspace is the folder of the project ({@link KeptWorkspace}), which a jdtls that ended cleanly leaves
 *       as it is, with a state file ({@value #STATE}): the next start on it imports and builds nothing again. A folder
 *       with no state file (the jdtls was killed, the start failed), with a state of another fingerprint or with a state
 *       that cannot be read is emptied before it is used, so a start never depends on what a bad end left.
 * </ul>
 *
 * <p>The daemon holds a lock on the folder as long as it uses it ({@link WorkspaceLock}), which is how the temporary
 * workspaces that nobody uses any more are told from the others, and removed when one is opened
 * ({@link StaleWorkspaces}). A kept workspace that is locked is in use: the start takes a temporary one.
 *
 * @param temporaryFolder where a temporary workspace is created, and where the ones of daemons that were killed are
 *     looked for
 */
record JdtlsWorkspace(Path temporaryFolder) {

    /** The file of a kept workspace that says what it was made with and what jdtls knows of the project. */
    static final String STATE = ".gabier-state";

    /** The file of a kept workspace that names the project it is of. */
    static final String ROOT = ".gabier-root";

    private static final String STATE_HEADER = "gabier-workspace 1";
    private static final Set<String> KEPT_FILES = Set.of(WorkspaceLock.FILE, ROOT);

    /**
     * What a kept workspace holds of the previous run.
     *
     * @param disk the files jdtls had been told of ({@link WatchedFiles#remembered})
     * @param runtimes the JDKs the projects were bound to ({@link JdtlsRuntimes}), which the next run binds them to again
     */
    record Remembered(Map<Path, Stamp> disk, List<Jdk> runtimes) {}

    /**
     * A workspace in use.
     *
     * @param data the folder jdtls is started on
     * @param lock held until the workspace is released
     * @param kept what the workspace is kept for, nothing for a temporary one
     * @param remembered what the previous run left in a kept workspace that is reused, nothing for a new one
     */
    record Opened(Path data, WorkspaceLock lock, Optional<KeptWorkspace> kept, Optional<Remembered> remembered) {

        Opened(Path data, WorkspaceLock lock) {
            this(data, lock, Optional.empty(), Optional.empty());
        }
    }

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

    /**
     * Locks the kept workspace of the project, and empties it unless a jdtls that ended cleanly left it as the same
     * fingerprint made it. A workspace that cannot be used (another daemon holds it, the folder cannot be made or
     * emptied) is told, and a temporary one is opened instead: the start does not fail for it.
     *
     * @param kept the workspace to open
     * @param grace as for {@link #open}
     * @param log receives a line for each folder removed or that could not be, and for the reason a kept workspace is
     *     not used as it is
     */
    Opened open(KeptWorkspace kept, Duration grace, Consumer<String> log) throws IOException {
        Optional<WorkspaceLock> lock = Optional.empty();
        try {
            Path data = Files.createDirectories(kept.folder());
            lock = WorkspaceLock.acquire(data);
            if (lock.isEmpty()) {
                log.accept("the workspace " + data + " is used by another jdtls: a temporary one is used");
                return open(grace, log);
            }
            if (isUsedByAJdtls(data)) {
                lock.get().close();
                log.accept("the workspace " + data + " is used by a jdtls that no daemon holds (its daemon was killed):"
                        + " a temporary one is used");
                return open(grace, log);
            }
            var owner = Files.getOwner(data);
            StaleWorkspaces.removeKilled(temporaryFolder, owner, grace, log);
            StaleWorkspaces.removeOfGoneProjects(data.resolve("..").normalize(), owner, log);
            Optional<Remembered> remembered = read(data, kept.fingerprint(), log);
            if (remembered.isEmpty()) {
                empty(data);
            }
            Files.deleteIfExists(data.resolve(STATE));
            Files.writeString(data.resolve(ROOT), kept.projectRoot().toString());

            return new Opened(data, lock.get(), Optional.of(kept), remembered);
        } catch (IOException | UncheckedIOException failure) {
            log.accept("the kept workspace " + kept.folder() + " cannot be used, a temporary one is used: " + failure);
            if (lock.isPresent()) {
                lock.get().close();
            }
            return open(grace, log);
        }
    }

    /**
     * Writes down that the jdtls on a kept workspace ended cleanly and what it knew, so that the next start reuses it.
     * Without it, {@link #release} empties the workspace.
     */
    void keep(Opened opened, Remembered remembered) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(STATE_HEADER);
        lines.add(opened.kept().orElseThrow().fingerprint());
        lines.add(String.valueOf(remembered.runtimes().size()));
        remembered.runtimes().stream().map(Jdk::line).forEach(lines::add);
        lines.addAll(WatchedFiles.format(remembered.disk()));
        Path written = Files.write(opened.data().resolve(STATE + ".tmp"), lines);
        Files.move(written, opened.data().resolve(STATE), StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Ends the use of the workspace: a temporary folder is removed, a kept one is emptied unless it was {@link #keep
     * kept}; then it is unlocked.
     */
    void release(Opened opened) throws IOException {
        try {
            if (opened.kept().isEmpty()) {
                Directories.delete(opened.data());
            } else if (!Files.exists(opened.data().resolve(STATE))) {
                empty(opened.data());
            }
        } finally {
            opened.lock().close();
        }
    }

    /**
     * True while a process runs jdtls on the folder ({@code -data <folder>} on its command line): the jdtls of a daemon
     * that was killed goes on for a while, and writes in its workspace until it is gone. The lock is no use for it: only
     * the daemon held it.
     */
    private static boolean isUsedByAJdtls(Path data) {
        String folder = data.toString();

        return ProcessHandle.allProcesses()
                .map(process -> process.info().arguments())
                .flatMap(Optional::stream)
                .anyMatch(arguments -> Arrays.asList(arguments).contains(folder));
    }

    /** What the previous run left, when it ended cleanly and with the fingerprint asked for. */
    private static Optional<Remembered> read(Path data, String fingerprint, Consumer<String> log) {
        Path state = data.resolve(STATE);
        if (!Files.isRegularFile(state)) {
            return Optional.empty();
        }
        try {
            List<String> lines = Files.readAllLines(state);
            if (lines.size() < 3 || !STATE_HEADER.equals(lines.get(0))) {
                log.accept("the kept workspace " + data + " has a state that is not readable: it is emptied");
                return Optional.empty();
            }
            if (!fingerprint.equals(lines.get(1))) {
                log.accept("the kept workspace " + data + " was made with " + lines.get(1) + ", not with " + fingerprint
                        + ": it is emptied");
                return Optional.empty();
            }
            int count = Integer.parseInt(lines.get(2));
            Optional<Map<Path, Stamp>> disk = WatchedFiles.parse(lines.subList(3 + count, lines.size()));
            List<Optional<Jdk>> runtimes =
                    lines.subList(3, 3 + count).stream().map(Jdk::parse).toList();
            if (disk.isEmpty() || runtimes.stream().anyMatch(Optional::isEmpty)) {
                log.accept("the kept workspace " + data + " has a state that is not readable: it is emptied");
                return Optional.empty();
            }
            List<Jdk> bound = runtimes.stream().map(Optional::get).toList();
            Optional<Jdk> replaced =
                    bound.stream().filter(runtime -> !runtime.isStillThere()).findFirst();
            if (replaced.isPresent()) {
                log.accept("the kept workspace " + data + " was bound to "
                        + replaced.get().home() + ", which is not the JDK it was ("
                        + replaced.get().version() + ") any more: it is emptied");
                return Optional.empty();
            }

            return Optional.of(new Remembered(disk.get(), bound));
        } catch (IOException | UncheckedIOException | IndexOutOfBoundsException | IllegalArgumentException unreadable) {
            log.accept("the kept workspace " + data + " has a state that is not readable: it is emptied");
            return Optional.empty();
        }
    }

    /** Removes what the workspace holds, but its lock file (which is held) and the name of its project. */
    private static void empty(Path data) throws IOException {
        List<Path> entries;
        try (Stream<Path> children = Files.list(data)) {
            entries = children.filter(
                            child -> !KEPT_FILES.contains(child.getFileName().toString()))
                    .toList();
        }
        for (Path entry : entries) {
            Directories.delete(entry);
        }
    }
}
