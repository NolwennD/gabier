package eu.gabier.daemon;

import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.CleanUp;
import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.OfferedAction;
import eu.gabier.editing.domain.OfferedActions;
import eu.gabier.editing.domain.PackageName;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.Range;
import eu.gabier.editing.domain.ReferenceLocations;
import eu.gabier.editing.domain.TypeKind;
import eu.gabier.protocol.JdtlsProgress;
import eu.gabier.protocol.JdtlsState;
import eu.gabier.testing.domain.BuiltProject;
import eu.gabier.testing.domain.ChangedBuild;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The project a daemon serves, with its jdtls started when it is needed: the first request that asks the language
 * server starts it and waits until it is ready, the following ones find it started, and the daemon starting does not
 * start it, nor does a {@code status}.
 *
 * <p>There is one start at a time, whatever the number of requests: the ones that arrive while it goes on wait for the
 * same one. A start that fails is the failure of every request that waited for it, and stays visible as {@code
 * FAILED}; the next request ends what is left of that jdtls and starts another. A jdtls that died while ready is
 * {@code FAILED} too, and treated the same way. Closing ends the start in progress or the running jdtls, and nothing
 * is started afterwards.
 */
final class ProjectServer implements ServedProject {

    /** How long jdtls may take to start (all its waits together), then to answer each request. */
    static final Duration TIMEOUT = Duration.ofMinutes(5);

    /** Starts a language server in the background: the one seam between the project and jdtls. */
    @FunctionalInterface
    interface Starter {

        /** Returns at once, the start going on behind the server. */
        StartedLanguageServer start();
    }

    private final Path root;
    private final Starter starter;
    private final Consumer<String> log;
    private volatile @Nullable StartedLanguageServer current;
    private boolean closed;

    ProjectServer(Path root, Starter starter, Consumer<String> log) {
        this.root = root;
        this.starter = starter;
        this.log = log;
    }

    /**
     * The project served by a real jdtls: the launcher is the one {@link JdtlsLocation} finds ({@code GABIER_JDTLS},
     * else the pinned jdtls installed under the home folder, else a {@code jdtls} on the {@code PATH}), and a project
     * that cannot start it is told so by the first request, not by the daemon at its start.
     *
     * @param root the folder jdtls is initialized on, which must be the project jdtls imports
     * @param home the home folder of the user
     * @param log receives a line for what happens to jdtls
     */
    static ProjectServer jdtls(Path root, Map<String, String> environment, Path home, Consumer<String> log) {
        var settings = JdtlsSettings.of(root, List.of(), TIMEOUT);
        // Looked for at each start, not once: a jdtls installed after the daemon began is found by the next request.
        JdtlsStarter jdtls = data -> launcher(JdtlsLocation.launcher(environment, home), environment, home, log)
                .start(data);

        return new ProjectServer(
                root, () -> JdtlsLanguageServer.start(jdtls, settings, JdtlsLanguageServer.QUIET, log), log);
    }

    /** The launcher, with the option that makes jdtls share the indexes of its libraries when its version is known. */
    private static JdtlsLauncher launcher(
            Path launcher, Map<String, String> environment, Path home, Consumer<String> log) {
        var indexes = SharedIndexes.of(launcher, environment, home);
        if (indexes.isEmpty()) {
            log.accept("the indexes of the libraries are not shared: " + launcher
                    + " is not in a distribution of jdtls that tells its version");
        }

        return new JdtlsLauncher(
                launcher,
                Path.of(System.getProperty("java.home")),
                indexes.map(SharedIndexes::jvmArgument).stream().toList());
    }

    @Override
    public Path root() {
        return root;
    }

    @Override
    public JdtlsState state() {
        var server = current;

        return server == null ? JdtlsState.NOT_STARTED : server.state();
    }

    @Override
    public Optional<JdtlsProgress> jdtlsProgress() {
        var server = current;

        return server == null ? Optional.empty() : server.progress();
    }

