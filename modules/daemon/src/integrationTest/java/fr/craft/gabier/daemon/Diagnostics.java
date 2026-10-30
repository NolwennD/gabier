package fr.craft.gabier.daemon;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.PublishDiagnosticsParams;

/**
 * What jdtls publishes about the files the probe opened. The reader thread of LSP4J delivers the publications while the
 * probe thread waits for them, so every method is synchronized. Files are told apart by their path, not by the spelling
 * of the URI jdtls uses.
 */
final class Diagnostics {

    private final Map<Path, CompletableFuture<List<Diagnostic>>> expected = new HashMap<>();
    private final Map<Path, CompletableFuture<List<Diagnostic>>> expectedErrors = new HashMap<>();
    private final Map<Path, List<Diagnostic>> latest = new HashMap<>();
    private final Map<Path, Integer> publications = new HashMap<>();
    private final LongSupplier nanoClock;
    private final long start;
    private long lastPublication;

    Diagnostics() {
        this(System::nanoTime);
    }

    /** @param nanoClock a monotonic clock in nanoseconds, replaced by a hand-made one in the tests */
    Diagnostics(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
        this.start = nanoClock.getAsLong();
        this.lastPublication = start;
    }

    /**
     * Starts waiting for a file, to be called right before it is opened (see {@link #open}): only a publication that
     * arrives after this call is an answer, one from before the opening says nothing about the file as opened.
     *
     * @return completed with the diagnostics of the first publication after this call; an empty list is an answer,
     *     silence is not
     */
    synchronized CompletableFuture<List<Diagnostic>> expect(Path file) {
        var publication = new CompletableFuture<List<Diagnostic>>();
        expected.put(file, publication);
        return publication;
    }

    /**
     * Registers the wait for a file and, only then, runs what opens it: a publication that arrived earlier, for
     * instance while another file was being opened, says nothing about this file as opened. Registering all the files
     * before opening the first one would let the publications of the workspace build answer for files not yet opened.
     *
     * @param sendDidOpen sends the {@code didOpen} of the file
     * @return as {@link #expect}
     */
    CompletableFuture<List<Diagnostic>> open(Path file, Runnable sendDidOpen) {
        var publication = expect(file);
        sendDidOpen.run();
        return publication;
    }

    /**
     * Starts waiting for an error on a file, like {@link #expect}: only a publication after this call counts.
     *
     * @return completed with the errors of the first publication after this call that has some
     */
    synchronized CompletableFuture<List<Diagnostic>> expectError(Path file) {
        var publication = new CompletableFuture<List<Diagnostic>>();
        expectedErrors.put(file, publication);
        return publication;
    }

    /** Records what the reader thread of LSP4J delivers; a publication for a URI that is not a file is ignored. */
    synchronized void publish(PublishDiagnosticsParams publication) {
        pathOf(publication.getUri()).ifPresent(file -> record(file, publication.getDiagnostics()));
    }

    private void record(Path file, List<Diagnostic> diagnostics) {
        latest.put(file, diagnostics);
        publications.merge(file, 1, Integer::sum);
        lastPublication = nanoClock.getAsLong();
        var waiting = expected.remove(file);
        if (waiting != null) {
            waiting.complete(diagnostics);
        }
        List<Diagnostic> errors =
                diagnostics.stream().filter(Diagnostics::isError).toList();
        if (!errors.isEmpty()) {
            var waitingForError = expectedErrors.remove(file);
            if (waitingForError != null) {
                waitingForError.complete(errors);
            }
        }
    }

    /**
     * How long jdtls has published nothing about a file, counted from the start of this object while it has published
     * nothing at all. A publication for a URI that is not a file is ignored, so it does not interrupt the quiet.
     */
    synchronized Duration quietFor() {
        return Duration.ofNanos(nanoClock.getAsLong() - lastPublication);
    }

    /** The time elapsed since this object was created, to date the publications in the output of the probe. */
    Duration sinceStart() {
        return Duration.ofNanos(nanoClock.getAsLong() - start);
    }

    /** How many publications jdtls made for the file, before and after it was opened. */
    synchronized int publications(Path file) {
        return publications.getOrDefault(file, 0);
    }

    /**
     * The errors of the latest publication of each file, one line each: the file relative to the root, the line (from
     * 1) and the message.
     */
    synchronized List<String> errors(Collection<Path> files, Path root) {
        List<String> errors = new ArrayList<>();
        for (Path file : files) {
            for (Diagnostic diagnostic : latest.getOrDefault(file, List.of())) {
                if (isError(diagnostic)) {
                    int line = diagnostic.getRange().getStart().getLine() + 1;
                    errors.add(root.relativize(file) + ":" + line + ": " + text(diagnostic));
                }
            }
        }
        return errors;
    }

    /** How many diagnostics of this severity the latest publication of each of these files holds. */
    synchronized long count(DiagnosticSeverity severity, Collection<Path> files) {
        return files.stream()
                .flatMap(file -> latest.getOrDefault(file, List.of()).stream())
                .filter(diagnostic -> diagnostic.getSeverity() == severity)
                .count();
    }

    /** The message is either a plain string or markup. */
    static String text(Diagnostic diagnostic) {
        var message = diagnostic.getMessage();
        return message.isLeft() ? message.getLeft() : message.getRight().getValue();
    }

    /** A diagnostic without a severity is not taken for an error. */
    private static boolean isError(Diagnostic diagnostic) {
        return diagnostic.getSeverity() == DiagnosticSeverity.Error;
    }

    /**
     * jdtls may spell the URI of a file differently from {@link Path#toUri()}: the path is the identity.
     *
     * @return empty for a URI that is not a {@code file:} one (jdtls publishes for {@code jdt://} classes of the
     *     JDK too) or is malformed: {@link Path#of(URI)} would throw in the reader thread of LSP4J
     */
    private static Optional<Path> pathOf(String uri) {
        try {
            URI parsed = URI.create(uri);
            if (!"file".equalsIgnoreCase(parsed.getScheme())) {
                return Optional.empty();
            }
            return Optional.of(Path.of(parsed).toAbsolutePath().normalize());
        } catch (IllegalArgumentException e) {
            // Malformed, or a file URI with a host: not a file of this checkout either.
            return Optional.empty();
        }
    }
}
