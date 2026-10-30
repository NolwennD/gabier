package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** What jdtls publishes about the files that were opened, kept for whoever asked. No jdtls is started. */
class DiagnosticsTest {

    private static final Path ROOT = Path.of("/project");
    private static final Path FILE = ROOT.resolve("modules/a/src/main/java/A.java");
    private static final Path OTHER = ROOT.resolve("modules/b/src/main/java/B.java");

    private final AtomicLong nanos = new AtomicLong();
    private final Diagnostics diagnostics = new Diagnostics(nanos::get);

    @Test
    void anEmptyPublicationAfterTheOpeningCountsAsAnAnswer() {
        var publication = diagnostics.expect(FILE);

        diagnostics.publish(publicationOf(FILE));

        assertThat(publication).isCompletedWithValue(List.of());
    }

    @Test
    void silenceIsNotAnAnswer() {
        var publication = diagnostics.expect(FILE);

        diagnostics.publish(publicationOf(OTHER));

        assertThat(publication).isNotDone();
    }

    @Test
    void aPublicationFromBeforeTheOpeningDoesNotCount() {
        diagnostics.publish(publicationOf(FILE));

        var publication = diagnostics.expect(FILE);

        assertThat(publication).isNotDone();
    }

    @Test
    void theUriIsMatchedByItsPathNotByItsSpelling() {
        var publication = diagnostics.expect(FILE);

        diagnostics.publish(
                new PublishDiagnosticsParams("file:///project/modules/a/src/../src/main/java/A.java", List.of()));

        assertThat(publication).isCompleted();
    }

    @Test
    void onlyTheLatestPublicationOfAFileCounts() {
        diagnostics.publish(publicationOf(FILE, error(2, "Stale")));
        diagnostics.publish(publicationOf(FILE));

        assertThat(diagnostics.latest(FILE)).isEmpty();
    }

    @Test
    void theLatestPublicationIsWhatJdtlsPublishedWithoutAnyExpectation() {
        diagnostics.publish(publicationOf(FILE, error(2, "Type mismatch")));

        assertThat(diagnostics.latest(FILE)).extracting(Diagnostics::text).containsExactly("Type mismatch");
    }

    @Test
    void aFileNeverPublishedHasNoLatestPublication() {
        assertThat(diagnostics.latest(FILE)).isEmpty();
    }

    @Test
    void aMarkupMessageIsReadAsItsText() {
        var markup = error(0, "");
        markup.setMessage(Either.forRight(new MarkupContent("markdown", "**bold**")));

        assertThat(Diagnostics.text(markup)).isEqualTo("**bold**");
    }

    @Test
    void publicationsDeliveredByAnotherThreadAreAllKept() throws Exception {
        var files = IntStream.range(0, 200)
                .mapToObj(i -> ROOT.resolve("modules/m/src/main/java/C" + i + ".java"))
                .toList();
        var expected = files.stream().map(diagnostics::expect).toArray(CompletableFuture[]::new);
        var reader = new Thread(() -> files.forEach(file -> diagnostics.publish(publicationOf(file, error(1, "e")))));

        reader.start();
        CompletableFuture.allOf(expected).get(10, TimeUnit.SECONDS);
        reader.join();

        assertThat(files)
                .allSatisfy(file -> assertThat(diagnostics.latest(file)).hasSize(1));
    }

    @Test
    void aPublicationSentWhileAnotherFileIsBeingOpenedDoesNotAnswerForTheNextOne() {
        diagnostics.open(FILE, () -> diagnostics.publish(publicationOf(OTHER)));

        var publication = diagnostics.open(OTHER, () -> {});

        assertThat(publication).isNotDone();
    }

    @Test
    void aPublicationSentOnceTheOpeningIsRegisteredAnswersForTheFile() {
        var publication = diagnostics.open(FILE, () -> diagnostics.publish(publicationOf(FILE, error(1, "Seen"))));

        assertThat(publication).isCompleted();
    }

    @Test
    void theOpeningIsSentAfterTheExpectationIsRegistered() {
        var registered = new ArrayList<Boolean>();

        var publication = diagnostics.open(FILE, () -> {
            diagnostics.publish(publicationOf(FILE));
            registered.add(true);
        });

        assertThat(registered).containsExactly(true);
        assertThat(publication).isCompletedWithValue(List.of());
    }

    @Test
    void aPublicationForAUriThatIsNotAFileIsIgnored() {
        var publication = diagnostics.expect(FILE);

        assertThatCode(() -> diagnostics.publish(new PublishDiagnosticsParams(
                        "jdt://contents/java.base/java.lang/String.class?=gabier/%5C/usr%5C/lib%5C/jvm=/String.class",
                        List.of(error(0, "Not ours")))))
                .doesNotThrowAnyException();

        assertThat(publication).isNotDone();
        assertThat(diagnostics.latest(FILE)).isEmpty();
        diagnostics.publish(publicationOf(FILE));
        assertThat(publication).isCompletedWithValue(List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not a uri at all", "file://some-host/share/File.java"})
    void aPublicationForAMalformedOrRemoteUriIsIgnored(String uri) {
        var publication = diagnostics.expect(FILE);

        assertThatCode(() -> diagnostics.publish(new PublishDiagnosticsParams(uri, List.of(error(0, "Not ours")))))
                .doesNotThrowAnyException();

        assertThat(publication).isNotDone();
        assertThat(diagnostics.latest(FILE)).isEmpty();
    }

    @Test
    void jdtlsIsQuietFromTheStartWhileNothingWasPublished() {
        nanos.set(Duration.ofSeconds(7).toNanos());

        assertThat(diagnostics.quietFor()).isEqualTo(Duration.ofSeconds(7));
    }

    @Test
    void jdtlsIsQuietSinceTheLastPublication() {
        nanos.set(Duration.ofSeconds(3).toNanos());
        diagnostics.publish(publicationOf(FILE));
        nanos.set(Duration.ofSeconds(10).toNanos());

        assertThat(diagnostics.quietFor()).isEqualTo(Duration.ofSeconds(7));

        diagnostics.publish(publicationOf(OTHER));
        nanos.set(Duration.ofSeconds(12).toNanos());

        assertThat(diagnostics.quietFor()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void aPublicationForAUriThatIsNotAFileDoesNotBreakTheQuiet() {
        diagnostics.publish(publicationOf(FILE));
        nanos.set(Duration.ofSeconds(5).toNanos());

        diagnostics.publish(new PublishDiagnosticsParams("jdt://contents/java.base/String.class", List.of()));

        assertThat(diagnostics.quietFor()).isEqualTo(Duration.ofSeconds(5));
    }

    private static Diagnostic error(int line, String message) {
        return diagnostic(line, DiagnosticSeverity.Error, message);
    }

    private static Diagnostic diagnostic(int line, DiagnosticSeverity severity, String message) {
        return new Diagnostic(new Range(new Position(line, 0), new Position(line, 1)), message, severity, "Java");
    }

    private static PublishDiagnosticsParams publicationOf(Path file, Diagnostic... found) {
        return new PublishDiagnosticsParams(file.toUri().toString(), List.of(found));
    }
}