    @Override
    public Optional<String> jdtlsFailure() {
        var server = current;

        return server == null ? Optional.empty() : server.failure();
    }

    @Override
    public void warmUp() {
        started();
    }

    @Override
    public List<List<Diagnostic>> diagnostics(List<Path> files) {
        return ready().diagnostics(files);
    }

    @Override
    public List<DiagnosedFile> projectDiagnostics() {
        return ready().projectDiagnostics();
    }

    @Override
    public DiskChanges watchDisk() {
        return ready().watchDisk();
    }

    @Override
    public BuiltProject built() {
        return ready().built();
    }

    @Override
    public ChangedBuild builtWithChanges() {
        return ready().builtWithChanges();
    }

    @Override
    public ChangeSet rename(Path file, Position position, String newName) {
        return ready().rename(file, position, newName);
    }

    @Override
    public ChangeSet extractVariable(Path file, Range selection, String name) {
        return ready().extractVariable(file, selection, name);
    }

    @Override
    public ChangeSet extractConstant(Path file, Range selection, String name) {
        return ready().extractConstant(file, selection, name);
    }

    @Override
    public ChangeSet extractField(Path file, Range selection, String name) {
        return ready().extractField(file, selection, name);
    }

    @Override
    public ChangeSet extractMethod(Path file, Range selection, String name) {
        return ready().extractMethod(file, selection, name);
    }

    @Override
    public ChangeSet moveTypeToFile(Path file, Position position) {
        return ready().moveTypeToFile(file, position);
    }

    @Override
    public ChangeSet moveToPackage(List<Path> files, PackageName target) {
        return ready().moveToPackage(files, target);
    }

    @Override
    public ChangeSet inline(Path file, Position position) {
        return ready().inline(file, position);
    }

    @Override
    public ChangeSet createType(Path file, TypeKind kind) {
        return ready().createType(file, kind);
    }

    @Override
    public ChangeSet createPackageInfo(Path file) {
        return ready().createPackageInfo(file);
    }

    @Override
    public ReferenceLocations references(Path file, Position position, boolean includeDeclaration) {
        return ready().references(file, position, includeDeclaration);
    }

    @Override
    public OfferedActions codeActions(Path file, Range range) {
        return ready().codeActions(file, range);
    }

    @Override
    public ChangeSet codeActionEdit(Path file, Range range, OfferedAction action) {
        return ready().codeActionEdit(file, range, action);
    }

    @Override
    public Optional<ChangeSet> organizeImports(Path file) {
        return ready().organizeImports(file);
    }

    @Override
    public Optional<ChangeSet> cleanUp(Path file, List<CleanUp> cleanUps) {
        return ready().cleanUp(file, cleanUps);
    }

    @Override
    public void close() throws IOException, InterruptedException {
        StartedLanguageServer server;
        synchronized (this) {
            closed = true;
            server = current;
            current = null;
        }
        if (server != null) {
            server.close();
        }
    }

    /** The language server once it is ready, started by this request if nobody started it. */
    private StartedLanguageServer ready() {
        var server = started();
        try {
            server.awaitReady();
        } catch (JdtlsException failure) {
            throw JdtlsStartException.of(root, failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException("Interrupted while waiting for jdtls to start", interrupted);
        }

        return server;
    }

    /** The server being started or started; a new one when there is none yet or when the last start failed. */
    private synchronized StartedLanguageServer started() {
        if (closed) {
            throw LanguageServerException.notReady();
        }
        var server = current;
        if (server != null && server.state() == JdtlsState.FAILED) {
            log.accept("jdtls failed: ending it and starting another");
            end(server);
            server = null;
        }
        if (server == null) {
            server = starter.start();
            current = server;
        }

        return server;
    }

    /**
     * Ends a server that failed, so that nothing of it is left (a jdtls that died while ready still holds its data
     * folder): its failure to end is told, it does not stop the next start.
     */
    private void end(StartedLanguageServer failed) {
        try {
            failed.close();
        } catch (IOException failure) {
            log.accept("could not end the jdtls that failed: " + failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
