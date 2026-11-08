package eu.gabier.daemon;

import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.CleanUp;
import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.editing.domain.Diagnostic.Severity;
import eu.gabier.editing.domain.FileChange;
import eu.gabier.editing.domain.JavaLanguageServer;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.Location;
import eu.gabier.editing.domain.NotMovableReason;
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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.Set;
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
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
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

    /** The module descriptor, at the root of a source folder. */
    private static final String MODULE_DESCRIPTOR = "module-info.java";

    /** The kinds of the symbols that jdtls lists for a class, an interface or an enum (a record is a class). */
    private static final Set<SymbolKind> TYPES = Set.of(SymbolKind.Class, SymbolKind.Interface, SymbolKind.Enum);

    /** What a name starts with: the label of an anonymous class does not ({@code {...}}). */
    private static final Pattern STARTS_AS_A_NAME = Pattern.compile("^\\p{javaJavaIdentifierStart}");

    private final Diagnostics diagnostics;
    private final Duration quiet;
    private final CompletableFuture<JdtlsSession> starting;
    private final StartProgress progress;
    private final Thread starter;

    /** The changes on disk jdtls has not acknowledged yet: they are told again by the next request. */
    private final List<FileEvent> untaken = new ArrayList<>();

    private int batches;

    /** The import settings jdtls has: its own defaults at its start, then what the adapter last gave it. */
    private ImportSettings importsOfJdtls = ImportSettings.DEFAULTS;

    private JdtlsLanguageServer(
            Diagnostics diagnostics,
            Duration quiet,
            CompletableFuture<JdtlsSession> starting,
            StartProgress progress,
            Thread starter) {
        this.diagnostics = diagnostics;
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
        return new JdtlsLanguageServer(diagnostics, quiet, starting, progress, starter);
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

    @Override
    public List<Diagnostic> diagnostics(Path file) {
        return diagnostics(List.of(file)).sequencedValues().getFirst();
    }

    /**
     * The diagnostics of several files with one wait for jdtls to be quiet: the cost of a request is that wait, so a
     * caller that has many files asks them together.
     *
     * @return the diagnostics of each file, by its normalized absolute path, in the order given
     * @throws LanguageServerException if jdtls is not ready or one of the files is not in its project (nothing is
     *     opened then)
     * @throws JdtlsException if jdtls dies or does not answer in time
     */
    synchronized SequencedMap<Path, List<Diagnostic>> diagnostics(List<Path> files) {
        SequencedMap<Path, List<Diagnostic>> answers = new LinkedHashMap<>();
        published(files).forEach((file, found) -> answers.put(file, domain(found)));
        return answers;
    }

    /** What {@link #diagnostics(List)} asks of jdtls, as jdtls publishes it. */
    private synchronized SequencedMap<Path, List<org.eclipse.lsp4j.Diagnostic>> published(List<Path> files) {
        JdtlsSession session = ready();
        Map<Path, String> texts = new LinkedHashMap<>();
        for (Path file : files) {
            texts.put(normalized(file), textInProject(session, normalized(file)));
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
        SequencedMap<Path, List<org.eclipse.lsp4j.Diagnostic>> answers = new LinkedHashMap<>();
        texts.keySet().forEach(file -> answers.put(file, diagnostics.latest(file)));
        return answers;
    }

    /**
     * Asks jdtls whether the symbol can be renamed ({@code prepareRename}), then for the rename, and gives its edit in
     * the terms of the domain ({@link WorkspaceEdits}). jdtls reads the files as it knows them (nothing is opened), after
     * it has been told of the changes on disk. A
     * symbol that jdtls says it cannot rename is no symbol (jdtls answers so for a blank, a keyword, a position past
     * the text), and a name that jdtls declines is invalid for the symbol. The name in a {@code package} line is the
     * exception: jdtls declines to prepare the rename of a package but renames it (the files move to the folder of the
     * new name, see {@link PackageDeclarations}), and renames nothing when the package named up to the segment of the
     * position has no class of its own (a refusal of its own, which says which package is empty).
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project, there is no symbol at the
     *     position, the package at the position holds no class or jdtls declines the name
     * @throws JdtlsException if jdtls dies or does not answer in time
     */
    @Override
    public synchronized ChangeSet rename(Path file, Position position, String newName) {
        JdtlsSession session = ready();
        Path path = normalized(file);
        String text = textInProject(session, path);
        var document = new TextDocumentIdentifier(path.toUri().toString());
        var at = new org.eclipse.lsp4j.Position(position.line(), position.column());
        var service = session.server().getTextDocumentService();
        JdtlsWatch watch = session.newWatch();
        try {
            prepare(session, watch);
            var renamable = watch.await(
                    "whether the symbol at " + position + " of " + path + " can be renamed",
                    () -> JdtlsRequests.unlessDeclined(service.prepareRename(new PrepareRenameParams(document, at))));
            // jdtls declines to prepare the rename of a package, which it renames all the same: its name is not a
            // symbol it would answer for, so the position is checked here.
            var packageName = PackageDeclarations.nameAt(text, position);
            if (renamable.isEmpty() && packageName.isEmpty()) {
                throw LanguageServerException.noSuchSymbol(path, position);
            }
            var edit = watch.await(
                    "the rename of the symbol at " + position + " of " + path + " to " + newName,
                    () -> JdtlsRequests.unlessDeclined(service.rename(new RenameParams(document, at, newName))));
            List<FileChange> changes = WorkspaceEdits.of(
                    edit.orElseThrow(() -> LanguageServerException.invalidName(path, position, newName)),
                    JdtlsLanguageServer::textOnDisk);
            if (changes.isEmpty()) {
                // jdtls renames the package up to the segment of the position, and answers no change at all when that
                // package holds no class of its own (a prefix of the name that only has sub-packages).
                throw packageName
                        .map(name ->
                                LanguageServerException.emptyPackage(path, position, name.upToPosition(), name.whole()))
                        .orElseGet(() -> LanguageServerException.noSuchSymbol(path, position));
            }
            return new ChangeSet(changes);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException(
                    "Interrupted while waiting for the rename of the symbol at " + position + " of " + path,
                    interrupted);
        }
    }

    /**
     * Extracts the expression of the selection into a local variable named as asked. jdtls cannot take the name: it
     * guesses one when it computes the refactoring ({@code java/getRefactorEdit}, command {@code extractVariable}, the
     * refactoring that the code action {@code refactor.extract.variable} stands for) and says where that name is in the
     * text the edit gives, for the client to rename it. So the edit is applied in memory, the text it gives is
     * opened in jdtls (never written to disk) and the variable is renamed with {@code textDocument/rename}, which is
     * what a client does after the edit. The two edits are folded into one on the text as it is on disk
     * ({@link SourceText#replacing}), so that the answer shows the final name only.
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project, jdtls cannot extract the
     *     selection or declines the name
     * @throws JdtlsException if jdtls dies, does not answer in time or answers what the adapter cannot apply
     */
    @Override
    public synchronized ChangeSet extractVariable(Path file, Range selection, String name) {
        return extract(Extraction.VARIABLE, file, selection, name);
    }

    /**
     * Extracts the expression of the selection into a new constant ({@code private static final}, declared at the top of
     * the class) named as asked, and replaces it by the constant. As for the variable, jdtls proposes a name of its own
     * ({@code java/getRefactorEdit}, command {@code extractConstant}, which needs no client capability and no setting)
     * and says where it is for the client to rename: the constant is renamed with {@code textDocument/rename} on the text
     * the edit gives, and the two edits are folded into one. A selection that jdtls cannot extract is refused in the same way by the four extractions.
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project, jdtls cannot extract the
     *     selection or declines the name
     * @throws JdtlsException if jdtls dies, does not answer in time or answers what the adapter cannot apply
     */
    @Override
    public synchronized ChangeSet extractConstant(Path file, Range selection, String name) {
        return extract(Extraction.CONSTANT, file, selection, name);
    }

    /**
     * Extracts the expression of the selection into a new field of the class named as asked, and replaces it by the
     * field. As for the variable, jdtls proposes a name of its own ({@code java/getRefactorEdit}, command {@code
     * extractField}, which needs no client capability and no setting) and says where it is for the client to rename: the
     * field is renamed with {@code textDocument/rename} on the text the edit gives, and the two edits are folded into
     * one. Where the field is initialised is left to jdtls: the command takes the place as an optional argument, which the
     * adapter does not send, so jdtls takes its default, the current method.
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project, jdtls cannot extract the
     *     selection or declines the name
     * @throws JdtlsException if jdtls dies, does not answer in time or answers what the adapter cannot apply
     */
    @Override
    public synchronized ChangeSet extractField(Path file, Range selection, String name) {
        return extract(Extraction.FIELD, file, selection, name);
    }

    /**
     * Extracts the selection (statements, or an expression) into a new method named as asked, and replaces it by the
     * call. As for the variable, jdtls proposes a name of its own ({@code java/getRefactorEdit}, command {@code
     * extractMethod}) and says where it is for the client to rename: the method is renamed with {@code
     * textDocument/rename} on the text the edit gives, and the two edits are folded into one.
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project, jdtls cannot extract the
     *     selection or declines the name
     * @throws JdtlsException if jdtls dies, does not answer in time or answers what the adapter cannot apply
     */
    @Override
    public synchronized ChangeSet extractMethod(Path file, Range selection, String name) {
        return extract(Extraction.METHOD, file, selection, name);
    }

    /**
     * The refactorings of {@code java/getRefactorEdit} that introduce a name for the client to rename: the command of
     * the refactoring, how the extraction is called in the messages, and what it introduces.
     */
    private enum Extraction {
        VARIABLE("extractVariable", "the extraction of the expression", "the extracted variable"),
        CONSTANT("extractConstant", "the extraction of the expression into a constant", "the extracted constant"),
        FIELD("extractField", "the extraction of the expression into a field", "the extracted field"),
        METHOD("extractMethod", "the extraction of the selection into a method", "the extracted method");

        private final String command;
        private final String what;
        private final String extracted;

        Extraction(String command, String what, String extracted) {
            this.command = command;
            this.what = what;
            this.extracted = extracted;
        }
    }

    /**
     * The extraction that jdtls offers as a command of {@code java/getRefactorEdit}, with the name asked for. A selection
     * jdtls cannot extract is refused the same way by the four extractions, whatever jdtls answers: nothing at all (what
     * it answers for these commands), a response error {@code InvalidRequest}, or an answer with no edit, with a message
     * or without. The refusal is gabier's text; the message of jdtls, when there is one, is not carried.
     */
    private ChangeSet extract(Extraction extraction, Path file, Range selection, String name) {
        String command = extraction.command;
        String what = extraction.what;
        JdtlsSession session = ready();
        Path path = normalized(file);
        String original = textInProject(session, path);
        var document = new TextDocumentIdentifier(path.toUri().toString());
        var refactoring = new JdtlsServer.RefactorEditParams(
                command, new CodeActionParams(document, lsp(selection), new CodeActionContext(List.of())));
        JdtlsWatch watch = session.newWatch();
        try {
            prepare(session, watch);
            var proposal = watch.await(
                    what + " at " + selection + " of " + path,
                    () -> JdtlsRequests.unlessDeclined(session.server().getRefactorEdit(refactoring)));
            var refactor = proposal.orElseThrow(() -> LanguageServerException.notExtractable(path, selection));
            var proposed = refactor.edit();
            if (proposed == null) {
                throw LanguageServerException.notExtractable(path, selection);
            }
            String extracted = SourceText.edited(path, original, editsOf(path, proposed, original));
            var introduced = introducedName(extraction, refactor, extracted);
            String named = extracted;
            if (!introduced.text().equals(name)) {
                named = SourceText.edited(
                        path,
                        extracted,
                        renamed(session, watch, extraction, path, extracted, introduced, name, selection));
            }

            return new ChangeSet(
                    List.of(new FileChange.EditFile(path, List.of(SourceText.replacing(original, named)))));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException(
                    "Interrupted while waiting for " + what + " at " + selection + " of " + path, interrupted);
        }
    }

    /** The name jdtls introduced, and where it is in the text after the edit. */
    private record Introduced(int offset, String text) {}

    private static Introduced introducedName(
            Extraction extraction, JdtlsServer.RefactorEdit refactor, String extracted) {
        var follow = refactor.command();
        if (follow == null || follow.arguments().isEmpty()) {
            throw new JdtlsException(
                    "jdtls made " + extraction.extracted + " but did not say where its name is: " + refactor);
        }
        var where = follow.arguments().getFirst();

        return new Introduced(where.offset(), extracted.substring(where.offset(), where.offset() + where.length()));
    }

    /**
     * The edits of the one file the extraction is about, as they are given: the edit of jdtls is made of this file only.
     */
    private static List<TextEdit> editsOf(Path path, WorkspaceEdit edit, String text) {
        List<FileChange> changes =
                WorkspaceEdits.of(edit, other -> other.equals(path) ? Optional.of(text) : Optional.empty());

        return changes.stream()
                .map(change -> {
                    if (change instanceof FileChange.EditFile edits
                            && edits.file().equals(path)) {
                        return edits.edits();
                    }
                    throw new JdtlsException(
                            "jdtls answered an extraction that does more than edit " + path + ": " + change);
                })
                .flatMap(List::stream)
                .toList();
    }

    /** Renames what the extraction introduced on the text jdtls has been given, opened for the time of the request. */
    private List<TextEdit> renamed(
            JdtlsSession session,
            JdtlsWatch watch,
            Extraction extraction,
            Path path,
            String text,
            Introduced introduced,
            String name,
            Range selection)
            throws InterruptedException {
        var service = session.server().getTextDocumentService();
        var where = SourceText.positionAt(text, introduced.offset());
        try {
            var publication = open(session, Map.of(path, text)).get(path);
            watch.await("jdtls to take in " + extraction.extracted + " in " + path, () -> publication);
            if (extraction == Extraction.METHOD) {
                refuseASignatureInUse(session, watch, path, introduced, lsp(where), name, selection);
            }
            var edit = watch.await(
                    "the rename of " + extraction.extracted + " at " + where + " of " + path + " to " + name,
                    () -> JdtlsRequests.unlessDeclined(service.rename(new RenameParams(
                            new TextDocumentIdentifier(path.toUri().toString()),
                            new org.eclipse.lsp4j.Position(where.line(), where.column()),
                            name))));

            List<TextEdit> edits = editsOf(
                    path,
                    edit.orElseThrow(() -> LanguageServerException.invalidName(path, selection.start(), name)),
                    text);
            if (!leavesTheDeclarationAsItWas(text, SourceText.edited(path, text, edits), introduced, name)) {
                throw LanguageServerException.invalidName(path, selection.start(), name);
            }

            return edits;
        } finally {
            close(session, List.of(path));
        }
    }

    /**
     * Refuses the name of a method that the class already declares with the parameters of the extracted one. jdtls
     * does not decline it: its rename answers an edit that leaves two methods of one signature, which does not
     * compile. The methods are those jdtls lists for the text it was given: a flat list in the order of its outline
     * (the members of a type, then the type), each labelled {@code name(parameters)}, or {@code name(parameters)
     * <type parameters>} for a generic method, with no return type, with the range of its name and the simple name of
     * its parent (empty for an anonymous class). The extracted method is the one whose name holds the place that
     * {@code textDocument/definition} gives for the name jdtls introduced, and the class it is in is the first type
     * after it in the list that has the name of its container: two classes of one simple name are two types.
     */
    private void refuseASignatureInUse(
            JdtlsSession session,
            JdtlsWatch watch,
            Path path,
            Introduced introduced,
            org.eclipse.lsp4j.Position where,
            String name,
            Range selection)
            throws InterruptedException {
        var service = session.server().getTextDocumentService();
        var document = new TextDocumentIdentifier(path.toUri().toString());
        var definition = watch.await(
                "the declaration of the method " + introduced.text() + " in " + path,
                () -> service.definition(new DefinitionParams(document, where)));
        var declaredAt = Optional.ofNullable(definition)
                .filter(Either::isLeft)
                .map(Either::getLeft)
                .flatMap(places -> places.stream().findFirst())
                .map(place -> place.getRange().getStart())
                .orElseThrow(() -> new JdtlsException(
                        "jdtls did not say where the method " + introduced.text() + " is declared in " + path));
        var listed = watch.await(
                "the methods of the extracted text of " + path,
                () -> service.documentSymbol(new DocumentSymbolParams(document)));
        List<SymbolInformation> symbols = Optional.ofNullable(listed).orElse(List.of()).stream()
                .filter(Either::isLeft)
                .map(Either::getLeft)
                .toList();
        var extracted = symbols.stream()
                .filter(symbol -> symbol.getKind() == SymbolKind.Method && nameIsAt(symbol, declaredAt))
                .findFirst()
                .orElseThrow(() -> new JdtlsException("jdtls did not list the method " + introduced.text()
                        + " it extracted in " + path + ": " + symbols));
        String wanted = name + parametersOf(extracted);
        var owner = ownerOf(symbols, extracted);
        if (symbols.stream()
                .filter(symbol -> symbol.getKind() == SymbolKind.Method)
                .anyMatch(method -> signatureOf(method).equals(wanted)
                        && ownerOf(symbols, method).equals(owner))) {
            throw LanguageServerException.invalidName(path, selection.start(), name);
        }
    }

    private static boolean nameIsAt(SymbolInformation symbol, org.eclipse.lsp4j.Position place) {
        var range = symbol.getLocation().getRange();

        return compare(range.getStart(), place) <= 0 && compare(place, range.getEnd()) <= 0;
    }

    /**
     * The type a member is declared in: the first type listed after it whose name is that of the container jdtls gave
     * (jdtls lists a type after its members). The listing has no other link between a member and its type.
     */
    private static Optional<SymbolInformation> ownerOf(List<SymbolInformation> symbols, SymbolInformation member) {
        String container = Objects.requireNonNullElse(member.getContainerName(), "");

        return symbols.subList(symbols.indexOf(member) + 1, symbols.size()).stream()
                .filter(symbol -> TYPES.contains(symbol.getKind()) && isNamed(symbol, container))
                .findFirst();
    }

    /** Whether the type is the one a container name is of: its label without the type parameters, none for an anonymous one. */
    private static boolean isNamed(SymbolInformation type, String container) {
        String label = type.getName();

        return container.isEmpty()
                ? !STARTS_AS_A_NAME.matcher(label).find()
                : label.replaceFirst("<.*", "").equals(container);
    }

    /** {@code name(parameters)} of a label {@code name(parameters)} or {@code name(parameters) <type parameters>}. */
    private static String signatureOf(SymbolInformation method) {
        String label = method.getName();

        return label.substring(0, label.indexOf(')') + 1);
    }

    private static String parametersOf(SymbolInformation method) {
        String signature = signatureOf(method);

        return signature.substring(signature.indexOf('('));
    }

    /**
     * Whether the rename only changed the name where jdtls introduced it. jdtls does not decline every name it should:
     * for the name of a local variable or parameter in scope it answers an edit all the same, and that edit does not
     * leave the line as it was (the space before {@code =} is lost, the end of the next line is cut). A name that jdtls
     * renames into such an edit is not valid for what was extracted. A rename never adds or removes a line, so the line
     * is looked for by its number, not at the offset: the first place that jdtls introduced the name at is not always
     * the first one the rename changes (a field is declared above the statement that assigns it), which moves the offset.
     */
    private static boolean leavesTheDeclarationAsItWas(
            String before, String after, Introduced introduced, String name) {
        int line = SourceText.positionAt(before, introduced.offset()).line();
        String restBefore =
                restOfTheLine(before, introduced.offset() + introduced.text().length());

        return after.lines().skip(line).findFirst().orElse("").endsWith(name + restBefore);
    }

    private static String restOfTheLine(String text, int from) {
        return text.substring(from).lines().findFirst().orElse("");
    }

    private static org.eclipse.lsp4j.Position lsp(Position position) {
        return new org.eclipse.lsp4j.Position(position.line(), position.column());
    }

    private static org.eclipse.lsp4j.Range lsp(Range range) {
        return new org.eclipse.lsp4j.Range(
                new org.eclipse.lsp4j.Position(
                        range.start().line(), range.start().column()),
                new org.eclipse.lsp4j.Position(range.end().line(), range.end().column()));
    }

    /**
     * Makes a top-level type of the nested type that encloses the position, with {@code java/move} and the move kind
     * {@code moveTypeToNewFile}.
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project or jdtls cannot move the
     *     type
     * @throws JdtlsException if jdtls dies, does not answer in time or answers what the adapter cannot apply
     */
    @Override
    public synchronized ChangeSet moveTypeToFile(Path file, Position position) {
        JdtlsSession session = ready();
        Path path = normalized(file);
        textInProject(session, path);
        var at = new org.eclipse.lsp4j.Position(position.line(), position.column());
        var context = new CodeActionParams(
                new TextDocumentIdentifier(path.toUri().toString()),
                new org.eclipse.lsp4j.Range(at, at),
                new CodeActionContext(List.of()));
        var move = JdtlsServer.MoveParams.typeToNewFile(context);
        JdtlsWatch watch = session.newWatch();
        try {
            prepare(session, watch);
            var proposal = watch.await(
                    "the move of the type at " + position + " of " + path,
                    () -> JdtlsRequests.unlessDeclined(session.server().move(move)));
            var refactor = proposal.orElseThrow(() -> LanguageServerException.notMovable(path, position));
            var edit = refactor.edit();
            if (edit == null) {
                throw LanguageServerException.notMovable(path, position);
            }
            List<FileChange> changes = WorkspaceEdits.of(edit, JdtlsLanguageServer::textOnDisk);
            if (changes.isEmpty()) {
                throw LanguageServerException.notMovable(path, position);
            }

            return new ChangeSet(changes);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException(
                    "Interrupted while waiting for the move of the type at " + position + " of " + path, interrupted);
        }
    }

    /**
     * Moves files to a package with {@code java/move} and the move kind {@code moveResource} (jdtls reads the files, the
     * URI of the folder of the package as the destination, and whether to update the references, as {@code
     * MoveHandler.moveCU} does). The destination is a package of the model of jdtls: it takes it from the folder, under
     * the source folder of the files. What jdtls does not do, the adapter decides before it asks, as jdtls skips a file
     * it does not know ({@code moveCU} ignores it), moves a file onto a file of its name and answers an empty move for a
     * file already in the package: each file must be a Java source of a source folder ({@code
     * java.project.listSourcePaths}), the files must share it (jdtls would put a test class in the main sources), none
     * may be in the package already or take the name of a file there.
     *
     * <p>jdtls answers no change, without a word, for a package whose folder it does not know, and does not see a
     * folder made on disk until it is told of it (an event of {@code workspace/didChangeWatchedFiles} for the folder,
     * which it refreshes): there is no setting and no capability for it, and the protocol has no operation to make a
     * folder (the client does, as for the {@code .temp} file jdtls itself creates to rename a package). So for a package
     * with no folder the adapter makes the missing folders for the time of the request, tells jdtls, asks, and removes
     * them, telling jdtls again, whatever the answer: a dry run leaves no folder behind. The change set moves the files
     * into the folder that does not exist, which applying it makes.
     *
     * @throws LanguageServerException if jdtls is not ready, a file is not in its project, or the files cannot be moved
     *     to the package (see {@link NotMovableReason})
     * @throws JdtlsException if jdtls dies, does not answer in time, cannot move the files for a reason the checks did
     *     not foresee, or the folder made for the request cannot be removed
     */
    @Override
    public synchronized ChangeSet moveToPackage(List<Path> files, PackageName target) {
        JdtlsSession session = ready();
        List<Path> paths = files.stream().map(JdtlsLanguageServer::normalized).toList();
        paths.forEach(path -> textInProject(session, path));
        String move = "the move of " + paths.size() + (paths.size() == 1 ? " file" : " files") + " to the package "
                + target.value();
        JdtlsWatch watch = session.newWatch();
        try {
            prepare(session, watch);
            Destination destination = destinationOf(paths, target, sourceFolders(session, watch));
            Path folder = destination.folder();
            List<Path> made = makeFolders(paths, folder, target);
            try {
                if (!made.isEmpty()) {
                    untaken.add(new FileEvent(made.getFirst().toUri().toString(), FileChangeType.Created));
                    takeInChangesOnDisk(session, watch);
                }

                return askToMove(session, watch, paths, destination, target, move);
            } finally {
                removeFolders(made, target);
                if (!made.isEmpty()) {
                    untaken.add(new FileEvent(made.getFirst().toUri().toString(), FileChangeType.Deleted));
                    takeInChangesOnDisk(session, watch);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException("Interrupted while waiting for " + move, interrupted);
        }
    }

    private ChangeSet askToMove(
            JdtlsSession session,
            JdtlsWatch watch,
            List<Path> paths,
            Destination destination,
            PackageName target,
            String move)
            throws InterruptedException {
        var request = JdtlsServer.MoveParams.resources(
                paths.stream().map(path -> path.toUri().toString()).toList(),
                destination.folder().toUri().toString());
        var proposal = watch.await(
                move, () -> JdtlsRequests.unlessDeclined(session.server().move(request)));
        var refactor = proposal.orElseThrow(() -> nothingToMove(paths, target));
        if (refactor.errorMessage() != null) {
            throw new JdtlsException(
                    "jdtls cannot move the files to the package " + target.value() + ": " + refactor.errorMessage());
        }
        var edit = refactor.edit();
        if (edit == null) {
            throw nothingToMove(paths, target);
        }
        List<FileChange> changes = new ArrayList<>(WorkspaceEdits.of(edit, JdtlsLanguageServer::textOnDisk));
        if (changes.isEmpty()) {
            throw nothingToMove(paths, target);
        }
        followInTheModuleDescriptor(paths, destination.sourceFolder(), target).ifPresent(changes::add);

        return new ChangeSet(WorkspaceEdits.inOrderOfAMove(changes));
    }

    /**
     * The edit of {@code module-info.java}, at the root of the source folder of the files, that makes the {@code
     * exports} and {@code opens} of a package follow its classes when the move takes the last of them (no Java file is
     * left in its folder, a {@code package-info.java} counting as one). jdtls rewrites what uses the moved classes but
     * leaves the descriptor as it is (observed on the real server; no setting known changes it), so the adapter edits it (see
     * {@link ModuleExports}).
     */
    private static Optional<FileChange> followInTheModuleDescriptor(
            List<Path> paths, Path sourceFolder, PackageName target) {
        Path descriptor = sourceFolder.resolve(MODULE_DESCRIPTOR);
        Optional<String> text = textOnDisk(descriptor);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> emptied = new HashMap<>();
        for (Path folder : paths.stream().map(Path::getParent).distinct().toList()) {
            if (!folder.equals(sourceFolder) && noJavaFileLeft(folder, paths)) {
                List<String> names = new ArrayList<>();
                sourceFolder.relativize(folder).forEach(name -> names.add(name.toString()));
                emptied.put(String.join(".", names), target.value());
            }
        }
        List<eu.gabier.editing.domain.TextEdit> edits = ModuleExports.following(text.get(), emptied);

        return edits.isEmpty() ? Optional.empty() : Optional.of(new FileChange.EditFile(descriptor, edits));
    }

    private static boolean noJavaFileLeft(Path folder, List<Path> moved) {
        try (Stream<Path> files = Files.list(folder)) {
            return files.noneMatch(file -> file.getFileName().toString().endsWith(".java") && !moved.contains(file));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static LanguageServerException nothingToMove(List<Path> paths, PackageName target) {
        return LanguageServerException.notMovableToPackage(paths, target, NotMovableReason.NOTHING_TO_MOVE);
    }

    /**
     * The source folder of the files and the folder of the package under it, once each file is known to be movable
     * there.
     *
     * @throws LanguageServerException for the first file that is not a Java source, is under no source folder, is in the
     *     package or has the name of a file of the package or of an earlier file, and for files of several source
     *     folders
     */
    private static Destination destinationOf(List<Path> paths, PackageName target, List<Path> sourceFolders) {
        Set<Path> roots = new LinkedHashSet<>();
        for (Path path : paths) {
            if (!path.getFileName().toString().endsWith(".java")) {
                throw LanguageServerException.notMovableToPackage(
                        List.of(path), target, NotMovableReason.NOT_A_JAVA_SOURCE);
            }
            roots.add(sourceFolders.stream()
                    .filter(path::startsWith)
                    .max(Comparator.comparingInt(Path::getNameCount))
                    .orElseThrow(() -> LanguageServerException.notMovableToPackage(
                            List.of(path), target, NotMovableReason.OUTSIDE_SOURCE_FOLDERS)));
        }
        if (roots.size() > 1) {
            throw LanguageServerException.notMovableToPackage(paths, target, NotMovableReason.SEVERAL_SOURCE_FOLDERS);
        }
        Path sourceFolder = roots.iterator().next();
        Path folder = sourceFolder.resolve(target.folder());
        Set<Path> names = new HashSet<>();
        for (Path path : paths) {
            if (folder.equals(path.getParent())) {
                throw LanguageServerException.notMovableToPackage(
                        List.of(path), target, NotMovableReason.ALREADY_IN_PACKAGE);
            }
            if (Files.exists(folder.resolve(path.getFileName())) || !names.add(path.getFileName())) {
                throw LanguageServerException.notMovableToPackage(List.of(path), target, NotMovableReason.NAME_TAKEN);
            }
        }

        return new Destination(sourceFolder, folder);
    }

    /** Where a move puts the files: the source folder they are in and the folder of the package under it. */
    private record Destination(Path sourceFolder, Path folder) {}

    /**
     * Makes the folders of the package that are not there, from the highest down.
     *
     * @return the folders made, highest first, none if the package had its folder
     * @throws LanguageServerException if a folder cannot be made (a file is where it goes); those made before are removed
     */
    private static List<Path> makeFolders(List<Path> paths, Path folder, PackageName target) {
        Deque<Path> missing = new ArrayDeque<>();
        Path candidate = folder;
        while (!Files.isDirectory(candidate)) {
            missing.push(candidate);
            candidate = Objects.requireNonNull(candidate.getParent(), "the source folder is a folder");
        }
        List<Path> made = new ArrayList<>();
        try {
            for (Path missed : missing) {
                Files.createDirectory(missed);
                made.add(missed);
            }
        } catch (IOException failure) {
            removeFolders(made, target);
            throw LanguageServerException.notMovableToPackage(paths, target, NotMovableReason.FOLDER_NOT_MADE);
        }

        return made;
    }

    /** Removes the folders made for a request, the lowest first. */
    private static void removeFolders(List<Path> made, PackageName target) {
        for (Path folder : made.reversed()) {
            try {
                Files.delete(folder);
            } catch (IOException failure) {
                throw new JdtlsException(
                        "gabier could not remove the folder " + folder + " made for the move to the package "
                                + target.value() + ": " + failure,
                        failure);
            }
        }
    }

    /**
     * Inlines the local variable, the method or the constant at the position, with the code action {@code
     * refactor.inline} asked for an empty range at the position (jdtls offers it when the position is on the name of
     * what it can inline: {@code RefactorProcessor.getInlineProposal}). The {@code only} of the request chooses
     * the processors jdtls runs, not the actions it returns (the refactor processor answers all of its {@code refactor.*}
     * actions), so the action taken is the one of the kind {@code refactor.inline} that is titled "Inline ..." ("Inline
     * Method", "Inline Constant", "Inline local variable"): jdtls gives "Make static" the same kind.
     *
     * <p>The edit is computed when the action is resolved, as for the organization of the imports, and jdtls gives none
     * when the conditions of the refactoring fail: a variable assigned more than once, a method that is recursive.
     * Nothing is offered for a position on no variable, method or constant, an abstract method, or a declaration that
     * nothing uses; all of these are one refusal, {@code not-inlinable}, as jdtls does not say which. On a variable,
     * jdtls inlines every use and removes the declaration, whatever the position; on a method or a constant, a position
     * on the declaration inlines every use and removes it, a position on a use inlines that use only.
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project or jdtls has nothing to
     *     inline there
     * @throws JdtlsException if jdtls dies or does not answer in time
     */
    @Override
    public synchronized ChangeSet inline(Path file, Position position) {
        JdtlsSession session = ready();
        Path path = normalized(file);
        textInProject(session, path);
        var at = new org.eclipse.lsp4j.Position(position.line(), position.column());
        var params = new CodeActionParams(
                new TextDocumentIdentifier(path.toUri().toString()),
                new org.eclipse.lsp4j.Range(at, at),
                new CodeActionContext(List.of(), List.of(CodeActionKind.RefactorInline)));
        var service = session.server().getTextDocumentService();
        JdtlsWatch watch = session.newWatch();
        try {
            prepare(session, watch);
            var offered = watch.await(
                    "the inlining of the symbol at " + position + " of " + path,
                    () -> JdtlsRequests.unlessDeclined(service.codeAction(params)));
            Optional<CodeAction> action = offered.stream()
                    .flatMap(List::stream)
                    .filter(Either::isRight)
                    .map(Either::getRight)
                    .filter(JdtlsLanguageServer::isAnInlining)
                    .findFirst();
            if (action.isPresent() && action.get().getEdit() == null) {
                var unresolved = action.get();
                action = watch.await(
                        "the edit of the inlining of the symbol at " + position + " of " + path,
                        () -> JdtlsRequests.unlessDeclined(service.resolveCodeAction(unresolved)));
            }
            List<FileChange> changes = action.map(CodeAction::getEdit)
                    .map(edit -> WorkspaceEdits.of(edit, JdtlsLanguageServer::textOnDisk))
                    .orElse(List.of());
            if (changes.isEmpty()) {
                throw LanguageServerException.notInlinable(path, position);
            }

            return new ChangeSet(changes);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException(
                    "Interrupted while waiting for the inlining of the symbol at " + position + " of " + path,
                    interrupted);
        }
    }

    /**
     * Where the symbol at the position is used, with {@code textDocument/references}. jdtls answers the references of
     * the type or the method that encloses a position where it finds no symbol (a blank, a comment: {@code
     * ReferencesHandler} falls back to the element at the offset), so the adapter asks {@code prepareRename} first,
     * as {@link #rename} does, and takes what jdtls declines for no symbol. jdtls reads the files as it knows them
     * (nothing is opened), after it has been told of the changes on disk.
     *
     * <p>jdtls does not say which of the locations it gives with {@code includeDeclaration} is the declaration. So the
     * uses are asked for without it, and, when the declaration is wanted, asked for again with it: what the second
     * answer has more than the first is the declaration. A location that is not a file ({@code jdt:} for a class of
     * a library) is left out; jdtls gives none to a client that does not declare {@code classFileContentsSupport},
     * which gabier does not.
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project or there is no symbol that
     *     jdtls can rename at the position
     * @throws JdtlsException if jdtls dies or does not answer in time
     */
    @Override
    public synchronized ReferenceLocations references(Path file, Position position, boolean includeDeclaration) {
        JdtlsSession session = ready();
        Path path = normalized(file);
        textInProject(session, path);
        var document = new TextDocumentIdentifier(path.toUri().toString());
        var at = new org.eclipse.lsp4j.Position(position.line(), position.column());
        var service = session.server().getTextDocumentService();
        JdtlsWatch watch = session.newWatch();
        String what = "the references of the symbol at " + position + " of " + path;
        try {
            takeInChangesOnDisk(session, watch);
            var symbol = watch.await(
                    "whether there is a symbol at " + position + " of " + path,
                    () -> JdtlsRequests.unlessDeclined(service.prepareRename(new PrepareRenameParams(document, at))));
            if (symbol.isEmpty()) {
                throw LanguageServerException.noSuchSymbol(path, position);
            }
            List<Location> uses =
                    locations(watch.await(what, () -> service.references(references(document, at, false))));
            if (!includeDeclaration) {
                return new ReferenceLocations(List.of(), uses);
            }
            Set<Location> known = Set.copyOf(uses);
            List<Location> declarations = locations(watch.await(
                            what + " with the declaration", () -> service.references(references(document, at, true))))
                    .stream()
                    .filter(location -> !known.contains(location))
                    .toList();

            return new ReferenceLocations(declarations, uses);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException("Interrupted while waiting for " + what, interrupted);
        }
    }

    private static ReferenceParams references(
            TextDocumentIdentifier document, org.eclipse.lsp4j.Position at, boolean includeDeclaration) {
        return new ReferenceParams(document, at, new ReferenceContext(includeDeclaration));
    }

    /** The places of the answer that are files, in the terms of the domain; none for an answer of null. */
    private static List<Location> locations(@Nullable List<? extends org.eclipse.lsp4j.Location> answer) {
        if (answer == null) {
            return List.of();
        }

        return answer.stream()
                .filter(found -> found.getUri().startsWith("file:"))
                .map(found -> new Location(
                        Path.of(URI.create(found.getUri())),
                        new Position(
                                found.getRange().getStart().getLine(),
                                found.getRange().getStart().getCharacter())))
                .toList();
    }

    /**
     * The code actions of the range, with {@code textDocument/codeAction}. jdtls computes the fixes of a problem only for
     * the diagnostics the request carries (without them it offers none: {@code CodeActionHandler} reads the problems
     * from {@code context.diagnostics}), so the adapter opens the file, takes the diagnostics jdtls publishes for it as
     * {@link #diagnostics} does, and sends those that touch the range. The {@code only} of the request names the
     * processors jdtls runs: {@code quickfix}, {@code refactor} and {@code quickassist}, which is jdtls's own kind for
     * the rewrites of a piece of code (a concatenation to a text block) and which it gives only to a client that
     * declares it ({@link JdtlsCapabilities#QUICK_ASSIST}); the {@code source} actions, which are about a whole file
     * (organize the imports, generate accessors) and are offered whatever the range, are left out, so that a range with
     * nothing to offer has an empty answer. A bare command, which a client without literal support is answered, is not
     * an action and is not listed.
     *
     * @throws LanguageServerException if jdtls is not ready or the file is not in its project
     * @throws JdtlsException if jdtls dies or does not answer in time
     */
    @Override
    public synchronized OfferedActions codeActions(Path file, Range range) {
        return new OfferedActions(offered(file, range).stream()
                .map(offer -> new OfferedAction(kindOf(offer), offer.getTitle()))
                .toList());
    }

    /**
     * The edit of the action, taken from the actions jdtls offers on the range now (the one of the same kind and title)
     * and resolved ({@code codeAction/resolve}) when jdtls does not give it with the action: the client declares {@code
     * codeAction.resolveSupport} for {@code edit}. An action that jdtls no longer offers, one that has no edit once
     * resolved (a command for the editor: {@code java.action.applyRefactoringCommand} for "Change signature", which
     * opens a dialog) and one whose edit changes nothing are one refusal, {@code not-applicable}.
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project, or there is no edit for
     *     the action
     * @throws JdtlsException if jdtls dies or does not answer in time
     */
    @Override
    public synchronized ChangeSet codeActionEdit(Path file, Range range, OfferedAction action) {
        Path path = normalized(file);
        Optional<CodeAction> found = offered(file, range).stream()
                .filter(offer ->
                        kindOf(offer).equals(action.kind()) && offer.getTitle().equals(action.title()))
                .findFirst();
        JdtlsSession session = ready();
        var service = session.server().getTextDocumentService();
        JdtlsWatch watch = session.newWatch();
        try {
            if (found.isPresent() && found.get().getEdit() == null) {
                var unresolved = found.get();
                found = watch.await(
                        "the edit of the action \"" + action.title() + "\" on " + range + " of " + path,
                        () -> JdtlsRequests.unlessDeclined(service.resolveCodeAction(unresolved)));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException(
                    "Interrupted while waiting for the edit of the action \"" + action.title() + "\" on " + range
                            + " of " + path,
                    interrupted);
        }
        List<FileChange> changes = found.map(CodeAction::getEdit)
                .map(edit -> WorkspaceEdits.of(edit, JdtlsLanguageServer::textOnDisk))
                .orElse(List.of());
        if (changes.isEmpty()) {
            throw LanguageServerException.notApplicable(path, range, action.title());
        }

        return new ChangeSet(changes);
    }

    private List<CodeAction> offered(Path file, Range range) {
        Path path = normalized(file);
        var asked = lsp(range);
        List<org.eclipse.lsp4j.Diagnostic> problems = published(List.of(path)).sequencedValues().getFirst().stream()
                .filter(problem -> touches(problem.getRange(), asked))
                .toList();
        JdtlsSession session = ready();
        var context = new CodeActionContext(
                problems, List.of(CodeActionKind.QuickFix, CodeActionKind.Refactor, JdtlsCapabilities.QUICK_ASSIST));
        var params =
                new CodeActionParams(new TextDocumentIdentifier(path.toUri().toString()), asked, context);
        var service = session.server().getTextDocumentService();
        JdtlsWatch watch = session.newWatch();
        try {
            prepare(session, watch);
            var offered = watch.await(
                    "the code actions on " + range + " of " + path,
                    () -> JdtlsRequests.unlessDeclined(service.codeAction(params)));
            return offered.stream()
                    .flatMap(List::stream)
                    .filter(Either::isRight)
                    .map(Either::getRight)
                    .toList();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException(
                    "Interrupted while waiting for the code actions on " + range + " of " + path, interrupted);
        }
    }

    /** Whether the problem is on the range or touches it: a point inside a problem is on it. */
    private static boolean touches(org.eclipse.lsp4j.Range problem, org.eclipse.lsp4j.Range range) {
        return compare(problem.getStart(), range.getEnd()) <= 0 && compare(range.getStart(), problem.getEnd()) <= 0;
    }

    private static int compare(org.eclipse.lsp4j.Position first, org.eclipse.lsp4j.Position second) {
        return first.getLine() != second.getLine()
                ? Integer.compare(first.getLine(), second.getLine())
                : Integer.compare(first.getCharacter(), second.getCharacter());
    }

    /** The kind of an action, empty when the server gave none: the protocol makes it optional. */
    private static String kindOf(CodeAction offer) {
        return Objects.requireNonNullElse(offer.getKind(), "");
    }

    private static boolean isAnInlining(CodeAction offer) {
        return CodeActionKind.RefactorInline.equals(offer.getKind())
                && offer.getTitle().startsWith("Inline ");
    }

    /**
     * Creates a type of the kind in a file that need not exist: the file is opened in jdtls with no text (as an editor
     * opens a new file, never written to disk), and the snippet of the kind that jdtls completes the empty text with
     * ({@code textDocument/completion} at 0:0, the client declaring {@code snippetSupport}, see {@link
     * JdtlsCapabilities}) is the text of the file: jdtls derives the {@code package} line from the folder of the file
     * in its source folder, writes the name of the file as the name of the type and, with the preferences of {@link
     * JdtlsSettings#javaPreferences}, no comment. {@link NewTypeText} turns the snippet into the text of a file.
     *
     * <p>jdtls offers the snippet whatever the file: for one that is under no source folder it has no package to
     * write, and gives the text of the default package. So the adapter asks which folders are source folders ({@code
     * java.project.listSourcePaths}) and refuses a file that is under none. A kind jdtls has no snippet for (a record,
     * before Java 14) is refused the same way, and jdtls does not say which of the two it is.
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project, the file is under no
     *     source folder or jdtls has no snippet of this kind
     * @throws JdtlsException if jdtls dies or does not answer in time
     */
    @Override
    public synchronized ChangeSet createType(Path file, TypeKind kind) {
        JdtlsSession session = ready();
        Path path = normalized(file);
        requireInProject(session, path);
        String what = "the snippet of a " + kind.keyword() + " for " + path;
        JdtlsWatch watch = session.newWatch();
        try {
            prepare(session, watch);
            if (sourceFolders(session, watch).stream().noneMatch(path::startsWith)) {
                throw LanguageServerException.notCreatable(path, kind);
            }
            String snippet = snippetOfKind(session, watch, path, kind, what)
                    .orElseThrow(() -> LanguageServerException.notCreatable(path, kind));
            var start = new Position(0, 0);
            var text = new TextEdit(new Range(start, start), NewTypeText.of(snippet));

            return new ChangeSet(
                    List.of(new FileChange.CreateFile(path), new FileChange.EditFile(path, List.of(text))));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException("Interrupted while waiting for " + what, interrupted);
        }
    }

    /** The snippet of the kind that jdtls completes the empty text of the file with; the file is closed afterwards. */
    private Optional<String> snippetOfKind(
            JdtlsSession session, JdtlsWatch watch, Path path, TypeKind kind, String what) throws InterruptedException {
        var uri = path.toUri().toString();
        var service = session.server().getTextDocumentService();
        var params = new CompletionParams(new TextDocumentIdentifier(uri), new org.eclipse.lsp4j.Position(0, 0));
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "java", 1, "")));
        try {
            return watch
                    .await(what, () -> JdtlsRequests.unlessDeclined(service.completion(params)))
                    .map(completion -> completion.isLeft()
                            ? completion.getLeft()
                            : completion.getRight().getItems())
                    .stream()
                    .flatMap(List::stream)
                    .filter(item -> item.getKind() == CompletionItemKind.Snippet
                            && kind.keyword().equals(item.getLabel()))
                    .map(CompletionItem::getInsertText)
                    .filter(Objects::nonNull)
                    .findFirst();
        } finally {
            close(session, List.of(path));
        }
    }

    /**
     * The source folders of the projects jdtls imported ({@code java.project.listSourcePaths}: a result whose {@code
     * data} lists them, each with its {@code path}).
     *
     * @throws JdtlsException if jdtls answers something else
     */
    private static List<Path> sourceFolders(JdtlsSession session, JdtlsWatch watch) throws InterruptedException {
        var command = new ExecuteCommandParams("java.project.listSourcePaths", List.of());
        Object answer = watch.await(
                "the source folders of the project (java.project.listSourcePaths)",
                () -> session.server().getWorkspaceService().executeCommand(command));
        if (answer instanceof Map<?, ?> result
                && Boolean.TRUE.equals(result.get("status"))
                && result.get("data") instanceof List<?> sources) {
            List<Path> folders = new ArrayList<>();
            for (Object source : sources) {
                if (!(source instanceof Map<?, ?> entry && entry.get("path") instanceof String folder)) {
                    throw notSourceFolders(answer);
                }
                folders.add(normalized(Path.of(folder)));
            }
            return folders;
        }
        throw notSourceFolders(answer);
    }

    private static JdtlsException notSourceFolders(@Nullable Object answer) {
        return new JdtlsException(
                "java.project.listSourcePaths answered " + answer + ", which is not a list of source folders");
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
        Path path = normalized(file);
        String original = textInProject(session, path);
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
                    .map(edit -> WorkspaceEdits.of(edit, JdtlsLanguageServer::textOnDisk))
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
        Path path = normalized(file);
        textInProject(session, path);
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
            List<FileChange> changes = answer.map(edit -> WorkspaceEdits.of(edit, JdtlsLanguageServer::textOnDisk))
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
    private void takeInChangesOnDisk(JdtlsSession session, JdtlsWatch watch) throws InterruptedException {
        untaken.addAll(session.changesOnDisk());
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
    private void prepare(JdtlsSession session, JdtlsWatch watch) throws InterruptedException {
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
    private static void close(JdtlsSession session, Iterable<Path> files) {
        for (Path file : files) {
            session.server()
                    .getTextDocumentService()
                    .didClose(new DidCloseTextDocumentParams(
                            new TextDocumentIdentifier(file.toUri().toString())));
        }
    }

    private JdtlsSession ready() {
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

    /**
     * The text of a file of the project.
     *
     * @throws LanguageServerException if the file is outside the project, does not exist or is a folder
     */
    private static String textInProject(JdtlsSession session, Path file) {
        requireInProject(session, file);
        if (Files.isDirectory(file)) {
            throw LanguageServerException.notAFile(file);
        }
        try {
            return Files.readString(file);
        } catch (NoSuchFileException missing) {
            throw LanguageServerException.fileMissing(file);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void requireInProject(JdtlsSession session, Path file) {
        if (session.projects().stream().noneMatch(file::startsWith)) {
            throw LanguageServerException.fileOutsideProject(file);
        }
    }

    private Map<Path, CompletableFuture<List<org.eclipse.lsp4j.Diagnostic>>> open(
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

    private static Optional<String> textOnDisk(Path file) {
        try {
            return Optional.of(Files.readString(file));
        } catch (NoSuchFileException missing) {
            return Optional.empty();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static Path normalized(Path file) {
        return file.toAbsolutePath().normalize();
    }
}
