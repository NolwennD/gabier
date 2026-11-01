package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.Diagnostic;
import fr.craft.gabier.editing.domain.Diagnostic.Severity;
import fr.craft.gabier.editing.domain.FileChange;
import fr.craft.gabier.editing.domain.JavaLanguageServer;
import fr.craft.gabier.editing.domain.LanguageServerException;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.editing.domain.Range;
import fr.craft.gabier.editing.domain.SourceText;
import fr.craft.gabier.editing.domain.TextEdit;
import fr.craft.gabier.editing.domain.TypeKind;
import fr.craft.gabier.protocol.JdtlsState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.PrepareRenameParams;
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
     * probe the publications of the opened files came within this pause of each other.
     */
    static final Duration QUIET = Duration.ofSeconds(5);

    /**
     * The start of the name of the file of the last event told to jdtls (a batch number follows): it does not exist,
     * see {@link #takeInChangesOnDisk}.
     */
    private static final String BARRIER = ".gabier-changes-taken-";

    private final Diagnostics diagnostics;
    private final Duration quiet;
    private final CompletableFuture<JdtlsSession> starting;
    private final Thread starter;

    /** The changes on disk jdtls has not acknowledged yet: they are told again by the next request. */
    private final List<FileEvent> untaken = new ArrayList<>();

    private int batches;

    private JdtlsLanguageServer(
            Diagnostics diagnostics, Duration quiet, CompletableFuture<JdtlsSession> starting, Thread starter) {
        this.diagnostics = diagnostics;
        this.quiet = quiet;
        this.starting = starting;
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
        Thread starter = Thread.ofPlatform().name("gabier-jdtls-start").daemon().unstarted(() -> {
            try {
                starting.complete(JdtlsSession.start(jdtls, settings, log, diagnostics::publish));
            } catch (InterruptedException stopped) {
                // Only close() interrupts the start, and the requests that wait for it are told why it ended.
                starting.completeExceptionally(new JdtlsException("the daemon is stopping", stopped));
            } catch (Exception failure) {
                starting.completeExceptionally(failure);
            }
        });
        starter.start();
        return new JdtlsLanguageServer(diagnostics, quiet, starting, starter);
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
        SequencedMap<Path, List<Diagnostic>> answers = new LinkedHashMap<>();
        texts.keySet().forEach(file -> answers.put(file, domain(diagnostics.latest(file))));
        return answers;
    }

    /**
     * Asks jdtls whether the symbol can be renamed ({@code prepareRename}), then for the rename, and gives its edit in
     * the terms of the domain ({@link WorkspaceEdits}). jdtls reads the files as it knows them (nothing is opened), after
     * it has been told of the changes on disk. A
     * symbol that jdtls says it cannot rename is no symbol (jdtls answers so for a blank, a keyword, a position past
     * the text), and a name that jdtls declines is invalid for the symbol.
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project, there is no symbol at the
     *     position or jdtls declines the name
     * @throws JdtlsException if jdtls dies or does not answer in time
     */
    @Override
    public synchronized ChangeSet rename(Path file, Position position, String newName) {
        JdtlsSession session = ready();
        Path path = normalized(file);
        textInProject(session, path);
        var document = new TextDocumentIdentifier(path.toUri().toString());
        var at = new org.eclipse.lsp4j.Position(position.line(), position.column());
        var service = session.server().getTextDocumentService();
        JdtlsWatch watch = session.newWatch();
        try {
            takeInChangesOnDisk(session, watch);
            var renamable = watch.await(
                    "whether the symbol at " + position + " of " + path + " can be renamed",
                    () -> JdtlsRequests.unlessDeclined(service.prepareRename(new PrepareRenameParams(document, at))));
            if (renamable.isEmpty()) {
                throw LanguageServerException.noSuchSymbol(path, position);
            }
            var edit = watch.await(
                    "the rename of the symbol at " + position + " of " + path + " to " + newName,
                    () -> JdtlsRequests.unlessDeclined(service.rename(new RenameParams(document, at, newName))));
            List<FileChange> changes = WorkspaceEdits.of(
                    edit.orElseThrow(() -> LanguageServerException.invalidName(path, position, newName)),
                    JdtlsLanguageServer::textOnDisk);
            if (changes.isEmpty()) {
                throw LanguageServerException.noSuchSymbol(path, position);
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
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project, there is no expression to
     *     extract in the selection or jdtls declines the name
     * @throws JdtlsException if jdtls dies, does not answer in time or answers what the adapter cannot apply
     */
    @Override
    public synchronized ChangeSet extractVariable(Path file, Range selection, String name) {
        return extract(Extraction.VARIABLE, file, selection, name);
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
     * The refactorings of {@code java/getRefactorEdit} that introduce a name for the client to rename.
     *
     * @param command the refactoring
     * @param what how the extraction is called in the messages
     * @param refusal the refusal when jdtls has no extraction for the selection
     */
    private enum Extraction {
        VARIABLE(
                "extractVariable",
                "the extraction of the expression",
                "the extracted variable",
                LanguageServerException::notAnExpression),
        METHOD(
                "extractMethod",
                "the extraction of the selection into a method",
                "the extracted method",
                LanguageServerException::notExtractable);

        private final String command;
        private final String what;
        private final String extracted;
        private final BiFunction<Path, Range, LanguageServerException> refusal;

        Extraction(
                String command,
                String what,
                String extracted,
                BiFunction<Path, Range, LanguageServerException> refusal) {
            this.command = command;
            this.what = what;
            this.extracted = extracted;
            this.refusal = refusal;
        }
    }

    /** The extraction that jdtls offers as a command of {@code java/getRefactorEdit}, with the name asked for. */
    private ChangeSet extract(Extraction extraction, Path file, Range selection, String name) {
        String command = extraction.command;
        String what = extraction.what;
        BiFunction<Path, Range, LanguageServerException> refusal = extraction.refusal;
        JdtlsSession session = ready();
        Path path = normalized(file);
        String original = textInProject(session, path);
        var document = new TextDocumentIdentifier(path.toUri().toString());
        var refactoring = new JdtlsServer.RefactorEditParams(
                command, new CodeActionParams(document, lsp(selection), new CodeActionContext(List.of())));
        JdtlsWatch watch = session.newWatch();
        try {
            takeInChangesOnDisk(session, watch);
            var proposal = watch.await(
                    what + " at " + selection + " of " + path,
                    () -> JdtlsRequests.unlessDeclined(session.server().getRefactorEdit(refactoring)));
            var refactor = proposal.orElseThrow(() -> refusal.apply(path, selection));
            // jdtls gives no edit, and says why in errorMessage, for a selection it cannot extract: a statement, a call
            // of a void method.
            var proposed = refactor.edit();
            if (proposed == null) {
                throw refusal.apply(path, selection);
            }
            String extracted = SourceText.edited(path, original, editsOf(path, proposed, original));
            var introduced = introducedName(refactor, extracted);
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

    private static Introduced introducedName(JdtlsServer.RefactorEdit refactor, String extracted) {
        var follow = refactor.command();
        if (follow == null || follow.arguments().isEmpty()) {
            throw new JdtlsException("jdtls extracted the variable but did not say where its name is: " + refactor);
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

    /** Renames the variable on the text jdtls has been given, opened for the time of the request. */
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
                refuseASignatureInUse(session, watch, path, introduced, name, selection);
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
     * compile. The methods are those jdtls lists for the text it was given (a flat list: the client declares no
     * hierarchical symbols), labelled {@code name(parameters) : return type}, under the name of their class.
     */
    private void refuseASignatureInUse(
            JdtlsSession session, JdtlsWatch watch, Path path, Introduced introduced, String name, Range selection)
            throws InterruptedException {
        var listed = watch.await(
                "the methods of the extracted text of " + path,
                () -> session.server()
                        .getTextDocumentService()
                        .documentSymbol(new DocumentSymbolParams(
                                new TextDocumentIdentifier(path.toUri().toString()))));
        List<SymbolInformation> methods = Optional.ofNullable(listed).orElse(List.of()).stream()
                .filter(Either::isLeft)
                .map(Either::getLeft)
                .filter(symbol -> symbol.getKind() == SymbolKind.Method)
                .toList();
        var extracted = methods.stream()
                .filter(method -> method.getName().startsWith(introduced.text() + "("))
                .findFirst()
                .orElseThrow(() -> new JdtlsException("jdtls did not list the method " + introduced.text()
                        + " it extracted in " + path + ": " + methods));
        String wanted = name + parametersOf(extracted);
        if (methods.stream()
                .anyMatch(method -> Objects.equals(method.getContainerName(), extracted.getContainerName())
                        && signatureOf(method).equals(wanted))) {
            throw LanguageServerException.invalidName(path, selection.start(), name);
        }
    }

    /** {@code name(parameters)} of a label {@code name(parameters) : return type}. */
    private static String signatureOf(SymbolInformation method) {
        String label = method.getName();
        int returnType = label.indexOf(" : ");

        return returnType < 0 ? label : label.substring(0, returnType);
    }

    private static String parametersOf(SymbolInformation method) {
        String signature = signatureOf(method);

        return signature.substring(signature.indexOf('('));
    }

    /**
     * Whether the rename only changed the name of the declaration. jdtls does not decline every name it should: for
     * the name of a local variable or parameter in scope it answers an edit all the same, and that edit does not leave
     * the declaration as it was (the space before {@code =} is lost, the end of the next line is cut). A name that
     * jdtls renames into such an edit is not valid for the variable.
     */
    private static boolean leavesTheDeclarationAsItWas(
            String before, String after, Introduced introduced, String name) {
        int start = introduced.offset();
        String restBefore = restOfTheLine(before, start + introduced.text().length());

        return after.startsWith(name, start)
                && restOfTheLine(after, start + name.length()).equals(restBefore);
    }

    private static String restOfTheLine(String text, int from) {
        return text.substring(from).lines().findFirst().orElse("");
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
        var move = new JdtlsServer.MoveParams("moveTypeToNewFile", context, true);
        JdtlsWatch watch = session.newWatch();
        try {
            takeInChangesOnDisk(session, watch);
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
            takeInChangesOnDisk(session, watch);
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
            takeInChangesOnDisk(session, watch);
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
     * preferences of {@link JdtlsSettings#javaPreferences} want them: that is not a refusal, the answer is none.
     *
     * <p>jdtls does not always judge the imports, and gabier does not write what jdtls could not judge. A type that
     * several packages declare and that the file uses without importing it is left out by jdtls, which asks the client
     * to choose ({@code java.action.organizeImports.chooseImports}, enabled by {@link JdtlsCapabilities#extended()}):
     * the request is refused with the candidates. An import that jdtls removes and reports an error on (it cannot
     * resolve it) is refused too, with the import.
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
            takeInChangesOnDisk(session, watch);
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
     * An import that the edit removes and that jdtls reports an error on is one it cannot judge: the request is refused,
     * names it, and nothing is written. A resolved import that nothing uses has a warning, not an error. The
     * diagnostics are asked only when the edit removes an import, because they cost a wait for jdtls to be quiet.
     */
    private void refuseTheImportsJdtlsCannotResolve(Path path, String original, List<FileChange> changes) {
        List<String> before = original.lines().toList();
        String after = SourceText.edited(path, original, editsOnTheFile(path, changes));
        List<String> kept = after.lines().toList();
        Map<Integer, String> removed = new LinkedHashMap<>();
        for (int line = 0; line < before.size(); line++) {
            String text = before.get(line);
            if (text.startsWith("import ") && !kept.contains(text)) {
                removed.put(line, importedName(text));
            }
        }
        if (removed.isEmpty()) {
            return;
        }
        List<String> unresolved = diagnostics(path).stream()
                .filter(found -> found.severity() == Severity.ERROR)
                .map(found -> removed.get(found.position().line()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!unresolved.isEmpty()) {
            throw LanguageServerException.unresolvedImports(path, unresolved);
        }
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
