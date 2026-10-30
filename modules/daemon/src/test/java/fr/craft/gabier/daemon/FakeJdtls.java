package fr.craft.gabier.daemon;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * A language server standing in for jdtls, in this process: it speaks LSP4J over pipes to the adapter, and a child
 * process ({@code sleep}, {@code sh -c "exit 3"}) stands in for the jdtls process, so that its exit, its tree and its
 * death can be seen. The test scripts it before {@code start}: what it answers, and when it stays silent.
 */
final class FakeJdtls implements JdtlsStarter, AutoCloseable {

    /** The client as the server sees it: the LSP one, and the {@code language/status} extension of jdtls. */
    interface StatusClient extends LanguageClient {

        @JsonNotification("language/status")
        void status(JdtlsClient.Status status);
    }

    /** What the server does with {@code shutdown}. */
    enum Shutdown {
        ANSWERS,
        FAILS,
        NEVER_ANSWERS
    }

    /** The command of the child process that stands in for the jdtls process. */
    private String[] child = {"sleep", "300"};

    private CompletableFuture<ProcessHandle> onExitOfHandle;
    private boolean answersInitialize = true;
    private String status = "ServiceReady";
    private Shutdown shutdown = Shutdown.ANSWERS;
    private Runnable onInitialize = () -> {};
    private Runnable onShutdown = () -> {};
    private final List<List<String>> projectAnswers = new ArrayList<>();
    private final AtomicInteger projectRequests = new AtomicInteger();

    private final CompletableFuture<InitializeParams> initializeParams = new CompletableFuture<>();
    private final AtomicInteger initializedNotifications = new AtomicInteger();
    private final AtomicInteger shutdownRequests = new AtomicInteger();
    private final AtomicInteger exitNotifications = new AtomicInteger();
    private final CountDownLatch exited = new CountDownLatch(1);
    private final List<String> commands = new CopyOnWriteArrayList<>();
    private boolean answersNothingToProjects;
    private String publishesOnInitialized = "";
    private final Map<String, List<List<Diagnostic>>> publicationsOnOpen = new ConcurrentHashMap<>();
    private Duration pauseBetweenPublications = Duration.ZERO;
    private final List<Opened> opened = new CopyOnWriteArrayList<>();
    private final List<String> closed = new CopyOnWriteArrayList<>();

    private volatile Process process;
    private volatile Path data;
    private volatile Future<Void> serving;
    private final List<AutoCloseable> channels = new ArrayList<>();

    /** The child that stands in for the jdtls process; the default one sleeps until it is killed. */
    FakeJdtls child(String... command) {
        this.child = command;
        return this;
    }

    /** The process survives the kill signal as far as its handle tells: {@code onExit()} answers this future. */
    FakeJdtls whoseHandleExitsAs(CompletableFuture<ProcessHandle> onExit) {
        this.onExitOfHandle = onExit;
        return this;
    }

    FakeJdtls neverAnswersInitialize() {
        this.answersInitialize = false;
        return this;
    }

    /** The {@code language/status} sent after {@code initialized}: {@code ServiceReady} by default, or none. */
    FakeJdtls sendsStatus(String type) {
        this.status = type;
        return this;
    }

    FakeJdtls shutdown(Shutdown shutdown) {
        this.shutdown = shutdown;
        return this;
    }

    FakeJdtls onInitialize(Runnable action) {
        this.onInitialize = action;
        return this;
    }

    FakeJdtls onShutdown(Runnable action) {
        this.onShutdown = action;
        return this;
    }

    /** {@code java.project.getAll} answers {@code null}, which is not a list of projects. */
    FakeJdtls answersNothingToProjects() {
        this.answersNothingToProjects = true;
        return this;
    }

    /** Publishes an empty list of diagnostics for the URI, right after {@code initialized} and before any status. */
    FakeJdtls publishesDiagnosticsFor(String uri) {
        this.publishesOnInitialized = uri;
        return this;
    }

    /** What jdtls was sent with a {@code didOpen}. */
    record Opened(String uri, String text) {}

    /**
     * Publishes these diagnostics for the file when it is opened, in the stages given (one publication per list), as
     * jdtls does for a file it opens: a quick one, then the one after the build.
     */
    @SafeVarargs
    final FakeJdtls publishesOnOpen(Path file, List<Diagnostic>... stages) {
        publicationsOnOpen.put(file.toUri().toString(), List.of(stages));
        return this;
    }

    /** The time the fake lets pass between two stages of the publication of a file. */
    FakeJdtls pausesBetweenPublications(Duration pause) {
        this.pauseBetweenPublications = pause;
        return this;
    }

    /** What {@code java.project.getAll} answers, in turn; the last answer is repeated. */
    FakeJdtls listsProjects(String... projectUris) {
        projectAnswers.add(List.of(projectUris));
        return this;
    }

