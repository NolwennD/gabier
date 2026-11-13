package eu.gabier.daemon;

import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.CleanUp;
import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.editing.domain.JavaLanguageServer;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.OfferedAction;
import eu.gabier.editing.domain.OfferedActions;
import eu.gabier.editing.domain.PackageName;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.Range;
import eu.gabier.editing.domain.ReferenceLocations;
import eu.gabier.editing.domain.TypeKind;
import eu.gabier.protocol.ImportSettings;
import eu.gabier.protocol.JdtlsProgress;
import eu.gabier.protocol.JdtlsState;
import eu.gabier.testing.domain.BuiltProject;
import eu.gabier.testing.domain.ChangedBuild;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;

/**
 * The port {@link JavaLanguageServer} on a real jdtls. It starts jdtls in the background ({@link JdtlsSession}), so
 * that it can answer at once that the project is still being imported, and from then on it answers in the types of the
 * domain: no LSP4J type leaves this package.
 *
 * <p>The diagnostics of a file are what jdtls publishes after the file is opened ({@code publishDiagnostics}, the
 * minimum every language server offers): the adapter opens the file as it is on disk, waits for the publication that
 * follows, then for jdtls to be quiet, because it publishes in stages (a quick reconcile, then after the build), and
 * answers the latest publication. It closes the file afterwards, so that the next request reads it from disk again. It
 * asks one thing at a time.
 *
 * <p>jdtls does not watch the disk: a file edited, created, moved or deleted between two requests (by the agent, or
 * from the change set of a rename) is a file jdtls has not learnt of. It registers the files it wants watched, and
 * wants {@code workspace/didChangeWatchedFiles} for them (see {@link WatchedFiles}): before each request the adapter
 * tells it what changed, and waits until it has taken the changes in.
 *
 * <p>A rename is {@code textDocument/prepareRename} then {@code textDocument/rename}, and the {@code WorkspaceEdit}
 * that jdtls answers is turned into the change set of the domain by {@link WorkspaceEdits}. The organization of the
 * imports is the code action {@code source.organizeImports}, not the command {@code java.edit.organizeImports}, which
 * pushes its edit to the client with {@code workspace/applyEdit}. The refusals of the port are mapped from what jdtls
 * really does (see {@code Scenarios} in the fixtures of the core).
 *
 * <p>A file is in the project when it is under a folder jdtls imported. jdtls itself does not refuse the others: it
 * opens them as non-project files and warns that only syntax errors are reported, which is not an answer about the
 * project, so the adapter refuses before it asks.
 */
final class JdtlsLanguageServer implements StartedLanguageServer {

    /**
     * How long jdtls must have published nothing before its diagnostics are taken as final. In the real runs of the
     * replay the publications of the opened files came within this pause of each other.
     */
    static final Duration QUIET = Duration.ofSeconds(5);

    /**
     * The start of the name of the file of the last event told to jdtls (a batch number follows): it does not exist,
     * see {@link #takeInChangesOnDisk}.
     */
    private static final String BARRIER = ".gabier-changes-taken-";

    /** What jdtls publishes, kept per file: read by every request that wants the problems of files. */
    private final Diagnostics diagnostics;

    /** The signal that jdtls has finished building: read by every request that wants the problems as they are now. */
    private final Builds builds;

    private final JdtlsDiagnostics problems;
    private final CompletableFuture<JdtlsSession> starting;
    private final StartProgress progress;
    private final Thread starter;

    /** The changes on disk jdtls has not acknowledged yet: they are told again by the next request. */
    private final List<FileEvent> untaken = new ArrayList<>();

    private int batches;

    /**
     * The Java files that changed on disk, as every request told jdtls of them, and whose tests no answer has covered
     * yet, each with the number of its last change (see {@link #builtWithChanges}).
     */
    private final Map<Path, Long> changedSources = new TreeMap<>();

    private long changes;

    /** The import settings jdtls has: its own defaults at its start, then what the adapter last gave it. */
    private ImportSettings importsOfJdtls = ImportSettings.DEFAULTS;

    private final JdtlsCreations creations = new JdtlsCreations(this);

    private final JdtlsReferences references = new JdtlsReferences(this);

    private final JdtlsCodeActions codeActions = new JdtlsCodeActions(this);

    private final JdtlsInlining inlining = new JdtlsInlining(this);

    private final JdtlsMoves moves = new JdtlsMoves(this);

    private final JdtlsRefactorings refactorings = new JdtlsRefactorings(this);

    private final JdtlsImports imports = new JdtlsImports(this);

