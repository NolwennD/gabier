package eu.gabier.cli.infrastructure.secondary;

import eu.gabier.cli.domain.Daemon;
import eu.gabier.cli.domain.Projects;
import eu.gabier.protocol.SocketLocation;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The projects reached on their Unix sockets: the daemon of a project is a {@link SocketDaemon} whose project is that
 * folder ({@code GABIER_PROJECT}), so that it listens where the daemon of that folder does, however the cli was
 * launched. A daemon is built the first time its project is asked for and kept; none is started before a request. The
 * project of the launch is {@link SocketLocation#projectRoot}: {@code GABIER_PROJECT}, else the nearest folder with a
 * {@code .git} above the working directory. When {@code GABIER_SOCKET} replaces the socket, every project is that one
 * socket.
 */
public final class SocketProjects implements Projects {

    private static final String GIT = ".git";

    private final Map<String, String> environment;
    private final PrintStream err;
    private final Duration noticeDelay;
    private final Map<Path, Daemon> daemons = new ConcurrentHashMap<>();

    /**
     * The projects of a cli with this environment.
     *
     * @param environment the environment of the cli, where the project of the launch and the sockets are resolved
     * @param err where the notice of a request that waits for jdtls is printed
     * @param noticeDelay how long a request waits before that notice
     */
    public SocketProjects(Map<String, String> environment, PrintStream err, Duration noticeDelay) {
        this.environment = Map.copyOf(environment);
        this.err = err;
        this.noticeDelay = noticeDelay;
    }

    @Override
    public Path launched() {
        return SocketLocation.projectRoot(environment, Path.of("").toAbsolutePath());
    }

    @Override
    public Path projectOf(Path file) {
        for (var folder = realPathOf(file); folder != null; folder = folder.getParent()) {
            if (Files.exists(folder.resolve(GIT))) {
                return folder;
            }
        }

        return launched();
    }

    @Override
    public Daemon daemonOf(Path project) {
        return daemons.computeIfAbsent(project, this::reach);
    }

    private Daemon reach(Path project) {
        var inProject = new HashMap<>(environment);
        inProject.put(SocketLocation.PROJECT, project.toString());

        return new SocketDaemon(inProject, err, noticeDelay);
    }

    /**
     * The real path of the file, as the daemon sees it. A file that does not exist yet has none: its real path is the
     * one of the nearest folder above it that exists, under which the names that are missing follow, so that a file to
     * be created below a symlinked folder is in the same project as the files that exist there.
     */
    private static Path realPathOf(Path file) {
        return realPathOfNormalized(file.toAbsolutePath().normalize());
    }

    private static Path realPathOfNormalized(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException gone) {
            // The root of a file system always exists, so a path that does not has a folder above it.
            var folder = Objects.requireNonNull(path.getParent(), "the root of a file system exists");

            return realPathOfNormalized(folder).resolve(folder.relativize(path));
        }
    }
}
