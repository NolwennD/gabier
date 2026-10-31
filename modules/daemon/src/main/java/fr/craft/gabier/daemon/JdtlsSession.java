package fr.craft.gabier.daemon;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;

/**
 * A jdtls that is ready: started on a project root with a data folder of its own, initialized, and done with importing
 * the project. The session waits for that without guessing a delay: for the answer to {@code initialize} and for the
 * {@code ServiceReady} status, which jdtls sends once its import jobs are over (checked on the real jdtls: the
 * notifications of the import, then {@code ServiceReady}, then the list of projects is complete). It then asks the
 * projects jdtls imported, once. It fails at once when the process dies (with its exit code), when jdtls reports an
 * error, when a project that was expected is not listed, or when nothing at all was imported; every timeout names what
 * was awaited.
 *
 * <p>Closing the session asks jdtls to shut down, ends the whole process tree and deletes the data folder; it does so
 * too when the start fails. The launcher starts a java process in turn, and the tree of a process that died cannot be
 * read any more: the session therefore records the tree from the moment the process started, again every tree interval
 * of the settings (a quarter of a second), and a last time at the close. What the launcher started and stopped between
 * two readings, or started and left behind in less than that before dying, is not seen.
 */
final class JdtlsSession implements AutoCloseable {

    private static final String DATA_DIRECTORY_PREFIX = "gabier-jdtls-data-";

    private final JdtlsSettings settings;
    private final Process process;
    private final Path data;
    private final Consumer<String> log;
    private final JdtlsClient client;
    private final WatchedFiles watched;
    private final LanguageServer server;
    private final JdtlsWatch watch;
    private final Future<Void> listening;
    private List<Path> projects = List.of();
    private final Set<ProcessHandle> started = ConcurrentHashMap.newKeySet();
    private final Thread recorder;
    private final Future<Void> exitLog;
    private boolean closed;

    private JdtlsSession(
            JdtlsSettings settings,
            Path data,
            JdtlsConnection connection,
            Consumer<String> log,
            Consumer<PublishDiagnosticsParams> diagnostics,
            WatchedFiles watched) {
        this.settings = settings;
        this.watched = watched;
        this.process = connection.process();
        this.data = data;
        this.log = log;
        this.client = new JdtlsClient(log, diagnostics, watched::register, watched::unregister);
        var launcher = LSPLauncher.createClientLauncher(client, connection.fromJdtls(), connection.toJdtls());
        this.listening = launcher.startListening();
        this.server = launcher.getRemoteProxy();
        this.watch = new JdtlsWatch(process, client.error(), settings.timeout());
        // The reason of a death that nobody asked for, in the log of the daemon, whoever notices it and whenever.
        this.exitLog = process.onExit().thenAccept(ended -> log.accept("jdtls exited with code " + ended.exitValue()));
        this.recorder = Thread.ofPlatform()
                .daemon()
                .name("jdtls-process-tree-" + process.pid())
                .unstarted(this::record);
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
        // The state of the disk that jdtls starts from, before it reads anything.
        WatchedFiles watched = WatchedFiles.of(settings.projectRoot());
        Path data = Files.createTempDirectory(DATA_DIRECTORY_PREFIX);
        JdtlsConnection connection;
        try {
            connection = starter.start(data);
        } catch (IOException | RuntimeException failure) {
            delete(data);
            throw failure;
        }
        var session = new JdtlsSession(settings, data, connection, log, diagnostics, watched);
        log.accept("jdtls started, pid " + session.pid() + ", data " + data);
        session.recordTheTree();
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

    /** False once the jdtls process has exited, whatever the reason: killed, crashed or ended by its launcher. */
    boolean isAlive() {
        return process.isAlive();
    }

    /** The exit code of the jdtls process, once it is not alive. */
    int exitValue() {
        return process.exitValue();
    }

    Path dataDirectory() {
        return data;
    }

    /** The folders of the projects jdtls has imported, as it listed them when the session became ready. */
    List<Path> projects() {
        return projects;
    }

    /** The processes of the tree of jdtls that the session has seen so far, and will end at the close. */
    List<ProcessHandle> recordedProcesses() {
        return List.copyOf(started);
    }

    /** Records the tree now, then keeps recording it, until the close, while the launcher may start other processes. */
    private void recordTheTree() {
        started.addAll(process.descendants().toList());
        recorder.start();
    }

    private void record() {
        try {
            while (true) {
                started.addAll(process.descendants().toList());
                Thread.sleep(settings.treeInterval());
            }
        } catch (InterruptedException e) {
            // The close stops the recording.
        }
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

    /** The folder jdtls was started on. */
    Path projectRoot() {
        return settings.projectRoot();
    }

    /** The files jdtls asked to have watched that changed on disk since it was last told ({@link WatchedFiles}). */
    List<FileEvent> changesOnDisk() {
        return watched.changes();
    }

    /** Tells jdtls of changes on disk, as it asked to be. A notification: the answer to it is not a message. */
    void tell(List<FileEvent> changes) {
        log.accept("jdtls told of the changes on disk: " + changes);
        server.getWorkspaceService().didChangeWatchedFiles(new DidChangeWatchedFilesParams(changes));
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
        projects = importedProjects();
    }

    /**
     * Asks {@code java.project.getAll} once, when jdtls has said {@code ServiceReady}: jdtls says it after its import
     * jobs are over (the {@code initialized} handler waits for them first), so the list is final, not a draft to wait
     * on. A list without an expected project, or an empty one, is refused at once.
     */
    private List<Path> importedProjects() throws InterruptedException {
        List<Path> listed = listProjects();
        List<Path> missing = settings.expectedProjects().stream()
                .filter(project -> !listed.contains(project))
                .toList();
        if (!missing.isEmpty()) {
            throw new JdtlsException("jdtls is ready but did not import " + missing + " (it listed " + listed + ")");
        }
        if (listed.isEmpty()) {
            throw NoBuildToImportException.of(settings.projectRoot());
        }
        return listed;
    }

    private List<Path> listProjects() throws InterruptedException {
        var command = new ExecuteCommandParams("java.project.getAll", List.of());
        Object answer = watch.await(
                "the projects jdtls imported (java.project.getAll)",
                () -> server.getWorkspaceService().executeCommand(command));
        if (!(answer instanceof List<?> uris)) {
            throw new JdtlsException("java.project.getAll answered " + answer + ", which is not a list of projects");
        }
        // jdtls names a project by the URI of its folder.
        return uris.stream()
                .map(uri -> Path.of(URI.create(String.valueOf(uri)))
                        .toAbsolutePath()
                        .normalize())
                .toList();
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
        // The death that is asked for is not told as one.
        exitLog.cancel(false);
        recorder.interrupt();
        // What was recorded answers for the launcher having died since: its children outlive it.
        started.addAll(process.descendants().toList());
        started.add(process.toHandle());
        List<ProcessHandle> tree = List.copyOf(started);
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