    private JdtlsLanguageServer(
            Diagnostics diagnostics,
            Builds builds,
            Duration quiet,
            CompletableFuture<JdtlsSession> starting,
            StartProgress progress,
            Thread starter) {
        this.diagnostics = diagnostics;
        this.builds = builds;
        this.starting = starting;
        this.progress = progress;
        this.starter = starter;
        this.problems = new JdtlsDiagnostics(this, diagnostics, builds, quiet);
    }

    /**
     * Starts jdtls in the background and returns at once: until the start is over, the requests are refused as not
     * ready. A start that fails is reported to the next request and to {@link #awaitStarted}.
     *
     * @param quiet how long jdtls must have published nothing before its diagnostics are taken as final
     * @param log receives a line for what happens (see {@link JdtlsSession#start})
     */
    static JdtlsLanguageServer start(JdtlsStarter jdtls, JdtlsSettings settings, Duration quiet, Consumer<String> log) {
        return start(jdtls, settings, quiet, Builds.SETTLE, log);
    }

    /**
     * As {@link #start(JdtlsStarter, JdtlsSettings, Duration, Consumer)}.
     *
     * @param settle how long nothing may be published after the build of the project before its diagnostics are taken as
     *     final ({@link Builds})
     */
    static JdtlsLanguageServer start(
            JdtlsStarter jdtls, JdtlsSettings settings, Duration quiet, Duration settle, Consumer<String> log) {
        var diagnostics = new Diagnostics();
        var starting = new CompletableFuture<JdtlsSession>();
        var progress = new StartProgress();
        Thread starter = Thread.ofPlatform().name("gabier-jdtls-start").daemon().unstarted(() -> {
            try {
                starting.complete(JdtlsSession.start(jdtls, settings, log, progress, diagnostics::publish));
            } catch (InterruptedException stopped) {
                // Only close() interrupts the start, and the requests that wait for it are told why it ended.
                starting.completeExceptionally(new JdtlsException("the daemon is stopping", stopped));
            } catch (Exception failure) {
                starting.completeExceptionally(failure);
            }
        });
        starter.start();
        return new JdtlsLanguageServer(
                diagnostics, new Builds(diagnostics, settle), quiet, starting, progress, starter);
    }

    @Override
    public JdtlsState state() {
        return switch (starting.state()) {
            case RUNNING -> JdtlsState.STARTING;
            // A jdtls that died after it was ready is failed: the next request must start it again.
            case SUCCESS -> starting.resultNow().isAlive() ? JdtlsState.READY : JdtlsState.FAILED;
            default -> JdtlsState.FAILED;
        };
    }

    @Override
    public Optional<JdtlsProgress> progress() {
        return state() == JdtlsState.STARTING ? Optional.of(progress.current()) : Optional.empty();
    }

    @Override
    public void awaitReady() throws InterruptedException {
        awaitStarted();
    }

    /**
     * Waits until jdtls is ready.
     *
     * @return the folders of the projects jdtls imported
     * @throws JdtlsException if jdtls could not be started (the message says why)
     */
    List<Path> awaitStarted() throws InterruptedException {
        try {
            starting.get();
        } catch (ExecutionException failure) {
            // Said by ready(), with the reason.
        }
        return ready().projects();
    }

    /**
     * The diagnostics of one file: the call of one file of {@link #diagnostics(List)}.
     *
     * @throws LanguageServerException if jdtls is not ready or the file is not in its project
     * @throws JdtlsException if jdtls dies or does not answer in time
     */
    List<Diagnostic> diagnostics(Path file) {
        return diagnostics(List.of(file)).getFirst();
    }

    @Override
    public synchronized List<List<Diagnostic>> diagnostics(List<Path> files) {
        return problems.diagnostics(files);
    }

    @Override
    public synchronized List<DiagnosedFile> projectDiagnostics() {
        return problems.projectDiagnostics();
    }

    @Override
    public synchronized BuiltProject built() {
        return problems.built();
    }

    /**
     * What {@link #built()} gives, with the Java files that changed on disk since the changes were last answered for.
     * They are what jdtls was told of, by this request or by any other ({@link #takeInChangesOnDisk} is the one place
     * where the changes of the disk are read): a request that is not about the tests takes the changes in too, and does
     * not use them up. Checked on the real jdtls for an edit by hand, a text edit and a rename: all of them are told,
     * as {@code Created}, {@code Changed} and {@code Deleted} events (the dependents of a deleted class are told of
     * only when their own file changed). The class files jdtls rewrites are no source of the changes: a jdtls that
     * starts rewrites every class, with no edit.
     *
     * <p>The changes are those of the running jdtls, so a restart forgets them. A file that was deleted is not among
     * them, and nor is a file that is not Java.
     *
     * @throws LanguageServerException if jdtls is not ready
     * @throws JdtlsException if jdtls dies, does not answer in time, fails the build or answers a shape that is not
     *     the documented one
     */
    @Override
    public synchronized ChangedBuild builtWithChanges() {
        BuiltProject built = built();
        Map<Path, Long> told = new TreeMap<>(changedSources);

        return new ChangedBuild(built, List.copyOf(told.keySet()), () -> answered(told));
    }

