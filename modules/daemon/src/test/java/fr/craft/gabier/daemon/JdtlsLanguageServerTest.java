package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.gabier.editing.domain.Diagnostic;
import fr.craft.gabier.editing.domain.Diagnostic.Severity;
import fr.craft.gabier.editing.domain.LanguageServerErrorKey;
import fr.craft.gabier.editing.domain.LanguageServerException;
import fr.craft.gabier.editing.domain.Position;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The port {@code JavaLanguageServer} as the jdtls adapter implements it, against {@link FakeJdtls}: a language server
 * in this process, over pipes. No jdtls is started. What the core does with the answers is proven with the fake of the
 * port; here is what the adapter does with jdtls: when it can answer, what it asks, how it reads the answer.
 */
class JdtlsLanguageServerTest {

    private static final Duration LONG = Duration.ofSeconds(30);
    private static final Duration QUIET = Duration.ofMillis(300);

    @TempDir
    Path root;

    private final FakeJdtls fake = new FakeJdtls();
    private final List<String> log = new CopyOnWriteArrayList<>();
    private Path clean;
    private Path broken;

    @BeforeEach
    void writeTheProject() throws IOException {
        // The real path: the temporary folder may be reached through a link, which jdtls does not use.
        root = root.toRealPath();
        clean = write("src/main/java/demo/Clean.java", "class Clean {}\n");
        broken = write("src/main/java/demo/Broken.java", "class Broken { int x = \"s\"; }\n");
        fake.listsProjects(root.toUri().toString());
    }

    @AfterEach
    void leaveNothingBehind() throws Exception {
        Thread.interrupted();
        fake.close();
        if (fake.data() != null && Files.exists(fake.data())) {
            try (Stream<Path> files = Files.walk(fake.data())) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
    }

    private Path write(String relative, String text) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
        return file;
    }

    /** The adapter, once jdtls has been told to initialize: the start is under way. */
    private JdtlsLanguageServer start() {
        return start(LONG);
    }

    private JdtlsLanguageServer start(Duration timeout) {
        var settings = new JdtlsSettings(root, List.of(root), timeout, Duration.ofMillis(20), Duration.ofSeconds(5));
        var server = JdtlsLanguageServer.start(fake, settings, QUIET, log::add);
        fake.initializeParams();
        return server;
    }

    private JdtlsLanguageServer started() throws Exception {
        var server = start();
        server.awaitStarted();
        return server;
    }

    private static org.eclipse.lsp4j.Diagnostic lsp(int line, int column, DiagnosticSeverity severity, String message) {
        return new org.eclipse.lsp4j.Diagnostic(
                new Range(
                        new org.eclipse.lsp4j.Position(line, column), new org.eclipse.lsp4j.Position(line, column + 1)),
                message,
                severity,
                "Java");
    }

    @Nested
    class Answering {

        @Test
        void tellsTheProjectsJdtlsImportedOnceItIsReady() throws Exception {
            try (var server = start()) {
                assertThat(server.awaitStarted()).containsExactly(root);
            }
        }

        @Test
        void answersNoDiagnosticForAFileJdtlsPublishedNothingAbout() throws Exception {
            fake.publishesOnOpen(clean, List.of());

            try (var server = started()) {
                assertThat(server.diagnostics(clean)).isEmpty();
            }
        }

        @Test
        void answersTheDiagnosticsOfAFileInTheTermsOfTheDomain() throws Exception {
            fake.publishesOnOpen(broken, List.of(lsp(0, 20, DiagnosticSeverity.Error, "Type mismatch")));

            try (var server = started()) {
                assertThat(server.diagnostics(broken))
                        .containsExactly(new Diagnostic(Severity.ERROR, new Position(0, 20), "Type mismatch"));
            }
        }

        @ParameterizedTest
        @CsvSource({"Error,ERROR", "Warning,WARNING", "Information,INFORMATION", "Hint,HINT"})
        void mapsEverySeverityOfTheProtocol(DiagnosticSeverity severity, Severity expected) throws Exception {
            fake.publishesOnOpen(broken, List.of(lsp(0, 0, severity, "m")));

            try (var server = started()) {
                assertThat(server.diagnostics(broken))
                        .extracting(Diagnostic::severity)
                        .containsExactly(expected);
            }
        }

