package eu.gabier.daemon;

import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.CleanUp;
import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.editing.domain.Diagnostic.Severity;
import eu.gabier.editing.domain.FileChange;
import eu.gabier.editing.domain.JavaLanguageServer;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.OfferedAction;
import eu.gabier.editing.domain.OfferedActions;
import eu.gabier.editing.domain.PackageName;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.Range;
import eu.gabier.editing.domain.ReferenceLocations;
import eu.gabier.editing.domain.SourceText;
import eu.gabier.editing.domain.TextEdit;
import eu.gabier.editing.domain.TypeKind;
import eu.gabier.protocol.ImportSettings;
import eu.gabier.protocol.JdtlsProgress;
import eu.gabier.protocol.JdtlsState;
import eu.gabier.testing.domain.BuiltProject;
import eu.gabier.testing.domain.ChangedBuild;
import eu.gabier.testing.domain.ClassFolder;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jspecify.annotations.Nullable;

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

    private final Duration quiet;
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

    private JdtlsLanguageServer(
            Diagnostics diagnostics,
            Builds builds,
            Duration quiet,
            CompletableFuture<JdtlsSession> starting,
            StartProgress progress,
            Thread starter) {
        this.diagnostics = diagnostics;
        this.builds = builds;
        this.quiet = quiet;
        this.starting = starting;
        this.progress = progress;
        this.starter = starter;
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

    /**
     * The diagnostics of several files with one wait for jdtls to be quiet: the cost of a request is that wait, so a
     * caller that has many files asks them together. Each file is opened once, whatever the number of times it is
     * given, and a file given twice is answered twice.
     *
     * @return the diagnostics of each file, in the order given
     * @throws LanguageServerException if jdtls is not ready or one of the files is not in its project (nothing is
     *     opened then)
     * @throws JdtlsException if jdtls dies or does not answer in time
     */
    @Override
    public synchronized List<List<Diagnostic>> diagnostics(List<Path> files) {
        return published(files).stream().map(JdtlsLanguageServer::domain).toList();
    }

    /**
     * The problems jdtls has published for the Java files of the project, once it has built it: no file is opened and
     * none is read, so the time does not grow with the number of files. jdtls publishes the problems of the whole
     * workspace as its builds go, the files that are not open included (checked on the real jdtls, with Gradle, Maven
     * and no build tool), and the daemon keeps the last publication of each file ({@link Diagnostics#published}). The
     * answer is read once the changes on disk are told to jdtls and its build is finished ({@link Builds}), so a file
     * edited since the last request is answered as it is now, and a build that is running is waited for, not taken for
     * a clean project.
     *
     * <p>The files are those jdtls published a problem for that are {@code .java} files under a folder of a project it
     * imported (a jar, a {@code pom.xml} or a file of another directory is left out), in the order of the paths: a clean
     * file is not in the answer, since jdtls says nothing of a file that never had a problem.
     *
     * @throws LanguageServerException if jdtls is not ready
     * @throws JdtlsException if jdtls dies, does not answer in time or fails the build
     */
    @Override
    public synchronized List<DiagnosedFile> projectDiagnostics() {
        JdtlsSession session = ready();
        try {
            afterTheBuild(session);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException("Interrupted while waiting for the diagnostics of the project", interrupted);
        }

        return projectFilesWithProblems(session)
                .map(published -> new DiagnosedFile(published.getKey(), domain(published.getValue())))
                .toList();
    }

    /**
     * Tells jdtls of the changes on disk and waits until its build is finished: what is read after it is the project as
     * it is now.
     *
     * @return the watch of the wait, for the requests that follow
     */
    private JdtlsWatch afterTheBuild(JdtlsSession session) throws InterruptedException {
        JdtlsWatch watch = session.newWatch();
        builds.awaitInitialized(session, watch);
        takeInChangesOnDisk(session, watch);
        builds.awaitFinished(session, watch);

        return watch;
    }

    /** What jdtls last published for the {@code .java} files of the projects it imported that have a problem. */
    private Stream<Map.Entry<Path, List<org.eclipse.lsp4j.Diagnostic>>> projectFilesWithProblems(JdtlsSession session) {
        return diagnostics.published().entrySet().stream()
                .filter(published -> !published.getValue().isEmpty())
                .filter(published -> published.getKey().getFileName().toString().endsWith(".java"))
                .filter(published -> session.projects().stream().anyMatch(published.getKey()::startsWith));
    }

    /**
     * What jdtls built: for each project it imported, the source folders and the folders it compiled them into
     * ({@code java.project.getSettings}), the jars of the class path of the tests ({@code java.project.getClasspaths},
     * scope {@code test}), and the Java files it published an error for. It is read once the changes on disk are told
     * to jdtls and its build is finished, as {@link #projectDiagnostics()} is, so the classes are those of the files as
     * they are now.
     *
     * <p>jdtls writes a class even for a file that does not compile (a stub whose faulty methods throw), so the files
     * with an error are told apart from the errors it published, not from the classes. No {@code java.*} setting is
     * needed: both commands answer for Eclipse, Maven and Gradle projects as they are.
     *
     * @throws LanguageServerException if jdtls is not ready
     * @throws JdtlsException if jdtls dies, does not answer in time, fails the build or answers a shape that is not
     *     the documented one
     */
    @Override
    public synchronized BuiltProject built() {
        JdtlsSession session = ready();
        try {
            JdtlsWatch watch = afterTheBuild(session);
            List<ClassFolder> folders = new ArrayList<>();
            Set<Path> classpath = new LinkedHashSet<>();
            for (Path project : session.projects()) {
                String uri = project.toUri().toString();
                folders.addAll(JdtlsBuildAnswers.classFolders(ask(
                        session,
                        watch,
                        "the settings of " + project + " (java.project.getSettings)",
                        "java.project.getSettings",
                        List.of(uri, JdtlsBuildAnswers.SETTINGS))));
                classpath.addAll(JdtlsBuildAnswers.classpath(ask(
                        session,
                        watch,
                        "the class path of " + project + " (java.project.getClasspaths)",
                        "java.project.getClasspaths",
                        List.of(uri, Map.of("scope", "test")))));
            }
            Set<Path> classFolders = folders.stream().map(ClassFolder::classes).collect(Collectors.toSet());
            List<Path> libraries = classpath.stream()
                    .filter(entry -> !classFolders.contains(entry))
                    .toList();
            List<Path> withErrors = projectFilesWithProblems(session)
                    .filter(published -> published.getValue().stream()
                            .anyMatch(found -> found.getSeverity() == DiagnosticSeverity.Error))
                    .map(Map.Entry::getKey)
                    .toList();

            return new BuiltProject(folders.stream().distinct().toList(), libraries, withErrors);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException("Interrupted while waiting for the build of the project", interrupted);
        }
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

    private static @Nullable Object ask(
            JdtlsSession session, JdtlsWatch watch, String what, String command, List<Object> arguments)
            throws InterruptedException {
        var params = new ExecuteCommandParams(command, arguments);

        return watch.await(what, () -> session.server().getWorkspaceService().executeCommand(params));
    }

    /** What {@link #diagnostics(List)} asks of jdtls, as jdtls publishes it for each file, in the order given. */
    synchronized List<List<org.eclipse.lsp4j.Diagnostic>> published(List<Path> files) {
        JdtlsSession session = ready();
        Map<Path, String> texts = new LinkedHashMap<>();
        for (Path file : files) {
            texts.put(
                    JdtlsProject.normalized(file), JdtlsProject.textInProject(session, JdtlsProject.normalized(file)));
        }
        JdtlsWatch watch = session.newWatch();
        try {
            takeInChangesOnDisk(session, watch);
            var publications = open(session, texts);
            for (var publication : publications.entrySet()) {
                watch.await("the diagnostics of " + publication.getKey(), publication::getValue);
            }
            QuietWait.await(watch, "the diagnostics of " + texts.keySet(), quiet, diagnostics::quietFor);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException("Interrupted while waiting for the diagnostics of " + texts.keySet(), interrupted);
        } finally {
            close(session, texts.keySet());
        }
        return files.stream()
                .map(file -> diagnostics.latest(JdtlsProject.normalized(file)))
                .toList();
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

    /**
     * Asks jdtls for the {@code source.organizeImports} code action of the file, resolves it when its edit is not given
     * (jdtls computes it at that point, the client declares {@code codeAction.resolveSupport} for {@code edit}), and
     * gives the edit in the terms of the domain ({@link WorkspaceEdits}). jdtls reads the file as it knows it (nothing is
     * opened), after it has been told of the changes on disk. It has no edit for a file whose imports are as the
     * preferences of the project want them (the settings of {@link JdtlsSettings#imports}, given before the request: see
     * {@link #followTheImportPreferences}): that is not a refusal, the answer is none.
     *
     * <p>jdtls does not always judge the imports, and gabier does not write what jdtls could not judge. A type that
     * several packages declare and that the file uses without importing it is left out by jdtls, which asks the client
     * to choose ({@code java.action.organizeImports.chooseImports}, enabled by {@link JdtlsCapabilities#extended()}):
     * the request is refused with the candidates. An import that jdtls removes, that the file still uses and that
     * jdtls reports an error on (it cannot resolve it) is refused too, with the import. The edit is judged before
     * anything is written, and the diagnostics are asked only when a used import is removed: an unused import is
     * removed at once.
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project, a type has several
     *     candidates or an import cannot be resolved
     * @throws JdtlsException if jdtls dies or does not answer in time
     */
    @Override
    public synchronized Optional<ChangeSet> organizeImports(Path file) {
        JdtlsSession session = ready();
        Path path = JdtlsProject.normalized(file);
        String original = JdtlsProject.textInProject(session, path);
        var params = new CodeActionParams(
                new TextDocumentIdentifier(path.toUri().toString()),
                new org.eclipse.lsp4j.Range(new org.eclipse.lsp4j.Position(0, 0), new org.eclipse.lsp4j.Position(0, 0)),
                new CodeActionContext(List.of(), List.of(CodeActionKind.SourceOrganizeImports)));
        var service = session.server().getTextDocumentService();
        JdtlsWatch watch = session.newWatch();
        try {
            prepare(session, watch);
            session.importChoices().clear();
            var offered = watch.await(
                    "the organization of the imports of " + path,
                    () -> JdtlsRequests.unlessDeclined(service.codeAction(params)));
            Optional<CodeAction> action = offered.stream()
                    .flatMap(List::stream)
                    .filter(Either::isRight)
                    .map(Either::getRight)
                    .findFirst();
            if (action.isPresent() && action.get().getEdit() == null) {
                var unresolved = action.get();
                action = watch.await(
                        "the edit of the organization of the imports of " + path,
                        () -> JdtlsRequests.unlessDeclined(service.resolveCodeAction(unresolved)));
            }
            refuseTheTypesJdtlsCannotChoose(session.importChoices(), path);
            List<FileChange> changes = action.map(CodeAction::getEdit)
                    .map(edit -> WorkspaceEdits.of(edit, JdtlsProject::textOnDisk))
                    .orElse(List.of());
            if (changes.isEmpty()) {
                return Optional.empty();
            }
            refuseTheImportsJdtlsCannotResolve(path, original, changes);

            return Optional.of(new ChangeSet(changes));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException(
                    "Interrupted while waiting for the organization of the imports of " + path, interrupted);
        }
    }

    /**
     * Applies the clean-ups to the file with {@code java/cleanup}, which runs the identifiers of the setting {@code
     * java.cleanup.actions} in the order of the list: the adapter sets it to the identifiers asked for ({@link
     * JdtlsSettings#withCleanUps}), and back to none once jdtls has answered, whatever the answer. jdtls reads the file
     * as it knows it (nothing is opened), after it has been told of the changes on disk, and answers one edit that
     * covers the whole text, or none when no clean-up changes it: that is not a refusal, the answer is none. It does not
     * refuse an identifier it does not know (it logs it), which is why {@link CleanUp} decides before the adapter is
     * asked.
     *
     * @throws LanguageServerException if jdtls is not ready or the file is not in its project (outside it, missing or a
     *     folder)
     * @throws JdtlsException if jdtls dies or does not answer in time
     */
    @Override
    public synchronized Optional<ChangeSet> cleanUp(Path file, List<CleanUp> cleanUps) {
        JdtlsSession session = ready();
        Path path = JdtlsProject.normalized(file);
        JdtlsProject.textInProject(session, path);
        var identifier = new TextDocumentIdentifier(path.toUri().toString());
        JdtlsWatch watch = session.newWatch();
        try {
            prepare(session, watch);
            session.configure(JdtlsSettings.withCleanUps(
                    cleanUps.stream().map(CleanUp::id).toList()));
            Optional<WorkspaceEdit> answer;
            try {
                answer = watch.await(
                        "the clean-ups of " + path,
                        () -> JdtlsRequests.unlessDeclined(session.server().cleanup(identifier)));
            } finally {
                session.configure(JdtlsSettings.withCleanUps(List.of()));
            }
            List<FileChange> changes = answer.map(edit -> WorkspaceEdits.of(edit, JdtlsProject::textOnDisk))
                    .orElse(List.of());

            return changes.isEmpty() ? Optional.empty() : Optional.of(new ChangeSet(changes));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException("Interrupted while waiting for the clean-ups of " + path, interrupted);
        }
    }

    /**
     * jdtls leaves a type that several packages declare unimported, and asks the client to choose (see {@link
     * JdtlsClient#executeClientCommand}); gabier does not choose for the agent, so it refuses and names the candidates.
     */
    private static void refuseTheTypesJdtlsCannotChoose(ImportChoices choices, Path path) {
        List<String> candidates = choices.offered();
        if (!candidates.isEmpty()) {
            throw LanguageServerException.ambiguousImports(
                    path,
                    candidates.stream()
                            .sorted(Comparator.comparing(JdtlsLanguageServer::simpleName)
                                    .thenComparing(Comparator.naturalOrder()))
                            .toList());
        }
    }

    private static String simpleName(String qualified) {
        return qualified.substring(qualified.lastIndexOf('.') + 1);
    }

    /**
     * jdtls deletes the imports it cannot resolve along with the ones nothing uses: for a file whose imports it does not
     * see (a source set it has no classpath for: "The package p is not accessible") that is every import, used or not.
     * An import that the edit removes, that the file still uses and that jdtls reports an error on is one it cannot
     * judge: the request is refused, names it, and nothing is written. Only then can code that is used be lost, so
     * the diagnostics, which cost a wait for jdtls to be quiet, are asked only for the removed imports whose simple name
     * still appears in the file outside the import lines ({@link #stillUsed}); an import nothing uses is removed
     * as the edit says, even when it cannot be resolved (the class was deleted).
     */
    private void refuseTheImportsJdtlsCannotResolve(Path path, String original, List<FileChange> changes) {
        List<String> before = original.lines().toList();
        String after = SourceText.edited(path, original, editsOnTheFile(path, changes));
        List<String> kept = after.lines().toList();
        String code =
                before.stream().filter(line -> !line.startsWith("import ")).collect(Collectors.joining("\n"));
        Map<Integer, String> atRisk = new LinkedHashMap<>();
        for (int line = 0; line < before.size(); line++) {
            String text = before.get(line);
            if (text.startsWith("import ") && !kept.contains(text) && stillUsed(importedName(text), code)) {
                atRisk.put(line, importedName(text));
            }
        }
        if (atRisk.isEmpty()) {
            return;
        }
        List<String> unresolved = diagnostics(path).stream()
                .filter(found -> found.severity() == Severity.ERROR)
                .map(found -> atRisk.get(found.position().line()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!unresolved.isEmpty()) {
            throw LanguageServerException.unresolvedImports(path, unresolved);
        }
    }

    /**
     * Whether the simple name of an imported name ({@code Set} of {@code java.util.Set}, {@code entry} of {@code
     * java.util.Map.entry}) is a word of the code. A star import has no name to look for, so it counts as used: what it
     * brought in cannot be told.
     */
    private static boolean stillUsed(String imported, String code) {
        String name = imported.substring(imported.lastIndexOf('.') + 1);

        return name.equals("*")
                || Pattern.compile("(?<![\\w$])" + Pattern.quote(name) + "(?![\\w$])")
                        .matcher(code)
                        .find();
    }

    /** {@code import static a.B.c;} is {@code a.B.c}. */
    private static String importedName(String declaration) {
        return declaration
                .substring("import ".length(), declaration.lastIndexOf(';'))
                .replaceFirst("^static\\s+", "")
                .strip();
    }

    private static List<TextEdit> editsOnTheFile(Path path, List<FileChange> changes) {
        return changes.stream()
                .filter(FileChange.EditFile.class::isInstance)
                .map(FileChange.EditFile.class::cast)
                .filter(edits -> edits.file().equals(path))
                .flatMap(edits -> edits.edits().stream())
                .toList();
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

    private static List<Diagnostic> domain(List<org.eclipse.lsp4j.Diagnostic> published) {
        return published.stream()
                .map(found -> new Diagnostic(
                        severity(found.getSeverity()),
                        new Position(
                                found.getRange().getStart().getLine(),
                                found.getRange().getStart().getCharacter()),
                        Diagnostics.text(found)))
                .toList();
    }

    /** A diagnostic without a severity is up to the client to interpret: it is not taken for an error. */
    private static Severity severity(@Nullable DiagnosticSeverity severity) {
        if (severity == null) {
            return Severity.INFORMATION;
        }
        return switch (severity) {
            case Error -> Severity.ERROR;
            case Warning -> Severity.WARNING;
            case Information -> Severity.INFORMATION;
            case Hint -> Severity.HINT;
        };
    }
}
