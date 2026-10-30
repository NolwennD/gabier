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
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;

/** What jdtls publishes about the files the probe opened, kept and judged. No jdtls is started. */
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
    void theErrorsAreListedWithFileLineAndMessage() {
        diagnostics.publish(publicationOf(FILE, error(2, "Type mismatch"), error(6, "Missing semicolon")));
        diagnostics.publish(publicationOf(OTHER, error(0, "Unknown module")));

        assertThat(diagnostics.errors(List.of(FILE, OTHER), ROOT))
                .containsExactly(
                        "modules/a/src/main/java/A.java:3: Type mismatch",
                        "modules/a/src/main/java/A.java:7: Missing semicolon",
                        "modules/b/src/main/java/B.java:1: Unknown module");
    }

    @Test
    void warningsAndHintsAreNotErrors() {
        diagnostics.publish(publicationOf(
                FILE, diagnostic(1, DiagnosticSeverity.Warning, "Unused"), diagnostic(2, null, "No severity")));

        assertThat(diagnostics.errors(List.of(FILE), ROOT)).isEmpty();
    }

    @Test
    void onlyTheLatestPublicationOfAFileCounts() {
        diagnostics.publish(publicationOf(FILE, error(2, "Stale")));
        diagnostics.publish(publicationOf(FILE));

        assertThat(diagnostics.errors(List.of(FILE), ROOT)).isEmpty();
    }

    @Test
    void aFileNeverPublishedHasNoError() {
        assertThat(diagnostics.errors(List.of(FILE), ROOT)).isEmpty();
    }

    @Test
    void theDiagnosticsAreCountedByKindOverTheFilesGiven() {
        diagnostics.publish(publicationOf(
                FILE, error(1, "One"), diagnostic(2, DiagnosticSeverity.Warning, "Two"), error(3, "Three")));
        diagnostics.publish(publicationOf(OTHER, diagnostic(1, DiagnosticSeverity.Warning, "Four")));

        assertThat(diagnostics.count(DiagnosticSeverity.Error, List.of(FILE, OTHER)))
                .isEqualTo(2);
        assertThat(diagnostics.count(DiagnosticSeverity.Warning, List.of(FILE, OTHER)))
                .isEqualTo(2);
        assertThat(diagnostics.count(DiagnosticSeverity.Warning, List.of(OTHER)))
                .isEqualTo(1);
    }

    @Test
    void thePublicationsOfAFileAreCounted() {
        diagnostics.publish(publicationOf(FILE));
        diagnostics.publish(publicationOf(FILE, error(1, "Late")));
        diagnostics.publish(publicationOf(OTHER));

        assertThat(diagnostics.publications(FILE)).isEqualTo(2);
        assertThat(diagnostics.publications(OTHER)).isEqualTo(1);
        assertThat(diagnostics.publications(ROOT.resolve("Unknown.java"))).isZero();
    }

    @Test
    void anErrorIsAwaitedThroughPublicationsWithoutOne() {
        var error = diagnostics.expectError(FILE);

        diagnostics.publish(publicationOf(FILE));
        diagnostics.publish(publicationOf(FILE, diagnostic(1, DiagnosticSeverity.Warning, "Unused")));
        assertThat(error).isNotDone();

        diagnostics.publish(publicationOf(FILE, error(4, "Type mismatch")));
        assertThat(error)
                .isCompletedWithValueMatching(found -> found.size() == 1
                        && found.getFirst().getMessage().getLeft().equals("Type mismatch"));
    }

    @Test
    void anErrorFromBeforeTheExpectationDoesNotCount() {
        diagnostics.publish(publicationOf(FILE, error(4, "Old")));

        assertThat(diagnostics.expectError(FILE)).isNotDone();
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

        assertThat(diagnostics.errors(files, ROOT)).hasSize(200);
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
        var error = diagnostics.expectError(FILE);

        assertThatCode(() -> diagnostics.publish(new PublishDiagnosticsParams(
                        "jdt://contents/java.base/java.lang/String.class?=gabier/%5C/usr%5C/lib%5C/jvm=/String.class",
                        List.of(error(0, "Not ours")))))
                .doesNotThrowAnyException();

        assertThat(publication).isNotDone();
        assertThat(error).isNotDone();
        assertThat(diagnostics.errors(List.of(FILE), ROOT)).isEmpty();
        diagnostics.publish(publicationOf(FILE));
        assertThat(publication).isCompletedWithValue(List.of());
        assertThat(diagnostics.publications(FILE)).isEqualTo(1);
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

    @Test
    void theTimeSinceTheStartIsReadFromTheClock() {
        nanos.set(Duration.ofMillis(1500).toNanos());

        assertThat(diagnostics.sinceStart()).isEqualTo(Duration.ofMillis(1500));
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
