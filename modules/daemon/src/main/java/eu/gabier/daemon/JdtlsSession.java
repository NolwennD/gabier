package eu.gabier.daemon;

import eu.gabier.daemon.JdtlsRuntimes.Jdk;
import eu.gabier.daemon.JdtlsWorkspace.Remembered;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.protocol.ProjectJdks;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.jspecify.annotations.Nullable;

/**
 * A jdtls that is ready: started on a project root with a data folder of its own, initialized, and done with importing
 * the project. The session waits for that without guessing a delay: for the answer to {@code initialize} and for the
 * {@code ServiceReady} status, which jdtls sends once its import jobs are over (checked on the real jdtls: the
 * notifications of the import, then {@code ServiceReady}, then the list of projects is complete). It then asks the
 * projects jdtls imported, once. It fails at once when the process dies (with its exit code), when jdtls reports an
 * error, when a project that was expected is not listed, or when nothing at all was imported; every timeout names what
 * was awaited.
 *
 * <p>Closing the session asks jdtls to shut down, ends the whole process tree and releases the data folder
 * ({@link JdtlsWorkspace}: a temporary one is deleted, a kept one is written down for the next start when jdtls left
 * by itself and was ready, and emptied otherwise); it does so too when the start fails. The launcher starts a java process in turn, and the tree of a process that died cannot be
 * read any more: the session therefore records the tree from the moment the process started, again every tree interval
 * of the settings (a quarter of a second), and a last time at the close. What the launcher started and stopped between
 * two readings, or started and left behind in less than that before dying, is not seen.
 */
final class JdtlsSession implements AutoCloseable {

    private final JdtlsSettings settings;
    private final Process process;
    private final JdtlsWorkspace.Opened workspace;
    private final Consumer<String> log;
    private final JdtlsClient client;
    private final ImportChoices choices = new ImportChoices();
    private final WatchedFiles watched;
    private final JdtlsServer server;
    private final JdtlsWatch watch;
    private final Future<Void> listening;
    private List<Path> projects = List.of();
    /** The JDKs jdtls bound the projects to, as it said when it was ready: what the next start declares. */
    private List<Jdk> bound = List.of();
    /** What the project chose for the JDKs of its environments ({@link RuntimeSources}), read when the start began. */
    private RuntimeSources.Chosen chosen = new RuntimeSources.Chosen(List.of(), List.of());
    /** The JDKs the projects are bound to and who chose them, as {@code status} says them. */
    private volatile ProjectJdks jdks = ProjectJdks.NONE;

    private final Set<ProcessHandle> started = ConcurrentHashMap.newKeySet();
    private final Thread recorder;
    private final Future<Void> exitLog;
    private boolean closed;
    private volatile boolean ready;

    private JdtlsSession(
            JdtlsSettings settings,
            JdtlsWorkspace.Opened workspace,
            JdtlsConnection connection,
            Consumer<String> log,
            StartProgress progress,
            Consumer<PublishDiagnosticsParams> diagnostics,
            WatchedFiles watched) {
        this.settings = settings;
        this.watched = watched;
        this.process = connection.process();
        this.workspace = workspace;
        this.log = log;
        this.client =
                new JdtlsClient(log, progress::status, diagnostics, watched::register, watched::unregister, choices);
        var launcher = new LSPLauncher.Builder<JdtlsServer>()
                .setLocalService(client)
                .setRemoteInterface(JdtlsServer.class)
                .setInput(connection.fromJdtls())
                .setOutput(connection.toJdtls())
                .create();
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
     * the data folder deleted (or emptied, when it is kept) before the failure is thrown.
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
        return start(starter, settings, log, new StartProgress(), diagnostics);
    }

