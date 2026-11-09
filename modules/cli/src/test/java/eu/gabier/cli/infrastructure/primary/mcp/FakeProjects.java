package eu.gabier.cli.infrastructure.primary.mcp;

import eu.gabier.cli.domain.Daemon;
import eu.gabier.cli.domain.Projects;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The projects of the tests of the server, hand-written: each project is a folder with the daemon the test scripted for
 * it, and a file belongs to the project whose folder is the deepest above it, else to the project of the launch. What
 * finds a project on a disk (the {@code .git}) is the business of the adapter, which has tests of its own.
 */
final class FakeProjects implements Projects {

    /** The folder of the project a test launches the server in, when it has only one project. */
    static final Path LAUNCHED = Path.of("/work");

    private final Path launched;
    private final Map<Path, Daemon> daemons = new LinkedHashMap<>();

    private FakeProjects(Path launched, Daemon daemon) {
        this.launched = launched;
        daemons.put(launched, daemon);
    }

    /** One project, {@link #LAUNCHED}, whose daemon is this one: every file is in it. */
    static FakeProjects only(Daemon daemon) {
        return new FakeProjects(LAUNCHED, daemon);
    }

    /** A server launched in this project, whose daemon is this one. */
    static FakeProjects launchedIn(Path project, Daemon daemon) {
        return new FakeProjects(project, daemon);
    }

    /** Another project, in this folder, with its daemon. */
    FakeProjects and(Path project, Daemon daemon) {
        daemons.put(project, daemon);

        return this;
    }

    @Override
    public Path launched() {
        return launched;
    }

    @Override
    public Path projectOf(Path file) {
        return daemons.keySet().stream()
                .filter(file::startsWith)
                .max(Comparator.comparingInt(Path::getNameCount))
                .orElse(launched);
    }

    @Override
    public Daemon daemonOf(Path project) {
        return daemons.get(project);
    }
}
