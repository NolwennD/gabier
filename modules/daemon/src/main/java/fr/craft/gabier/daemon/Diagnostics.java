package fr.craft.gabier.daemon;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;

/**
 * What jdtls publishes about the files that were opened. The reader thread of LSP4J delivers the publications while the
 * thread that asked waits for them, so every method that touches the state is synchronized. Files are told apart by
 * their path, not by the spelling of the URI jdtls uses.
 */
final class Diagnostics {

    private final Map<Path, CompletableFuture<List<Diagnostic>>> expected = new HashMap<>();
    private final Map<Path, List<Diagnostic>> latest = new HashMap<>();
    private final LongSupplier nanoClock;
    private long lastPublication;

    Diagnostics() {
        this(System::nanoTime);
    }

    /**
     * Starts with no publication.
     *
     * @param nanoClock a monotonic clock in nanoseconds, replaced by a hand-made one in the tests
     */
    Diagnostics(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
        this.lastPublication = nanoClock.getAsLong();
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

    /** Records what the reader thread of LSP4J delivers; a publication for a URI that is not a file is ignored. */
    synchronized void publish(PublishDiagnosticsParams publication) {
        pathOf(publication.getUri()).ifPresent(file -> record(file, publication.getDiagnostics()));
    }

    private void record(Path file, List<Diagnostic> diagnostics) {
        latest.put(file, diagnostics);
        lastPublication = nanoClock.getAsLong();
        var waiting = expected.remove(file);
        if (waiting != null) {
            waiting.complete(diagnostics);
        }
    }

    /**
     * What jdtls published last about a file: jdtls publishes in stages (a quick reconcile, then after the build), so
     * the first publication after the opening is not the last.
     *
     * @return none when jdtls never published for the file
     */
    synchronized List<Diagnostic> latest(Path file) {
        return latest.getOrDefault(file, List.of());
    }

    /**
     * How long jdtls has published nothing for any file, counted from the creation of this object while it has
     * published nothing at all. A publication for a URI that is not a file is ignored, so it does not interrupt the
     * quiet.
     */
    synchronized Duration quietFor() {
        return Duration.ofNanos(nanoClock.getAsLong() - lastPublication);
    }

    /** The message is either a plain string or markup. */
    static String text(Diagnostic diagnostic) {
        var message = diagnostic.getMessage();
        return message.isLeft() ? message.getLeft() : message.getRight().getValue();
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
            // Malformed, or a file URI with a host: not a file of this project either.
            return Optional.empty();
        }
    }
}
