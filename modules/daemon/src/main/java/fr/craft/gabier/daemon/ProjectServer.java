package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.CleanUp;
import fr.craft.gabier.editing.domain.Diagnostic;
import fr.craft.gabier.editing.domain.LanguageServerException;
import fr.craft.gabier.editing.domain.PackageName;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.editing.domain.Range;
import fr.craft.gabier.editing.domain.ReferenceLocations;
import fr.craft.gabier.editing.domain.TypeKind;
import fr.craft.gabier.protocol.JdtlsState;
import fr.craft.gabier.protocol.KeptWorkspaces;
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

    private static final String LAUNCHER_VARIABLE = "GABIER_JDTLS";

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
     * The project served by a real jdtls: the launcher is the one {@code GABIER_JDTLS} names, and a project that
     * cannot start it is told so by the first request, not by the daemon at its start. Its workspace is temporary, or
     * kept in the folder that {@value KeptWorkspaces#VARIABLE} names.
     *
     * @param root the folder jdtls is initialized on, which must be the project jdtls imports
     * @param log receives a line for what happens to jdtls
     */
    static ProjectServer jdtls(Path root, Map<String, String> environment, Consumer<String> log) {
        var settings = JdtlsSettings.of(root, List.of(), TIMEOUT)
                .withWorkspace(KeptWorkspaces.folder(environment)
                        .map(JdtlsWorkspace.temporary()::keptIn)
                        .orElseGet(JdtlsWorkspace::temporary));
        var launcher = environment.getOrDefault(LAUNCHER_VARIABLE, "");
        JdtlsStarter jdtls = launcher.isBlank()
                ? data -> {
                    throw new IOException(LAUNCHER_VARIABLE + " is not set: it must be the path of the jdtls launcher");
                }
                : launcher(Path.of(launcher), environment, log);

        return new ProjectServer(
                root, () -> JdtlsLanguageServer.start(jdtls, settings, JdtlsLanguageServer.QUIET, log), log);
    }

    /** The launcher, with the option that makes jdtls share the indexes of its libraries when its version is known. */
    private static JdtlsLauncher launcher(Path launcher, Map<String, String> environment, Consumer<String> log) {
        var indexes = SharedIndexes.of(launcher, environment, Path.of(System.getProperty("user.home")));
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
    public Optional<String> jdtlsFailure() {
        var server = current;

        return server == null ? Optional.empty() : server.failure();
    }

    @Override
    public List<Diagnostic> diagnostics(Path file) {
        return ready().diagnostics(file);
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
    public ReferenceLocations references(Path file, Position position, boolean includeDeclaration) {
        return ready().references(file, position, includeDeclaration);
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