    /** Forgets the changes an answer covered, and not a file that changed again after it. */
    private synchronized void answered(Map<Path, Long> told) {
        told.forEach(changedSources::remove);
    }

    private void remember(FileEvent event) {
        Path file = Path.of(URI.create(event.getUri()));
        if (!file.getFileName().toString().endsWith(".java")) {
            return;
        }
        if (event.getType() == FileChangeType.Deleted) {
            changedSources.remove(file);
        } else {
            changedSources.put(file, ++changes);
        }
    }

    /** What {@link #diagnostics(List)} asks of jdtls, as jdtls publishes it for each file, in the order given. */
    synchronized List<List<org.eclipse.lsp4j.Diagnostic>> published(List<Path> files) {
        return problems.published(files);
    }

    @Override
    public synchronized ChangeSet rename(Path file, Position position, String newName) {
        return refactorings.rename(file, position, newName);
    }

    @Override
    public synchronized ChangeSet extractVariable(Path file, Range selection, String name) {
        return refactorings.extractVariable(file, selection, name);
    }

    @Override
    public synchronized ChangeSet extractConstant(Path file, Range selection, String name) {
        return refactorings.extractConstant(file, selection, name);
    }

    @Override
    public synchronized ChangeSet extractField(Path file, Range selection, String name) {
        return refactorings.extractField(file, selection, name);
    }

    @Override
    public synchronized ChangeSet extractMethod(Path file, Range selection, String name) {
        return refactorings.extractMethod(file, selection, name);
    }

    @Override
    public synchronized ChangeSet moveTypeToFile(Path file, Position position) {
        return moves.moveTypeToFile(file, position);
    }

    @Override
    public synchronized ChangeSet moveToPackage(List<Path> files, PackageName target) {
        return moves.moveToPackage(files, target);
    }

    @Override
    public synchronized ChangeSet inline(Path file, Position position) {
        return inlining.inline(file, position);
    }

    @Override
    public synchronized ReferenceLocations references(Path file, Position position, boolean includeDeclaration) {
        return references.references(file, position, includeDeclaration);
    }

    @Override
    public synchronized OfferedActions codeActions(Path file, Range range) {
        return codeActions.codeActions(file, range);
    }

    @Override
    public synchronized ChangeSet codeActionEdit(Path file, Range range, OfferedAction action) {
        return codeActions.codeActionEdit(file, range, action);
    }

    @Override
    public synchronized ChangeSet createType(Path file, TypeKind kind) {
        return creations.createType(file, kind);
    }

    @Override
    public synchronized ChangeSet createPackageInfo(Path file) {
        return creations.createPackageInfo(file);
    }

    @Override
    public synchronized Optional<ChangeSet> organizeImports(Path file) {
        return imports.organizeImports(file);
    }

    @Override
    public synchronized Optional<ChangeSet> cleanUp(Path file, List<CleanUp> cleanUps) {
        return imports.cleanUp(file, cleanUps);
    }

    /** Adds an event to the ones the next {@link #takeInChangesOnDisk} tells jdtls of. */
    void queue(FileEvent event) {
        untaken.add(event);
    }

    /**
     * Tells jdtls of the files that changed on disk since it was last told, and waits until it has taken them in. jdtls
     * queues the events and handles them on a thread of its own, one after the other, so a request sent right after
     * would be answered for the files as they were. The wait ends on the last event, which is a deletion of a file that
     * does not exist: for a deletion jdtls publishes empty diagnostics of the file, as the events are handled, and
     * that publication is the acknowledgment (a request of jdtls has none).
     *
     * <p>The events stay in {@link #untaken} until that wait has ended: when it times out (jdtls is busy), the retry
     * tells them again and waits again, and does not ask jdtls while they may still be queued. Each batch ends with a
     * file of its own, so that a late acknowledgment of the earlier batch is not taken for the one of the retry.
     */
    void takeInChangesOnDisk(JdtlsSession session, JdtlsWatch watch) throws InterruptedException {
        List<FileEvent> onDisk = session.changesOnDisk();
        onDisk.forEach(this::remember);
        untaken.addAll(onDisk);
        if (untaken.isEmpty()) {
            return;
        }
        Path barrier = session.projectRoot().resolve(BARRIER + ++batches);
        List<FileEvent> events = new ArrayList<>(untaken);
        events.add(new FileEvent(barrier.toUri().toString(), FileChangeType.Deleted));
        var taken = diagnostics.open(barrier, () -> session.tell(events));
        watch.await("jdtls to take in the changes on disk of " + untaken.size() + " files", () -> taken);
        untaken.clear();
    }