    /**
     * As {@link #start(JdtlsStarter, JdtlsSettings, Consumer, Consumer)}, keeping in {@code progress} where the start
     * is, from what jdtls says.
     */
    static JdtlsSession start(
            JdtlsStarter starter,
            JdtlsSettings settings,
            Consumer<String> log,
            StartProgress progress,
            Consumer<PublishDiagnosticsParams> diagnostics)
            throws IOException, InterruptedException {
        JdtlsWorkspace.Opened opened = open(starter, settings, log);
        try {
            return start(opened, starter, settings, log, progress, diagnostics);
        } catch (JdtlsException failure) {
            // A start on a workspace of a previous run that fails is run again on an empty one: the failure may be what
            // that run left (the workspace was emptied when the failed session closed).
            if (opened.remembered().isEmpty()) {
                throw failure;
            }
            log.accept("jdtls did not start on the kept workspace, it starts again on an empty one: "
                    + failure.getMessage());

            return start(open(starter, settings, log), starter, settings, log, progress, diagnostics);
        }
    }

    private static JdtlsSession start(
            JdtlsWorkspace.Opened opened,
            JdtlsStarter starter,
            JdtlsSettings settings,
            Consumer<String> log,
            StartProgress progress,
            Consumer<PublishDiagnosticsParams> diagnostics)
            throws IOException, InterruptedException {
        // The state of the disk that jdtls starts from, before it reads anything: the one the kept workspace was left
        // with, when it is reused.
        WatchedFiles watched = opened.remembered()
                .map(remembered -> WatchedFiles.of(settings.projectRoot(), remembered.disk()))
                .orElseGet(() -> WatchedFiles.of(settings.projectRoot()));
        JdtlsConnection connection;
        try {
            connection = starter.start(opened.data());
        } catch (IOException | RuntimeException failure) {
            settings.workspace().release(opened);
            throw failure;
        }
        var session = new JdtlsSession(settings, opened, connection, log, progress, diagnostics, watched);
        log.accept("jdtls started, pid " + session.pid() + ", data " + opened.data()
                + (opened.remembered().isPresent() ? " (kept from the previous run)" : ""));
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
        session.ready = true;
        return session;
    }

    private static JdtlsWorkspace.Opened open(JdtlsStarter starter, JdtlsSettings settings, Consumer<String> log)
            throws IOException {
        Optional<KeptWorkspace> kept = starter.keptWorkspace(settings.projectRoot());

        return kept.isPresent()
                ? settings.workspace().open(kept.get(), settings.timeout(), log)
                : settings.workspace().open(settings.timeout(), log);
    }