        @Test
        void takesADiagnosticWithoutSeverityForInformation() throws Exception {
            fake.publishesOnOpen(broken, List.of(lsp(0, 0, null, "m")));

            try (var server = started()) {
                assertThat(server.diagnostics(broken))
                        .extracting(Diagnostic::severity)
                        .containsExactly(Severity.INFORMATION);
            }
        }

        @Test
        void readsTheTextOfAMarkupMessage() throws Exception {
            var markup = lsp(0, 0, DiagnosticSeverity.Error, "");
            markup.setMessage(Either.forRight(new MarkupContent("markdown", "**bad**")));
            fake.publishesOnOpen(broken, List.of(markup));

            try (var server = started()) {
                assertThat(server.diagnostics(broken))
                        .extracting(Diagnostic::message)
                        .containsExactly("**bad**");
            }
        }

        @Test
        void keepsTheOrderJdtlsGaveThem() throws Exception {
            fake.publishesOnOpen(
                    broken,
                    List.of(
                            lsp(4, 0, DiagnosticSeverity.Warning, "second"),
                            lsp(1, 0, DiagnosticSeverity.Error, "first")));

            try (var server = started()) {
                assertThat(server.diagnostics(broken))
                        .extracting(Diagnostic::message)
                        .containsExactly("second", "first");
            }
        }

        @Test
        void answersTheLatestPublicationOnceJdtlsIsQuiet() throws Exception {
            // A quick reconcile first, then the build says the file is fine.
            fake.publishesOnOpen(broken, List.of(lsp(0, 0, DiagnosticSeverity.Error, "stale")), List.of())
                    .pausesBetweenPublications(Duration.ofMillis(100));

            try (var server = started()) {
                assertThat(server.diagnostics(broken)).isEmpty();
            }
        }

        @Test
        void sendsTheTextOfTheFileAsItIsOnDiskAndClosesItOnceAnswered() throws Exception {
            fake.publishesOnOpen(clean, List.of());

            try (var server = started()) {
                server.diagnostics(clean);

                assertThat(fake.opened())
                        .containsExactly(new FakeJdtls.Opened(clean.toUri().toString(), "class Clean {}\n"));
                assertThat(fake.closed(1)).containsExactly(clean.toUri().toString());
            }
        }

        @Test
        void readsTheFileAgainWhenAskedAgainAfterAnEdit() throws Exception {
            fake.publishesOnOpen(clean, List.of());

            try (var server = started()) {
                server.diagnostics(clean);
                Files.writeString(clean, "class Clean { int edited; }\n");
                server.diagnostics(clean);

                assertThat(fake.opened())
                        .extracting(FakeJdtls.Opened::text)
                        .containsExactly("class Clean {}\n", "class Clean { int edited; }\n");
                assertThat(fake.closed(2)).hasSize(2);
            }
        }

        @Test
        void putsThePathOfTheFileInItsNormalForm() throws Exception {
            fake.publishesOnOpen(clean, List.of());

            try (var server = started()) {
                assertThat(server.diagnostics(root.resolve("src/main/../main/java/demo/./Clean.java")))
                        .isEmpty();
            }
        }

        @Test
        void answersSeveralFilesWithOneWaitForJdtlsToBeQuiet() throws Exception {
            fake.publishesOnOpen(clean, List.of())
                    .publishesOnOpen(broken, List.of(lsp(0, 0, DiagnosticSeverity.Error, "m")));

            try (var server = started()) {
                var answers = server.diagnostics(List.of(clean, broken));

                assertThat(answers.keySet()).containsExactly(clean, broken);
                assertThat(answers.get(clean)).isEmpty();
                assertThat(answers.get(broken)).hasSize(1);
                assertThat(fake.opened()).hasSize(2);
                assertThat(fake.closed(2)).hasSize(2);
            }
        }

        @Test
        void closesTheFilesAlsoWhenJdtlsDoesNotAnswerInTime() throws Exception {
            // No publication is scripted.
            try (var server = start(Duration.ofSeconds(1))) {
                server.awaitStarted();

                assertThatThrownBy(() -> server.diagnostics(clean))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("Timed out")
                        .hasMessageContaining("the diagnostics of " + clean);
                assertThat(fake.closed(1)).containsExactly(clean.toUri().toString());
            }
        }