    /**
     * What a request that may write imports starts with: jdtls is given the import settings of the project as its files
     * say them now ({@link #followTheImportPreferences}), and told of the changes on disk. A request that writes none
     * ({@code diagnostics}, {@code references}) only takes in the changes.
     */
    void prepare(JdtlsSession session, JdtlsWatch watch) throws InterruptedException {
        followTheImportPreferences(session);
        takeInChangesOnDisk(session, watch);
    }

    /**
     * Reads the files of the project that say how it writes its imports ({@link ImportPreferences}) and gives jdtls the
     * three settings when they are not the ones it has: it reads no such file itself, and keeps what it was last given
     * (see {@link JdtlsSettings#imports}). The files are read at each call, so a change is followed by the next request
     * with no restart. A jdtls just started has the defaults of its own, which is what {@link ImportSettings#DEFAULTS}
     * says, so a project whose files leave them is told nothing.
     */
    private void followTheImportPreferences(JdtlsSession session) {
        ImportSettings wanted = ImportPreferences.read(session.projectRoot()).settings();
        if (!ImportPreferences.sameValues(wanted, importsOfJdtls)) {
            session.configure(JdtlsSettings.imports(wanted));
            importsOfJdtls = wanted;
        }
    }

    /** Asks jdtls to shut down, or ends its start when it is not over, and deletes what the session made. */
    @Override
    public void close() throws IOException, InterruptedException {
        if (starting.state() == Future.State.RUNNING) {
            // The session ends itself when its start is interrupted.
            starter.interrupt();
        }
        starter.join();
        if (starting.state() == Future.State.SUCCESS) {
            starting.resultNow().close();
        }
    }

    /** A notification: LSP4J does not throw when jdtls is gone, the next wait for an answer says so. */
    static void close(JdtlsSession session, Iterable<Path> files) {
        for (Path file : files) {
            session.server()
                    .getTextDocumentService()
                    .didClose(new DidCloseTextDocumentParams(
                            new TextDocumentIdentifier(file.toUri().toString())));
        }
    }

    /**
     * Follows the disk once jdtls has registered the files it wants watched, which it does late in its initialization:
     * what is followed is what it asked for, and nothing else.
     *
     * @throws LanguageServerException if jdtls is not ready
     * @throws JdtlsException if jdtls dies or does not register in time
     */
    @Override
    public DiskChanges watchDisk() {
        JdtlsSession session = ready();
        try {
            builds.awaitInitialized(session, session.newWatch());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException(
                    "Interrupted while waiting for jdtls to register the files it watches", interrupted);
        }

        return session.watchDisk();
    }

    JdtlsSession ready() {
        if (starting.state() == Future.State.RUNNING) {
            throw LanguageServerException.notReady();
        }
        if (starting.state() == Future.State.FAILED) {
            throw failureOfTheStart(starting.exceptionNow());
        }
        JdtlsSession session = starting.resultNow();
        if (!session.isAlive()) {
            throw death(session);
        }
        return session;
    }

    @Override
    public Optional<String> failure() {
        return switch (starting.state()) {
            case FAILED ->
                Optional.ofNullable(failureOfTheStart(starting.exceptionNow()).getMessage());
            case SUCCESS ->
                starting.resultNow().isAlive()
                        ? Optional.empty()
                        : Optional.ofNullable(death(starting.resultNow()).getMessage());
            default -> Optional.empty();
        };
    }

    /** A refusal that has a problem object of its own stays one; any other failure is a failure to start. */
    private static RuntimeException failureOfTheStart(Throwable failure) {
        if (failure instanceof NoBuildToImportException noBuild) {
            return noBuild;
        }
        return new JdtlsException("jdtls did not start: " + failure.getMessage(), failure);
    }

    private static JdtlsException death(JdtlsSession session) {
        return new JdtlsException(
                "jdtls is not running any more: it exited with code " + session.exitValue() + " after it was ready");
    }

    Map<Path, CompletableFuture<List<org.eclipse.lsp4j.Diagnostic>>> open(
            JdtlsSession session, Map<Path, String> texts) {
        Map<Path, CompletableFuture<List<org.eclipse.lsp4j.Diagnostic>>> publications = new LinkedHashMap<>();
        texts.forEach((file, text) -> {
            var item = new TextDocumentItem(file.toUri().toString(), "java", 1, text);
            // The wait is registered right before this file is opened: a publication from before says nothing about it.
            publications.put(
                    file,
                    diagnostics.open(
                            file,
                            () -> session.server()
                                    .getTextDocumentService()
                                    .didOpen(new DidOpenTextDocumentParams(item))));
        });
        return publications;
    }
}