    @Override
    public JdtlsConnection start(Path dataDirectory) throws IOException {
        this.data = dataDirectory;
        Process started = new ProcessBuilder(child).start();
        this.process = onExitOfHandle == null ? started : new StubbornProcess(started, onExitOfHandle);
        Pipe toServer = Pipe.open();
        Pipe fromServer = Pipe.open();
        channels.addAll(List.of(toServer.sink(), toServer.source(), fromServer.sink(), fromServer.source()));
        Launcher<StatusClient> launcher = new LSPLauncher.Builder<StatusClient>()
                .setLocalService(new Server())
                .setRemoteInterface(StatusClient.class)
                .setInput(Channels.newInputStream(toServer.source()))
                .setOutput(Channels.newOutputStream(fromServer.sink()))
                .create();
        this.serving = launcher.startListening();
        this.client = launcher.getRemoteProxy();
        return new JdtlsConnection(
                process, Channels.newInputStream(fromServer.source()), Channels.newOutputStream(toServer.sink()));
    }

    private volatile StatusClient client;

    /** Closes the pipes: from now on, writing to the fake fails as writing to a dead jdtls does. */
    void closePipes() throws Exception {
        for (AutoCloseable channel : channels) {
            channel.close();
        }
    }

    private void pause() {
        try {
            Thread.sleep(pauseBetweenPublications);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    Process process() {
        return process;
    }

    Path data() {
        return data;
    }

    InitializeParams initializeParams() {
        return initializeParams.join();
    }

    int initializedNotifications() {
        return initializedNotifications.get();
    }

    int shutdownRequests() {
        return shutdownRequests.get();
    }

    /** Waits for the {@code exit} notification, which travels on its own thread: true when it came in time. */
    boolean awaitExitNotification() throws InterruptedException {
        return exited.await(10, TimeUnit.SECONDS);
    }

    int exitNotifications() {
        return exitNotifications.get();
    }

    List<Opened> opened() {
        return opened;
    }

    /** The documents closed, once there are as many as expected: the notifications travel on their own thread. */
    List<String> closed(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (closed.size() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        return List.copyOf(closed);
    }

    List<String> commands() {
        return commands;
    }

    int projectRequests() {
        return projectRequests.get();
    }

    /** Whatever a test left running is ended, so that a red test leaves nothing behind. */
    @Override
    public void close() throws Exception {
        if (process != null) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
        closePipes();
        if (serving != null) {
            serving.cancel(true);
        }
    }

    private final class Server implements LanguageServer {

        @Override
        public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
            initializeParams.complete(params);
            onInitialize.run();
            if (!answersInitialize) {
                return new CompletableFuture<>();
            }
            return CompletableFuture.completedFuture(new InitializeResult(new ServerCapabilities()));
        }

        @Override
        public void initialized(InitializedParams params) {
            initializedNotifications.incrementAndGet();
            if (!publishesOnInitialized.isEmpty()) {
                client.publishDiagnostics(new PublishDiagnosticsParams(publishesOnInitialized, List.of()));
            }
            if (!status.isEmpty()) {
                client.status(new JdtlsClient.Status(status, status));
            }
        }

        @Override
        public CompletableFuture<Object> shutdown() {
            shutdownRequests.incrementAndGet();
            onShutdown.run();
            return switch (shutdown) {
                case ANSWERS -> CompletableFuture.completedFuture(null);
                case FAILS -> CompletableFuture.failedFuture(new IllegalStateException("shutdown refused"));
                case NEVER_ANSWERS -> new CompletableFuture<>();
            };
        }

        @Override
        public void exit() {
            exitNotifications.incrementAndGet();
            // Like jdtls, which leaves when it is told to exit.
            process.destroyForcibly();
            exited.countDown();
        }

        @Override
        public TextDocumentService getTextDocumentService() {
            return new TextDocumentService() {
                @Override
                public void didOpen(DidOpenTextDocumentParams params) {
                    String uri = params.getTextDocument().getUri();
                    opened.add(new Opened(uri, params.getTextDocument().getText()));
                    var stages = publicationsOnOpen.getOrDefault(uri, List.of());
                    for (int stage = 0; stage < stages.size(); stage++) {
                        if (stage > 0) {
                            pause();
                        }
                        client.publishDiagnostics(new PublishDiagnosticsParams(uri, stages.get(stage)));
                    }
                }

                @Override
                public void didChange(DidChangeTextDocumentParams params) {}

                @Override
                public void didClose(DidCloseTextDocumentParams params) {
                    closed.add(params.getTextDocument().getUri());
                }

                @Override
                public void didSave(DidSaveTextDocumentParams params) {}
            };
        }

        @Override
        public WorkspaceService getWorkspaceService() {
            return new WorkspaceService() {
                @Override
                public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
                    commands.add(params.getCommand());
                    int asked = projectRequests.getAndIncrement();
                    if (answersNothingToProjects) {
                        return CompletableFuture.completedFuture(null);
                    }
                    List<List<String>> answers = projectAnswers;
                    return CompletableFuture.completedFuture(answers.get(Math.min(asked, answers.size() - 1)));
                }

                @Override
                public void didChangeConfiguration(DidChangeConfigurationParams params) {}

                @Override
                public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {}
            };
        }
    }
}
