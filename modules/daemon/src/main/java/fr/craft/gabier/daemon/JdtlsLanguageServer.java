package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.Diagnostic;
import fr.craft.gabier.editing.domain.Diagnostic.Severity;
import fr.craft.gabier.editing.domain.FileChange;
import fr.craft.gabier.editing.domain.LanguageServerException;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.protocol.JdtlsState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
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
 * <p>A rename is {@code textDocument/prepareRename} then {@code textDocument/rename}, and the {@code WorkspaceEdit}
 * that jdtls answers is turned into the change set of the domain by {@link RenameChanges}. The refusals of the port
 * are mapped from what jdtls really does (see {@code Scenarios} in the fixtures of the core).
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

    private final Diagnostics diagnostics;
    private final Duration quiet;
    private final CompletableFuture<JdtlsSession> starting;
    private final Thread starter;

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
            case SUCCESS -> JdtlsState.READY;
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
     * the terms of the domain ({@link RenameChanges}). jdtls reads the files as it knows them: nothing is opened. A
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
            var renamable = watch.await(
                    "whether the symbol at " + position + " of " + path + " can be renamed",
                    () -> JdtlsRequests.unlessDeclined(service.prepareRename(new PrepareRenameParams(document, at))));
            if (renamable.isEmpty()) {
                throw LanguageServerException.noSuchSymbol(path, position);
            }
            var edit = watch.await(
                    "the rename of the symbol at " + position + " of " + path + " to " + newName,
                    () -> JdtlsRequests.unlessDeclined(service.rename(new RenameParams(document, at, newName))));
            List<FileChange> changes = RenameChanges.of(
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
            Throwable failure = starting.exceptionNow();
            throw new JdtlsException("jdtls did not start: " + failure.getMessage(), failure);
        }
        return starting.resultNow();
    }

    /** The text of a file of the project; the file is registered to await its publication before it is opened. */
    private static String textInProject(JdtlsSession session, Path file) {
        if (session.projects().stream().noneMatch(file::startsWith)) {
            throw LanguageServerException.fileOutsideProject(file);
        }
        try {
            return Files.readString(file);
        } catch (NoSuchFileException missing) {
            throw LanguageServerException.fileOutsideProject(file);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
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
