package fr.craft.gabier.daemon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.jspecify.annotations.Nullable;

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

        @JsonRequest("workspace/executeClientCommand")
        CompletableFuture<Object> executeClientCommand(ExecuteCommandParams command);
    }

    /** What the server does with {@code shutdown}. */
    enum Shutdown {
        ANSWERS,
        FAILS,
        NEVER_ANSWERS
    }

    private static final String METHOD = "workspace/didChangeWatchedFiles";

    /** The command of the child process that stands in for the jdtls process. */
    private String[] child = {"sleep", "300"};

    private int children;
    private volatile List<ProcessHandle> launched = List.of();
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
    private volatile boolean acknowledgesChanges = true;
    private volatile Duration acknowledgmentDelay = Duration.ZERO;
    private final List<String> received = new CopyOnWriteArrayList<>();
    private String publishesOnInitialized = "";
    private final Map<String, List<List<Diagnostic>>> publicationsOnOpen = new ConcurrentHashMap<>();
    private Duration pauseBetweenPublications = Duration.ZERO;
    private final List<Opened> opened = new CopyOnWriteArrayList<>();
    private final List<String> closed = new CopyOnWriteArrayList<>();
    private CompletableFuture<Either3<org.eclipse.lsp4j.Range, PrepareRenameResult, PrepareRenameDefaultBehavior>>
            prepareRenameAnswer = new CompletableFuture<>();
    private CompletableFuture<WorkspaceEdit> renameAnswer = new CompletableFuture<>();
    private final List<PrepareRenameParams> prepared = new CopyOnWriteArrayList<>();
    private final List<RenameParams> renamed = new CopyOnWriteArrayList<>();
    private CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbolAnswer =
            new CompletableFuture<>();
    private CompletableFuture<List<Either<Command, CodeAction>>> codeActionAnswer = new CompletableFuture<>();
    private CompletableFuture<CodeAction> resolveAnswer = new CompletableFuture<>();
    private final List<CodeActionParams> codeActionRequests = new CopyOnWriteArrayList<>();
    private final List<CodeAction> resolveRequests = new CopyOnWriteArrayList<>();
    private CompletableFuture<JdtlsServer.RefactorEdit> refactorEditAnswer = new CompletableFuture<>();
    private final List<JdtlsServer.RefactorEditParams> refactorEditRequests = new CopyOnWriteArrayList<>();
    private CompletableFuture<JdtlsServer.RefactorEdit> moveAnswer = new CompletableFuture<>();
    private final List<JdtlsServer.MoveParams> moveRequests = new CopyOnWriteArrayList<>();
    private final List<List<String>> registeredWatchers = new ArrayList<>();
    private final List<DidChangeWatchedFilesParams> watched = new CopyOnWriteArrayList<>();

    private volatile Process process;
    private volatile Path data;
    private volatile Future<Void> serving;
    private final List<AutoCloseable> channels = new ArrayList<>();
    private volatile StatusClient client;
    private volatile @Nullable JsonElement chooseImportsSelections;
    private final List<Object> commandAnswers = new CopyOnWriteArrayList<>();

    /** The child that stands in for the jdtls process; the default one sleeps until it is killed. */
    FakeJdtls child(String... command) {
        this.child = command;
        return this;
    }

    /**
     * The command of the child process starts this many processes in turn ({@code sh -c "sleep 300 & wait"} starts
     * one): {@code start} returns only once they run, so that no test reads the tree before it exists.
     */
    FakeJdtls whoseLauncherStarts(int children) {
        this.children = children;
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

    /**
     * The answer to {@code textDocument/prepareRename}, whatever the position: a range, null (nothing can be renamed)
     * or a failed future (a refusal). Unscripted, the request never gets an answer.
     */
    FakeJdtls prepareRenameAnswers(
            CompletableFuture<Either3<org.eclipse.lsp4j.Range, PrepareRenameResult, PrepareRenameDefaultBehavior>>
                    answer) {
        this.prepareRenameAnswer = answer;
        return this;
    }

    /**
     * The answer to {@code textDocument/rename}, whatever the request: an edit, null or a failed future (a refusal).
     * Unscripted, the request never gets an answer.
     */
    FakeJdtls renameAnswers(CompletableFuture<WorkspaceEdit> answer) {
        this.renameAnswer = answer;
        return this;
    }

    /**
     * The answer to {@code textDocument/documentSymbol}, whatever the request: the symbols, null or a failed future.
     * Unscripted, the request never gets an answer.
     */
    FakeJdtls documentSymbolAnswers(CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> answer) {
        this.documentSymbolAnswer = answer;
        return this;
    }

    /**
     * The answer to {@code textDocument/codeAction}, whatever the request: the actions, null or a failed future.
     * Unscripted, the request never gets an answer.
     */
    FakeJdtls codeActionAnswers(CompletableFuture<List<Either<Command, CodeAction>>> answer) {
        this.codeActionAnswer = answer;
        return this;
    }

    /**
     * The answer to {@code codeAction/resolve}, whatever the action: the action with its edit, null or a failed
     * future. Unscripted, the request never gets an answer.
     */
    FakeJdtls resolveAnswers(CompletableFuture<CodeAction> answer) {
        this.resolveAnswer = answer;
        return this;
    }

    /**
     * The answer to {@code java/getRefactorEdit}, whatever the request: the edit and the rename to run after it, null or
     * a failed future (a refusal). Unscripted, the request never gets an answer.
     */
    FakeJdtls refactorEditAnswers(CompletableFuture<JdtlsServer.RefactorEdit> answer) {
        this.refactorEditAnswer = answer;
        return this;
    }

    /**
     * The answer to {@code java/move}, whatever the request: the edit, null or a failed future (a refusal). Unscripted,
     * the request never gets an answer.
     */
    FakeJdtls moveAnswers(CompletableFuture<JdtlsServer.RefactorEdit> answer) {
        this.moveAnswer = answer;
        return this;
    }

    /** The {@code java/move} requests received, in order. */
    List<JdtlsServer.MoveParams> moveRequests() {
        return moveRequests;
    }

    /** The {@code java/getRefactorEdit} requests received, in order. */
    List<JdtlsServer.RefactorEditParams> refactorEditRequests() {
        return refactorEditRequests;
    }

    /**
     * Makes the server ask the client to choose between candidates, as jdtls does for a type that several packages
     * declare ({@code java.action.organizeImports.chooseImports}), while it answers the next {@code codeAction}.
     *
     * @param selections the JSON the command carries as its second argument: the candidates of each use
     */
    FakeJdtls asksToChooseImports(String selections) {
        this.chooseImportsSelections = JsonParser.parseString(selections);
        return this;
    }

    /** What the client answered to the commands the server sent it, in order. */
    List<Object> commandAnswers() {
        return commandAnswers;
    }

    /** The {@code codeAction} requests received, in order. */
    List<CodeActionParams> codeActionRequests() {
        return codeActionRequests;
    }

    /** The {@code codeAction/resolve} requests received, in order. */
    List<CodeAction> resolveRequests() {
        return resolveRequests;
    }

    /** The {@code prepareRename} requests received, in order. */
    List<PrepareRenameParams> prepared() {
        return prepared;
    }

    /** The {@code rename} requests received, in order. */
    List<RenameParams> renamed() {
        return renamed;
    }

    /**
     * Registers these globs to be watched ({@code client/registerCapability} for {@code workspace/didChangeWatchedFiles},
     * as jdtls does) once it is ready, in a registration of its own for each call: the start does not end before the
     * client has answered it.
     */
    FakeJdtls registersWatchersFor(String... globs) {
        registeredWatchers.add(List.of(globs));
        return this;
    }

    /** The deletions of {@code workspace/didChangeWatchedFiles} are not acknowledged by a publication of diagnostics. */
    FakeJdtls neverAcknowledgesChanges() {
        this.acknowledgesChanges = false;
        return this;
    }

    /** The acknowledgments are given again from now on (after {@link #neverAcknowledgesChanges}). */
    FakeJdtls acknowledgesChangesFromNow() {
        this.acknowledgesChanges = true;
        return this;
    }

    /**
     * The acknowledgments of the changes come this long after the notification, on a thread of their own: jdtls handles
     * the events on a thread of its own, so a request sent in the meantime is not behind them.
     */
    FakeJdtls delaysAcknowledgments(Duration delay) {
        this.acknowledgmentDelay = delay;
        return this;
    }

    /**
     * What the fake received and sent about files, in the order it happened: {@code didChangeWatchedFiles},
     * {@code acknowledged <file>} (the publication that answers a deletion), {@code didOpen <file>},
     * {@code prepareRename <file>}, {@code rename <file>}, {@code getRefactorEdit <file>} and {@code move <file>}, a file by its name.
     */
    List<String> received() {
        return received;
    }

    private static String named(String uri) {
        return Path.of(java.net.URI.create(uri)).getFileName().toString();
    }

    /** The {@code workspace/didChangeWatchedFiles} notifications received, in order. */
    List<DidChangeWatchedFilesParams> watched() {
        return watched;
    }

    /** What {@code java.project.getAll} answers, in turn; the last answer is repeated. */
    FakeJdtls listsProjects(String... projectUris) {
        projectAnswers.add(List.of(projectUris));
        return this;
    }

    /** Forgets the answers scripted so far: from now on, {@code java.project.getAll} answers these projects. */
    FakeJdtls listsOnlyProjects(String... projectUris) {
        projectAnswers.clear();
        return listsProjects(projectUris);
    }

    @Override
    public JdtlsConnection start(Path dataDirectory) throws IOException {
        this.data = dataDirectory;
        Process started = new ProcessBuilder(child).start();
        awaitChildren(started);
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

    private void awaitChildren(Process started) throws IOException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        List<ProcessHandle> running = executed(started);
        while (running.size() < children) {
            if (System.nanoTime() > deadline) {
                started.destroyForcibly();
                throw new IOException("the launcher did not start " + children + " process(es) in time");
            }
            LockSupport.parkNanos(Duration.ofMillis(5).toNanos());
            running = executed(started);
        }
        launched = running;
    }

    /** The descendants that run their own program: a process just forked still has the arguments of its parent. */
    private static List<ProcessHandle> executed(Process launcher) {
        Optional<String[]> own = launcher.info().arguments();
        return launcher.descendants()
                .filter(child -> child.info().arguments().isPresent()
                        && !Arrays.equals(child.info().arguments().get(), own.orElse(null)))
                .toList();
    }

    /** The processes the launcher had started when {@code start} returned (see {@link #whoseLauncherStarts}). */
    List<ProcessHandle> launched() {
        return launched;
    }

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
        launched.forEach(ProcessHandle::destroyForcibly);
        if (process != null) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
        closePipes();
        if (serving != null) {
            serving.cancel(true);
        }
    }

    private final class Server implements JdtlsServer {

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
            if (registeredWatchers.isEmpty()) {
                sendStatus();
            } else {
                // The answer to a registration arrives on the thread that is handling this notification.
                Thread.ofVirtual().start(() -> {
                    registerWatchers();
                    sendStatus();
                });
            }
        }

        private void sendStatus() {
            if (!status.isEmpty()) {
                client.status(new JdtlsClient.Status(status, status));
            }
        }

        private void registerWatchers() {
            for (int registration = 0; registration < registeredWatchers.size(); registration++) {
                var watchers = new JsonArray();
                for (String glob : registeredWatchers.get(registration)) {
                    var watcher = new JsonObject();
                    watcher.addProperty("globPattern", glob);
                    watchers.add(watcher);
                }
                var options = new JsonObject();
                options.add("watchers", watchers);
                client.registerCapability(new RegistrationParams(
                                List.of(new Registration("watchers-" + registration, METHOD, options))))
                        .join();
            }
        }

        @Override
        public CompletableFuture<RefactorEdit> getRefactorEdit(RefactorEditParams params) {
            received.add("getRefactorEdit "
                    + named(params.context().getTextDocument().getUri()));
            refactorEditRequests.add(params);
            return refactorEditAnswer;
        }

        @Override
        public CompletableFuture<RefactorEdit> move(MoveParams params) {
            received.add("move " + named(params.params().getTextDocument().getUri()));
            moveRequests.add(params);
            return moveAnswer;
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
                    received.add("didOpen " + named(uri));
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
                public CompletableFuture<
                                Either3<org.eclipse.lsp4j.Range, PrepareRenameResult, PrepareRenameDefaultBehavior>>
                        prepareRename(PrepareRenameParams params) {
                    received.add(
                            "prepareRename " + named(params.getTextDocument().getUri()));
                    prepared.add(params);
                    return prepareRenameAnswer;
                }

                @Override
                public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
                    received.add("rename " + named(params.getTextDocument().getUri()));
                    renamed.add(params);
                    return renameAnswer;
                }

                @Override
                public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
                        DocumentSymbolParams params) {
                    received.add(
                            "documentSymbol " + named(params.getTextDocument().getUri()));
                    return documentSymbolAnswer;
                }

                @Override
                public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
                    received.add("codeAction " + named(params.getTextDocument().getUri()));
                    codeActionRequests.add(params);
                    var selections = chooseImportsSelections;
                    if (selections == null) {
                        return codeActionAnswer;
                    }
                    // Asked and answered on other threads than this one: the answer is read by the thread that is here.
                    return client.executeClientCommand(new ExecuteCommandParams(
                                    "java.action.organizeImports.chooseImports",
                                    List.of(params.getTextDocument().getUri(), selections, false)))
                            .thenCompose(answer -> {
                                commandAnswers.add(answer);
                                return codeActionAnswer;
                            });
                }

                @Override
                public CompletableFuture<CodeAction> resolveCodeAction(CodeAction action) {
                    received.add("resolveCodeAction");
                    resolveRequests.add(action);
                    return resolveAnswer;
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
                public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
                    received.add("didChangeWatchedFiles");
                    watched.add(params);
                    // As jdtls does for a deletion, handled after the events before it: that is its acknowledgment.
                    List<String> deleted = params.getChanges().stream()
                            .filter(change -> acknowledgesChanges)
                            .filter(change -> change.getType() == FileChangeType.Deleted)
                            .map(change -> change.getUri())
                            .toList();
                    if (acknowledgmentDelay.isZero()) {
                        acknowledge(deleted);
                    } else {
                        Thread.ofVirtual().start(() -> {
                            LockSupport.parkNanos(acknowledgmentDelay.toNanos());
                            acknowledge(deleted);
                        });
                    }
                }

                private void acknowledge(List<String> uris) {
                    for (String uri : uris) {
                        received.add("acknowledged " + named(uri));
                        client.publishDiagnostics(new PublishDiagnosticsParams(uri, List.of()));
                    }
                }
            };
        }
    }
}
