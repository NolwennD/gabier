package eu.gabier.cli.infrastructure.secondary;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Connects to the daemon, and starts it when nothing answers on its socket: a process launch, the same {@code
 * gabier-daemon} a user would start by hand.
 *
 * <p>The executable is {@code gabier-daemon} next to the binary of the cli (a distribution that ships the two side by
 * side), else on the {@code PATH} (the two installed by name, {@code mise run install}). The daemon runs in the working
 * directory of the cli with the environment of the cli, so that it serves the project the daemon would serve if it was
 * started there: the folder given by {@code GABIER_PROJECT}, else the current folder. It is started detached from the
 * cli, with no input and its output in a log kept next to the socket, which a failed start names. The cli waits until
 * the socket accepts a connection, for {@link #START_DELAY} at most; a daemon that exits, or does not listen in time,
 * is reported and ended, never left running. A daemon that answers is used, never started twice.
 */
final class DaemonLauncher {

    /** How long a started daemon has to listen on its socket. It binds first, jdtls starts later, at a request. */
    static final Duration START_DELAY = Duration.ofSeconds(20);

    /**
     * What the starts of the daemon of a socket take turns on, one lock for each socket: the log file of a start is
     * written by that start alone, and the daemons of two projects do not wait for each other.
     */
    private static final Map<Path, Object> LAUNCHES = new ConcurrentHashMap<>();

    private static final String DAEMON_NAME = "gabier-daemon";
    private static final Pattern PATH_SEPARATOR = Pattern.compile(Pattern.quote(File.pathSeparator));
    private static final long POLL_MILLIS = 25;

    private final Map<String, String> environment;
    private final Path workingDirectory;
    private final @Nullable Path ownBinary;
    private final Duration delay;

    /**
     * A launcher for a cli with this environment, in this folder.
     *
     * @param environment the environment of the cli, given to the daemon as it is, and where {@code PATH} is read
     * @param workingDirectory the folder the daemon is started in
     * @param ownBinary the executable of the cli, or null when it is not known
     * @param delay how long a started daemon has to listen
     */
    DaemonLauncher(Map<String, String> environment, Path workingDirectory, @Nullable Path ownBinary, Duration delay) {
        this.environment = environment;
        this.workingDirectory = workingDirectory;
        this.ownBinary = ownBinary;
        this.delay = delay;
    }

    /** The launcher of this process: its working directory, its binary, the usual delay. */
    static DaemonLauncher of(Map<String, String> environment) {
        var own = ProcessHandle.current().info().command().map(Path::of).orElse(null);

        return new DaemonLauncher(environment, Path.of("").toAbsolutePath(), own, START_DELAY);
    }

    /**
     * Connects to the daemon on the socket, starting it first when nothing answers.
     *
     * @throws DaemonException when the daemon cannot be started or does not come up
     */
    DaemonClient connect(Path socket) {
        var running = DaemonClient.tryConnect(socket);
        if (running.isPresent()) {
            return running.get();
        }
        // One start at a time for a socket in this process: calls that run together (the server of MCP answers several
        // at once) would each launch a daemon and write its log, and the one that comes second finds the socket of the
        // first. The starts for another socket go on meanwhile.
        synchronized (LAUNCHES.computeIfAbsent(socket.toAbsolutePath().normalize(), _ -> new Object())) {
            return DaemonClient.tryConnect(socket).orElseGet(() -> start(socket));
        }
    }

    /** Where the output of the daemon that the cli starts for this socket goes. */
    static Path logOf(Path socket) {
        return socket.resolveSibling(socket.getFileName() + ".log");
    }

    private DaemonClient start(Path socket) {
        var executable = locate(socket);
        var log = logOf(socket);
        var process = launch(executable, socket, log);
        try {
            return awaitSocket(process, socket, log);
        } catch (DaemonException failure) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            throw failure;
        }
    }

    private Path locate(Path socket) {
        var searched = new ArrayList<Path>();
        if (ownBinary != null && ownBinary.getParent() != null) {
            searched.add(ownBinary.getParent());
        }
        PATH_SEPARATOR
                .splitAsStream(environment.getOrDefault("PATH", ""))
                .filter(folder -> !folder.isEmpty())
                .forEach(folder -> searched.add(Path.of(folder)));
        for (var folder : searched) {
            var candidate = folder.resolve(DAEMON_NAME);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate;
            }
        }

        throw DaemonException.startFailed(
                DAEMON_NAME + " was not found next to the binary of gabier or in the PATH (looked in "
                        + String.join(
                                File.pathSeparator,
                                searched.stream().map(Path::toString).toList()) + ")",
                socket,
                null,
                null);
    }

    private Process launch(Path executable, Path socket, Path log) {
        try {
            createPrivately(log);
        } catch (IOException e) {
            throw DaemonException.startFailed(
                    "cannot write the log of the daemon (" + e.getMessage() + ")", socket, log, e);
        }
        var builder = new ProcessBuilder(executable.toString())
                .directory(workingDirectory.toFile())
                .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
        builder.environment().clear();
        builder.environment().putAll(environment);
        try {
            return builder.start();
        } catch (IOException e) {
            throw DaemonException.startFailed(
                    executable + " could not be launched (" + e.getMessage() + ")", socket, log, e);
        }
    }

    /**
     * An empty log, readable by the user only, in a folder that is created private when it is missing (the daemon
     * refuses a folder that is not). A log left by the previous start is replaced, a link is not followed.
     */
    private static void createPrivately(Path log) throws IOException {
        Files.createDirectories(
                log.getParent(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Files.deleteIfExists(log);
        Files.createFile(log, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    }

    private DaemonClient awaitSocket(Process process, Path socket, Path log) {
        var deadline = System.nanoTime() + delay.toNanos();
        while (true) {
            // Looked at before the connection: a daemon that ended while the socket still refused is a failed start,
            // and one that ended because another daemon answers on the socket is reached by the connection.
            var ended = !process.isAlive();
            var client = DaemonClient.tryConnect(socket);
            if (client.isPresent()) {
                return client.get();
            }
            if (ended) {
                throw DaemonException.exited(process.exitValue(), socket, log);
            }
            if (System.nanoTime() - deadline >= 0) {
                throw DaemonException.startFailed(
                        DAEMON_NAME + " did not listen on " + socket + " within " + seconds(delay)
                                + " seconds; its output is in " + log,
                        socket,
                        log,
                        null);
            }
            pause(process, socket, log);
        }
    }

    /** Waits a moment, or until the daemon ends: the next look at it is the next turn of the loop. */
    private static void pause(Process process, Path socket, Path log) {
        try {
            process.waitFor(POLL_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw DaemonException.startFailed("the wait for " + DAEMON_NAME + " was interrupted", socket, log, e);
        }
    }

    private static String seconds(Duration duration) {
        return String.valueOf(duration.toMillis() / 1000.0);
    }
}