    /** The types jdtls could not choose an import for, in the request being answered. */
    ImportChoices importChoices() {
        return choices;
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
        return workspace.data();
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

    /** The JDKs the projects are bound to and who chose them; none until the session is ready. */
    ProjectJdks jdks() {
        return jdks;
    }

    /** The raw language server, for whoever keeps talking to jdtls. */
    JdtlsServer server() {
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

    /**
     * Follows the disk of the project for the files jdtls asked to have watched, with the events of the system: what
     * jdtls would have to be told of ({@link #changesOnDisk}) is known when it happens, not when it is looked for.
     */
    DiskChanges watchDisk() {
        return new FolderWatch(
                projectRoot(),
                new NioFolderEvents(),
                watched::fingerprint,
                watched::watches,
                FolderWatch.DEBOUNCE,
                FolderWatch.POLL_PACE);
    }

    /** True once jdtls has asked to have files watched, which it does late in its initialization ({@link WatchedFiles}). */
    boolean watchersRegistered() {
        return watched.registered();
    }

    /** Tells jdtls of changes on disk, as it asked to be. A notification: the answer to it is not a message. */
    void tell(List<FileEvent> changes) {
        log.accept("jdtls told of the changes on disk: " + changes);
        server.getWorkspaceService().didChangeWatchedFiles(new DidChangeWatchedFilesParams(changes));
    }

    /**
     * Gives jdtls a configuration, as {@code workspace/didChangeConfiguration}: a notification, which jdtls handles
     * before it reads the next message, so a request sent after it is answered with the new settings.
     */
    void configure(Map<String, Object> settings) {
        log.accept("jdtls configured: " + settings);
        server.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams(settings));
    }

    private void awaitReady() throws InterruptedException {
        var initialize = new InitializeParams();
        initialize.setRootUri(settings.projectRoot().toUri().toString());
        initialize.setCapabilities(JdtlsCapabilities.forRefactoring());
        chosen = settings.runtimes().read(settings.projectRoot());
        chosen.warnings().forEach(warning -> log.accept("JDKs: " + warning));
        chosen.choices()
                .forEach(choice -> log.accept("JDK chosen for " + choice.jdk().name() + ", " + choice.source() + ": "
                        + choice.jdk().home()));
        initialize.setInitializationOptions(Map.of(
                "settings", JdtlsSettings.javaPreferences(chosen.jdks()),
                "extendedClientCapabilities", JdtlsCapabilities.extended()));
        watch.await("the answer to initialize", () -> server.initialize(initialize));
        // Telling jdtls that the client is ready is part of the wait: writing to a jdtls that died throws.
        watch.await("the language/status notification of type ServiceReady", () -> {
            server.initialized(new InitializedParams());
            return client.serviceReady();
        });
        projects = importedProjects();
        bound = boundRuntimes();
        pinTheRuntimes();
        jdks = JdtlsRuntimes.describe(bound, chosen);
    }

    /**
     * Declares to jdtls the JDKs the projects are to be bound to ({@link JdtlsRuntimes}): jdtls makes each the default of
     * its execution environment and keeps that in the workspace, so that the next start, which builds before the client
     * says anything, binds them the same. They are the JDKs the project chose ({@link RuntimeSources}) in place of the
     * ones jdtls drew, and, in a workspace that is kept, the draw of jdtls for the other environments, unless the
     * workspace was already pinned to the same ones. A declaration that does not change what jdtls bound builds nothing
     * (measured), so nothing is sent then. What jdtls binds afterwards is asked again: it is what the status says.
     */
    private void pinTheRuntimes() throws InterruptedException {
        List<Jdk> wanted = JdtlsRuntimes.overlay(bound, chosen.jdks());
        List<Jdk> pinned = workspace.remembered().map(Remembered::runtimes).orElse(List.of());
        boolean pinTheDraw = workspace.kept().isPresent() && !bound.isEmpty() && !bound.equals(pinned);
        if (!wanted.equals(bound) || pinTheDraw) {
            configure(JdtlsRuntimes.settings(wanted));
            bound = boundRuntimes();
        }
    }

    /**
     * The JDKs jdtls bound the projects to ({@code java.project.getSettings}, {@value JdtlsRuntimes#VM_LOCATION}), for
     * the status and for the next start to declare. A start does not fail for it: when jdtls does not say, nothing is
     * known of them.
     */
    private List<Jdk> boundRuntimes() throws InterruptedException {
        List<@Nullable Object> answers = new ArrayList<>();
        for (Path project : projects) {
            var command = new ExecuteCommandParams(
                    "java.project.getSettings",
                    List.of(project.toUri().toString(), List.of(JdtlsRuntimes.VM_LOCATION)));
            try {
                answers.add(watch.await(
                        "the JDK of " + project + " (java.project.getSettings)",
                        () -> server.getWorkspaceService().executeCommand(command)));
            } catch (JdtlsException | LanguageServerException failure) {
                log.accept("the JDK of " + project + " is not known: " + failure.getMessage());
                return List.of();
            }
        }

        return JdtlsRuntimes.boundIn(answers);
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
     * Asks jdtls to shut down, ends whatever is left of its process tree and releases the data folder (a kept one is
     * written down when jdtls left by itself), even when the shutdown fails or this thread is interrupted. Closing a session already closed does nothing.
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
        boolean ended = false;
        try {
            ended = askToLeave() && process.waitFor(settings.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } finally {
            try {
                tree.forEach(ProcessHandle::destroyForcibly);
                for (ProcessHandle member : tree) {
                    awaitEnd(member);
                }
            } finally {
                listening.cancel(true);
                try {
                    // Only a jdtls that was ready and left by itself has saved its workspace: the others empty it.
                    if (ended && ready && workspace.kept().isPresent()) {
                        keep();
                    }
                } finally {
                    settings.workspace().release(workspace);
                }
            }
        }
    }

    /** Writes down what the next start on this workspace needs; a workspace that cannot is released empty. */
    private void keep() {
        try {
            settings.workspace().keep(workspace, new Remembered(watched.remembered(), bound));
        } catch (IOException failure) {
            log.accept("the workspace " + workspace.data() + " cannot be kept: " + failure);
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
}
