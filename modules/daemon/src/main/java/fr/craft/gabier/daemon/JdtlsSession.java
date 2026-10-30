package fr.craft.gabier.daemon;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;

/**
 * A jdtls that is ready: started on a project root with a data folder of its own, initialized, and known to have
 * imported the projects that were expected of it. The session waits for that without guessing a delay: for the answer
 * to {@code initialize}, for the {@code ServiceReady} status, and for the list of projects jdtls gives. It fails at once
 * when the process dies (with its exit code) or when jdtls reports an error, and every timeout names what was awaited.
 *
 * <p>Closing the session asks jdtls to shut down and ends the whole process tree (the launcher starts a java process
 * in turn), then deletes the data folder; it does so too when the start fails.
 */
final class JdtlsSession implements AutoCloseable {

    private static final String DATA_DIRECTORY_PREFIX = "gabier-jdtls-data-";

    private final JdtlsSettings settings;
    private final Process process;
    private final Path data;
    private final Consumer<String> log;
    private final JdtlsClient client;
    private final LanguageServer server;
    private final JdtlsWatch watch;
    private final Future<Void> listening;
    private List<Path> projects = List.of();
    private boolean closed;

    private JdtlsSession(
            JdtlsSettings settings,
            Path data,
            JdtlsConnection connection,
            Consumer<String> log,
            Consumer<PublishDiagnosticsParams> diagnostics) {
        this.settings = settings;
        this.process = connection.process();
        this.data = data;
        this.log = log;
        this.client = new JdtlsClient(log, diagnostics);
        var launcher = LSPLauncher.createClientLauncher(client, connection.fromJdtls(), connection.toJdtls());
        this.listening = launcher.startListening();
        this.server = launcher.getRemoteProxy();
        this.watch = new JdtlsWatch(process, client.error(), settings.timeout());
    }

    /**
     * Starts jdtls and waits until it is ready. Nothing is left behind when this fails: the process tree is ended and
     * the data folder deleted before the failure is thrown.
     *
     * @param starter starts the jdtls process, given the data folder
     * @param log receives a line for what happens (the start, the statuses of jdtls, a shutdown that did not go well)
     * @param diagnostics receives every publication of diagnostics jdtls sends, from the first message on
     * @throws JdtlsException when jdtls dies (its exit code is in the message), reports an error, or does not become
     *     ready in time
     */
    static JdtlsSession start(
            JdtlsStarter starter,
            JdtlsSettings settings,
            Consumer<String> log,
            Consumer<PublishDiagnosticsParams> diagnostics)
            throws IOException, InterruptedException {
        Path data = Files.createTempDirectory(DATA_DIRECTORY_PREFIX);
        JdtlsConnection connection;
        try {
            connection = starter.start(data);
        } catch (IOException | RuntimeException failure) {
            delete(data);
            throw failure;
        }
        var session = new JdtlsSession(settings, data, connection, log, diagnostics);
        log.accept("jdtls started, pid " + session.pid() + ", data " + data);
        try {
            session.awaitReady();
        } catch (InterruptedException | RuntimeException failure) {
            try {
                session.close();
            } catch (IOException | InterruptedException | RuntimeException closing) {
                failure.addSuppressed(closing);
            }
            throw failure;
        }
        return session;
    }

    long pid() {
        return process.pid();
    }

    Path dataDirectory() {
        return data;
    }

    /** The folders of the projects jdtls has imported, as it listed them when the session became ready. */
    List<Path> projects() {
        return projects;
    }

    /** The raw language server, for whoever keeps talking to jdtls. */
    LanguageServer server() {
        return server;
    }

    /**
     * Waits that fail when jdtls dies or reports an error and share a new deadline, as long as the timeout of the
     * settings, started now: for a request made long after the start, whose deadline must not be the one of the start.
     */
    JdtlsWatch newWatch() {
        return new JdtlsWatch(process, client.error(), settings.timeout());
    }

    private void awaitReady() throws InterruptedException {
        var initialize = new InitializeParams();
        initialize.setRootUri(settings.projectRoot().toUri().toString());
        initialize.setCapabilities(JdtlsCapabilities.forRefactoring());
        initialize.setInitializationOptions(Map.of());
        watch.await("the answer to initialize", () -> server.initialize(initialize));
        // Telling jdtls that the client is ready is part of the wait: writing to a jdtls that died throws.
        watch.await("the language/status notification of type ServiceReady", () -> {
            server.initialized(new InitializedParams());
            return client.serviceReady();
        });
        projects = awaitProjects();
    }

    /** Asks {@code java.project.getAll} until jdtls lists every expected project, and returns what it listed. */
    private List<Path> awaitProjects() throws InterruptedException {
        List<Path> expected = settings.expectedProjects();
        List<Path> listed = List.of();
        List<Path> missing = expected;
        while (true) {
            String what = "jdtls to list the modules " + expected + " with java.project.getAll (still missing "
                    + missing + ", it listed " + listed + ")";
            var command = new ExecuteCommandParams("java.project.getAll", List.of());
            Object answer = watch.await(what, () -> server.getWorkspaceService().executeCommand(command));
            if (!(answer instanceof List<?> uris)) {
                throw new JdtlsException(
                        "java.project.getAll answered " + answer + ", which is not a list of projects");
            }
            // jdtls names a project by the URI of its folder.
            listed = uris.stream()
                    .map(uri -> Path.of(URI.create(String.valueOf(uri)))
                            .toAbsolutePath()
                            .normalize())
                    .toList();
            List<Path> imported = listed;
            missing = expected.stream()
                    .filter(project -> !imported.contains(project))
                    .toList();
            if (missing.isEmpty()) {
                return listed;
            }
            watch.pause(what, settings.pollInterval());
        }
    }

    /**
     * Asks jdtls to shut down, ends whatever is left of its process tree and deletes the data folder, even when the
     * shutdown fails or this thread is interrupted. Closing a session already closed does nothing.
     */
    @Override
    public void close() throws IOException, InterruptedException {
        if (closed) {
            return;
        }
        closed = true;
        List<ProcessHandle> tree = Stream.concat(process.descendants(), Stream.of(process.toHandle()))
                .toList();
        try {
            if (askToLeave()) {
                process.waitFor(settings.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS);
            }
        } finally {
            try {
                tree.forEach(ProcessHandle::destroyForcibly);
                for (ProcessHandle member : tree) {
                    awaitEnd(member);
                }
            } finally {
                listening.cancel(true);
                delete(data);
            }
        }
    }

    /** True when jdtls was told to exit. A dead jdtls is not asked: it would never answer. */
    private boolean askToLeave() throws InterruptedException {
        if (!process.isAlive()) {
            return false;
        }
        try {
            server.shutdown().get(settings.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS);
            server.exit();
            return true;
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            // A dead server makes the proxy throw an unchecked JsonRpcException: the kill must still happen.
            log.accept("jdtls did not shut down cleanly: " + e);
            return false;
        }
    }

    private void awaitEnd(ProcessHandle member) throws InterruptedException {
        try {
            member.onExit().get(settings.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException e) {
            log.accept("process " + member.pid() + " is still running: " + e);
        }
    }

    private static void delete(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
    }
}