        @Test
        void keepsTheInterruptionOfTheThreadThatWaits() throws Exception {
            try (var server = started()) {
                Thread.currentThread().interrupt();

                assertThatThrownBy(() -> server.diagnostics(clean))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("Interrupted while waiting for the diagnostics of [" + clean + "]");
                assertThat(Thread.interrupted()).isTrue();
            }
        }
    }

    @Nested
    class Refusing {

        @Test
        void refusesAFileOutsideTheProjectWithoutOpeningIt() throws Exception {
            Path outside = root.resolveSibling("outside").resolve("Elsewhere.java");

            try (var server = started()) {
                assertThatThrownBy(() -> server.diagnostics(outside))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT);
                            assertThat(refusal.parameters()).containsEntry("file", outside.toString());
                        });
                assertThat(fake.opened()).isEmpty();
            }
        }

        @Test
        void refusesAFolderThatOnlyStartsLikeTheProject() throws Exception {
            Path sibling = write("../" + root.getFileName() + "-other/A.java", "class A {}\n");

            try (var server = started()) {
                assertThatThrownBy(() -> server.diagnostics(sibling))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key())
                                        .isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));
            }
        }

        @Test
        void refusesTheWholeBatchWhenOneFileIsOutsideTheProject() throws Exception {
            Path outside = root.resolveSibling("outside").resolve("Elsewhere.java");

            try (var server = started()) {
                assertThatThrownBy(() -> server.diagnostics(List.of(clean, outside)))
                        .isInstanceOf(LanguageServerException.class);
                assertThat(fake.opened()).isEmpty();
            }
        }

        @Test
        void refusesAFileThatDoesNotExistAsNotBeingInTheProject() throws Exception {
            Path missing = root.resolve("src/main/java/demo/Missing.java");

            try (var server = started()) {
                assertThatThrownBy(() -> server.diagnostics(missing))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key())
                                        .isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));
                assertThat(fake.opened()).isEmpty();
            }
        }

        @Test
        void failsWhenTheFileCannotBeReadAtAll() throws Exception {
            Path folder = root.resolve("src/main/java/demo");

            try (var server = started()) {
                assertThatThrownBy(() -> server.diagnostics(folder)).isInstanceOf(UncheckedIOException.class);
            }
        }

        @Test
        void refusesAsNotReadyWhileJdtlsIsStillImporting() throws Exception {
            fake.neverAnswersInitialize();

            try (var server = start()) {
                assertThatThrownBy(() -> server.diagnostics(clean))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
                assertThat(fake.opened()).isEmpty();
            }
        }

        @Test
        void saysWhyJdtlsDidNotStartInsteadOfAnsweringNotReadyForEver() throws Exception {
            fake.sendsStatus("Error");

            try (var server = start()) {
                assertThatThrownBy(server::awaitStarted)
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("jdtls did not start")
                        .hasMessageContaining("jdtls sent the status Error");
                assertThatThrownBy(() -> server.diagnostics(clean))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("jdtls did not start");
            }
        }
    }

    @Nested
    class Closing {

        @Test
        void endsTheSessionOfAJdtlsThatIsReady() throws Exception {
            var server = started();

            server.close();

            assertThat(fake.process().isAlive()).isFalse();
            assertThat(fake.data()).doesNotExist();
        }

        @Test
        void endsTheStartOfAJdtlsThatIsStillImporting() throws Exception {
            fake.neverAnswersInitialize();
            var server = start();

            server.close();

            assertThat(fake.process().isAlive()).isFalse();
            assertThat(fake.data()).doesNotExist();
        }

        @Test
        void closesWithoutFailingAfterAFailedStart() throws Exception {
            fake.sendsStatus("Error");
            var server = start();
            assertThatThrownBy(server::awaitStarted).isInstanceOf(JdtlsException.class);

            server.close();

            assertThat(fake.process().isAlive()).isFalse();
        }
    }

    @Test
    void doesNotRenameYet() throws Exception {
        try (var server = started()) {
            assertThatThrownBy(() -> server.rename(clean, new Position(0, 6), "Other"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("rename");
        }
    }
}
