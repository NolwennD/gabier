package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonPrimitive;
import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.Diagnostic;
import fr.craft.gabier.editing.domain.Diagnostic.Severity;
import fr.craft.gabier.editing.domain.FileChange;
import fr.craft.gabier.editing.domain.LanguageServerErrorKey;
import fr.craft.gabier.editing.domain.LanguageServerException;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.editing.domain.SourceText;
import fr.craft.gabier.protocol.JdtlsState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.SnippetTextEdit;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
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

    /** What jdtls is told of the files edited on disk between two requests, as it asked to be (it registers globs). */
    @Nested
    class ChangesOnDisk {

        private static final String BARRIER = ".gabier-changes-taken-1";
        private static final String SECOND_BARRIER = ".gabier-changes-taken-2";

        private List<String> told() {
            return fake.watched().stream()
                    .flatMap(params -> params.getChanges().stream())
                    .map(change -> change.getType() + " " + root.relativize(Path.of(URI.create(change.getUri()))))
                    .toList();
        }

        @BeforeEach
        void watchTheSources() {
            fake.registersWatchersFor("**/*.java").publishesOnOpen(clean, List.of());
        }

        @Test
        void tellsJdtlsOfAFileEditedSinceTheLastRequestBeforeOpeningIt() throws Exception {
            try (var server = started()) {
                server.diagnostics(clean);
                Files.writeString(clean, "class Clean { int edited; }\n");
                int before = fake.received().size();

                server.diagnostics(clean);

                assertThat(told()).containsExactly("Changed src/main/java/demo/Clean.java", "Deleted " + BARRIER);
                assertThat(fake.received().subList(before, before + 3))
                        .containsExactly("didChangeWatchedFiles", "acknowledged " + BARRIER, "didOpen Clean.java");
            }
        }

        @Test
        void tellsJdtlsOfAFileCreatedAndOfAFileDeleted() throws Exception {
            try (var server = started()) {
                write("src/main/java/demo/Created.java", "class Created {}\n");
                Files.delete(broken);

                server.diagnostics(clean);

                assertThat(told())
                        .containsExactly(
                                "Created src/main/java/demo/Created.java",
                                "Deleted src/main/java/demo/Broken.java",
                                "Deleted " + BARRIER);
            }
        }

        @Test
        void tellsJdtlsOnlyOnceOfAChange() throws Exception {
            try (var server = started()) {
                Files.writeString(clean, "class Clean { int edited; }\n");
                server.diagnostics(clean);

                server.diagnostics(clean);

                assertThat(fake.watched()).hasSize(1);
            }
        }

        @Test
        void tellsJdtlsNothingWhenNothingChanged() throws Exception {
            try (var server = started()) {
                server.diagnostics(clean);

                assertThat(fake.watched()).isEmpty();
            }
        }

        @Test
        void tellsJdtlsOfAFileEditedWhileItImportedWithTheFirstRequest() throws Exception {
            try (var server = start()) {
                Files.writeString(clean, "class Clean { int edited; }\n");
                server.awaitStarted();

                server.diagnostics(clean);

                assertThat(told()).containsExactly("Changed src/main/java/demo/Clean.java", "Deleted " + BARRIER);
            }
        }

        @Test
        void tellsJdtlsNothingOfAFileItDoesNotWatch() throws Exception {
            try (var server = started()) {
                write("notes.txt", "a note\n");

                server.diagnostics(clean);

                assertThat(fake.watched()).isEmpty();
            }
        }

        @Test
        void waitsForJdtlsToTakeTheChangesInBeforeItAsksForTheDiagnostics() throws Exception {
            fake.delaysAcknowledgments(Duration.ofMillis(300));

            try (var server = started()) {
                Files.writeString(clean, "class Clean { int edited; }\n");

                server.diagnostics(clean);

                // jdtls handles the events on a thread of its own: the file is opened once it has acknowledged them.
                assertThat(fake.received())
                        .startsWith("didChangeWatchedFiles", "acknowledged " + BARRIER, "didOpen Clean.java");
            }
        }

        @Test
        void aRetryAfterATimedOutWaitStillWaitsForJdtlsToTakeTheEarlierChangesIn() throws Exception {
            fake.neverAcknowledgesChanges();

            try (var server = start(Duration.ofSeconds(2))) {
                server.awaitStarted();
                Files.writeString(clean, "class Clean { int edited; }\n");
                assertThatThrownBy(() -> server.diagnostics(clean)).hasMessageContaining("Timed out");
                fake.acknowledgesChangesFromNow().delaysAcknowledgments(Duration.ofMillis(300));

                server.diagnostics(clean);

                assertThat(fake.received())
                        .startsWith(
                                "didChangeWatchedFiles",
                                "didChangeWatchedFiles",
                                "acknowledged " + SECOND_BARRIER,
                                "didOpen Clean.java");
                assertThat(fake.watched().getLast().getChanges())
                        .extracting(change ->
                                change.getType() + " " + root.relativize(Path.of(URI.create(change.getUri()))))
                        .containsExactly("Changed src/main/java/demo/Clean.java", "Deleted " + SECOND_BARRIER);
            }
        }

        @Test
        void tellsJdtlsOnlyOnceOfAChangeItHasAcknowledgedEvenAfterATimedOutWait() throws Exception {
            fake.neverAcknowledgesChanges();

            try (var server = start(Duration.ofSeconds(2))) {
                server.awaitStarted();
                Files.writeString(clean, "class Clean { int edited; }\n");
                assertThatThrownBy(() -> server.diagnostics(clean)).hasMessageContaining("Timed out");
                fake.acknowledgesChangesFromNow();
                server.diagnostics(clean);

                server.diagnostics(clean);

                assertThat(fake.watched()).hasSize(2);
            }
        }

        @Test
        void failsWhenJdtlsDoesNotAcknowledgeTheChanges() throws Exception {
            fake.neverAcknowledgesChanges();

            try (var server = start(Duration.ofSeconds(2))) {
                server.awaitStarted();
                Files.writeString(clean, "class Clean { int edited; }\n");

                assertThatThrownBy(() -> server.diagnostics(clean))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("Timed out")
                        .hasMessageContaining("take in the changes on disk");
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
        void saysThatJdtlsDiedWithItsExitCodeWhenAskedAfterItsDeath() throws Exception {
            try (var server = started()) {
                fake.process().destroyForcibly().onExit().get();

                assertThatThrownBy(() -> server.diagnostics(clean))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("jdtls is not running any more")
                        .hasMessageContaining(
                                "it exited with code " + fake.process().exitValue());
                assertThat(fake.opened()).isEmpty();
            }
        }

        @Test
        void passesOnTheRefusalOfAFolderWithNoBuildToImportAsItIs() throws Exception {
            fake.listsOnlyProjects();
            var settings = JdtlsSettings.of(root, List.of(), LONG);

            try (var server = JdtlsLanguageServer.start(fake, settings, QUIET, log::add)) {
                assertThatThrownBy(server::awaitStarted).isInstanceOf(NoBuildToImportException.class);
                assertThatThrownBy(() -> server.diagnostics(clean)).isInstanceOf(NoBuildToImportException.class);
                assertThat(server.state()).isEqualTo(JdtlsState.FAILED);
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
    class WhereItIs {

        @Test
        void isStartingWhileJdtlsImports() throws Exception {
            fake.neverAnswersInitialize();

            try (var server = start()) {
                assertThat(server.state()).isEqualTo(JdtlsState.STARTING);
                assertThat(server.failure()).isEmpty();
            }
        }

        @Test
        void isReadyOnceJdtlsHasImported() throws Exception {
            try (var server = start()) {
                server.awaitReady();

                assertThat(server.state()).isEqualTo(JdtlsState.READY);
                assertThat(server.failure()).isEmpty();
            }
        }

        @Test
        void isFailedWhenJdtlsDiedAfterItWasReady() throws Exception {
            try (var server = started()) {
                fake.process().destroyForcibly().onExit().get();

                assertThat(server.state()).isEqualTo(JdtlsState.FAILED);
            }
        }

        @Test
        void isFailedWhenJdtlsDoesNotStart() throws Exception {
            fake.sendsStatus("Error");

            try (var server = start()) {
                assertThatThrownBy(server::awaitReady).isInstanceOf(JdtlsException.class);

                assertThat(server.state()).isEqualTo(JdtlsState.FAILED);
                assertThat(server.failure())
                        .hasValueSatisfying(reason -> assertThat(reason)
                                .startsWith("jdtls did not start: ")
                                .contains("jdtls sent the status Error"));
            }
        }

        @Test
        void saysWhyJdtlsFailedWhenItDiedAfterItWasReady() throws Exception {
            try (var server = started()) {
                fake.process().destroyForcibly().onExit().get();

                assertThat(server.failure())
                        .contains("jdtls is not running any more: it exited with code "
                                + fake.process().exitValue() + " after it was ready");
            }
        }

        @Test
        void saysThatThereIsNoBuildToImportWhenThereIsNone() throws Exception {
            fake.listsOnlyProjects();

            try (var server =
                    JdtlsLanguageServer.start(fake, JdtlsSettings.of(root, List.of(), LONG), QUIET, log::add)) {
                assertThatThrownBy(server::awaitReady).isInstanceOf(NoBuildToImportException.class);

                assertThat(server.failure())
                        .hasValueSatisfying(reason -> assertThat(reason).contains("no build to import in " + root));
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
        void tellsTheRequestThatWaitsWhenTheDaemonStopsThatItIsStopping() throws Exception {
            fake.neverAnswersInitialize();
            var server = start();

            server.close();

            assertThatThrownBy(server::awaitStarted)
                    .isInstanceOf(JdtlsException.class)
                    .hasMessage("jdtls did not start: the daemon is stopping");
            assertThat(server.state()).isEqualTo(JdtlsState.FAILED);
            assertThat(server.failure()).contains("jdtls did not start: the daemon is stopping");
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

    @Nested
    class Renaming {

        private Path greeter;

        @BeforeEach
        void writeTheSources() throws IOException {
            greeter = write("src/main/java/demo/Greeter.java", "class Greeter {\n    void greet() {}\n}\n");
        }

        private CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> renamable() {
            var range = new Range(new org.eclipse.lsp4j.Position(1, 9), new org.eclipse.lsp4j.Position(1, 14));
            return CompletableFuture.completedFuture(Either3.forFirst(range));
        }

        private WorkspaceEdit edit(TextDocumentEdit... edits) {
            return new WorkspaceEdit(Arrays.stream(edits)
                    .map(Either::<TextDocumentEdit, ResourceOperation>forLeft)
                    .toList());
        }

        private TextDocumentEdit editsOf(Path file, TextEdit... edits) {
            return new TextDocumentEdit(
                    new VersionedTextDocumentIdentifier(file.toUri().toString(), 1),
                    Arrays.stream(edits)
                            .map(Either::<TextEdit, SnippetTextEdit>forLeft)
                            .toList());
        }

        private TextEdit replace(int line, int from, int to, String text) {
            return new TextEdit(
                    new Range(new org.eclipse.lsp4j.Position(line, from), new org.eclipse.lsp4j.Position(line, to)),
                    text);
        }

        private FileChange domainEdits(Path file, fr.craft.gabier.editing.domain.TextEdit... edits) {
            return new FileChange.EditFile(file, List.of(edits));
        }

        private fr.craft.gabier.editing.domain.TextEdit domainReplace(int line, int from, int to, String text) {
            return new fr.craft.gabier.editing.domain.TextEdit(
                    new fr.craft.gabier.editing.domain.Range(new Position(line, from), new Position(line, to)), text);
        }

        @Test
        void asksWhetherTheSymbolCanBeRenamedThenForTheRenameAndAnswersInTheTermsOfTheDomain() throws Exception {
            fake.prepareRenameAnswers(renamable())
                    .renameAnswers(
                            CompletableFuture.completedFuture(edit(editsOf(greeter, replace(1, 9, 14, "hello")))));

            try (var server = started()) {
                ChangeSet changes = server.rename(greeter, new Position(1, 10), "hello");

                assertThat(changes)
                        .isEqualTo(new ChangeSet(List.of(domainEdits(greeter, domainReplace(1, 9, 14, "hello")))));
                assertThat(fake.prepared()).hasSize(1);
                assertThat(fake.prepared().getFirst().getTextDocument().getUri())
                        .isEqualTo(greeter.toUri().toString());
                assertThat(fake.prepared().getFirst().getPosition()).isEqualTo(new org.eclipse.lsp4j.Position(1, 10));
                assertThat(fake.renamed()).hasSize(1);
                assertThat(fake.renamed().getFirst().getNewName()).isEqualTo("hello");
                assertThat(fake.renamed().getFirst().getPosition()).isEqualTo(new org.eclipse.lsp4j.Position(1, 10));
            }
        }

        @Test
        void tellsJdtlsOfTheFilesEditedSinceTheLastRequestBeforeAskingWhetherTheSymbolCanBeRenamed() throws Exception {
            fake.registersWatchersFor("**/*.java")
                    .prepareRenameAnswers(renamable())
                    .renameAnswers(CompletableFuture.completedFuture(edit(editsOf(greeter, replace(1, 9, 14, "hi")))));

            try (var server = started()) {
                Files.writeString(greeter, "class Greeter {\n    void greet() {}\n    int other;\n}\n");

                server.rename(greeter, new Position(1, 10), "hi");

                assertThat(fake.received())
                        .containsExactly(
                                "didChangeWatchedFiles",
                                "acknowledged .gabier-changes-taken-1",
                                "prepareRename Greeter.java",
                                "rename Greeter.java");
                assertThat(fake.watched()).hasSize(1);
                assertThat(fake.watched().getFirst().getChanges())
                        .extracting(change -> change.getType() + " " + change.getUri())
                        .containsExactly(
                                "Changed " + greeter.toUri(),
                                "Deleted "
                                        + root.resolve(".gabier-changes-taken-1")
                                                .toUri());
            }
        }

        @Test
        void givesTheMoveOfAFileAfterTheEditsInTheOrderOfTheAnswer() throws Exception {
            Path welcomer = root.resolve("src/main/java/demo/Welcomer.java");
            var move = new org.eclipse.lsp4j.RenameFile(
                    greeter.toUri().toString(), welcomer.toUri().toString());
            var answer = new WorkspaceEdit(List.of(
                    Either.<TextDocumentEdit, ResourceOperation>forLeft(
                            editsOf(greeter, replace(0, 6, 13, "Welcomer"))),
                    Either.<TextDocumentEdit, ResourceOperation>forRight(move)));
            fake.prepareRenameAnswers(renamable()).renameAnswers(CompletableFuture.completedFuture(answer));

            try (var server = started()) {
                assertThat(server.rename(greeter, new Position(0, 8), "Welcomer")
                                .changes())
                        .containsExactly(
                                domainEdits(greeter, domainReplace(0, 6, 13, "Welcomer")),
                                new FileChange.RenameFile(greeter, welcomer));
            }
        }

        @Test
        void clampsThePositionsOfAnEditToTheTextOfTheFileAsItIsOnDisk() throws Exception {
            // "class Greeter {" has 15 columns.
            fake.prepareRenameAnswers(renamable())
                    .renameAnswers(
                            CompletableFuture.completedFuture(edit(editsOf(greeter, replace(0, 6, 99, "hello")))));

            try (var server = started()) {
                assertThat(server.rename(greeter, new Position(0, 8), "hello").changes())
                        .containsExactly(domainEdits(greeter, domainReplace(0, 6, 15, "hello")));
            }
        }

        @Test
        void givesTheEditsOfAFileThatIsNotOnDiskAsTheyAre() throws Exception {
            Path created = root.resolve("src/main/java/demo/Created.java");
            fake.prepareRenameAnswers(renamable())
                    .renameAnswers(
                            CompletableFuture.completedFuture(edit(editsOf(created, replace(0, 6, 99, "hello")))));

            try (var server = started()) {
                assertThat(server.rename(greeter, new Position(0, 8), "hello").changes())
                        .containsExactly(domainEdits(created, domainReplace(0, 6, 99, "hello")));
            }
        }

        @Test
        void failsWhenTheFileOfAnEditCannotBeRead() throws Exception {
            Path folder = root.resolve("src/main/java/demo");
            fake.prepareRenameAnswers(renamable())
                    .renameAnswers(CompletableFuture.completedFuture(edit(editsOf(folder, replace(0, 6, 9, "hello")))));

            try (var server = started()) {
                assertThatThrownBy(() -> server.rename(greeter, new Position(0, 8), "hello"))
                        .isInstanceOf(UncheckedIOException.class);
            }
        }

        @Test
        void putsThePathOfTheFileInItsNormalFormInTheRequestAndTheRefusals() throws Exception {
            fake.prepareRenameAnswers(CompletableFuture.completedFuture(null));
            var spelled = root.resolve("src/main/../main/java/demo/./Greeter.java");

            try (var server = started()) {
                assertThatThrownBy(() -> server.rename(spelled, new Position(1, 10), "hello"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.parameters()).containsEntry("file", greeter.toString()));
                assertThat(fake.prepared().getFirst().getTextDocument().getUri())
                        .isEqualTo(greeter.toUri().toString());
            }
        }

        @Test
        void refusesWhereJdtlsHasNothingToRenameAsNoSymbol() throws Exception {
            fake.prepareRenameAnswers(CompletableFuture.completedFuture(null));

            try (var server = started()) {
                assertThatThrownBy(() -> server.rename(greeter, new Position(2, 0), "hello"))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NO_SUCH_SYMBOL);
                            assertThat(refusal.parameters())
                                    .containsEntry("file", greeter.toString())
                                    .containsEntry("line", "2")
                                    .containsEntry("column", "0");
                        });
                assertThat(fake.renamed()).isEmpty();
            }
        }

        @Test
        void refusesWhereJdtlsSaysTheElementCannotBeRenamedAsNoSymbolWithoutAskingForTheRename() throws Exception {
            fake.prepareRenameAnswers(declined("Renaming this element is not supported."));

            try (var server = started()) {
                assertThatThrownBy(() -> server.rename(greeter, new Position(2, 0), "hello"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NO_SUCH_SYMBOL));
                assertThat(fake.renamed()).isEmpty();
            }
        }

        @Test
        void refusesAnInvalidNameWhichJdtlsDeclinesAsAnInvalidName() throws Exception {
            fake.prepareRenameAnswers(renamable()).renameAnswers(declined("'1st' is not a valid Java identifier"));

            try (var server = started()) {
                assertThatThrownBy(() -> server.rename(greeter, new Position(1, 10), "1st"))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.INVALID_NAME);
                            assertThat(refusal.parameters())
                                    .containsEntry("name", "1st")
                                    .containsEntry("file", greeter.toString())
                                    .containsEntry("line", "1")
                                    .containsEntry("column", "10");
                        });
            }
        }

        @Test
        void takesTheAbsenceOfAnEditForAnInvalidName() throws Exception {
            fake.prepareRenameAnswers(renamable()).renameAnswers(CompletableFuture.completedFuture(null));

            try (var server = started()) {
                assertThatThrownBy(() -> server.rename(greeter, new Position(1, 10), "hello"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.INVALID_NAME));
            }
        }

        @Test
        void refusesAnEditThatChangesNothingAsNoSymbol() throws Exception {
            fake.prepareRenameAnswers(renamable())
                    .renameAnswers(CompletableFuture.completedFuture(edit(editsOf(greeter))));

            try (var server = started()) {
                assertThatThrownBy(() -> server.rename(greeter, new Position(1, 10), "hello"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NO_SUCH_SYMBOL));
            }
        }

        @Test
        void saysWhatWentWrongWhenJdtlsFailsInsteadOfDeclining() throws Exception {
            var failure = new CompletableFuture<WorkspaceEdit>();
            failure.completeExceptionally(new IllegalStateException("the model is broken"));
            fake.prepareRenameAnswers(renamable()).renameAnswers(failure);

            try (var server = started()) {
                assertThatThrownBy(() -> server.rename(greeter, new Position(1, 10), "hello"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("waiting for the rename of the symbol at")
                        .hasMessageContaining("Internal error");
            }
        }

        @Test
        void refusesAFileOutsideTheProjectWithoutAskingJdtls() throws Exception {
            Path outside = root.resolveSibling("outside").resolve("Elsewhere.java");

            try (var server = started()) {
                assertThatThrownBy(() -> server.rename(outside, new Position(0, 0), "hello"))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT);
                            assertThat(refusal.parameters()).containsEntry("file", outside.toString());
                        });
                assertThat(fake.prepared()).isEmpty();
            }
        }

        @Test
        void refusesAsNotReadyWhileJdtlsIsStillImporting() throws Exception {
            fake.neverAnswersInitialize();

            try (var server = start()) {
                assertThatThrownBy(() -> server.rename(greeter, new Position(1, 10), "hello"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
                assertThat(fake.prepared()).isEmpty();
            }
        }

        @Test
        void failsWhenJdtlsDoesNotAnswerInTime() throws Exception {
            // No answer is scripted.
            try (var server = start(Duration.ofSeconds(1))) {
                server.awaitStarted();

                assertThatThrownBy(() -> server.rename(greeter, new Position(1, 10), "hello"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("Timed out")
                        .hasMessageContaining("can be renamed");
            }
        }

        @Test
        void keepsTheInterruptionOfTheThreadThatWaits() throws Exception {
            fake.prepareRenameAnswers(renamable()).renameAnswers(CompletableFuture.completedFuture(edit()));

            try (var server = started()) {
                Thread.currentThread().interrupt();

                assertThatThrownBy(() -> server.rename(greeter, new Position(1, 10), "hello"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("Interrupted while waiting for the rename");
                assertThat(Thread.interrupted()).isTrue();
            }
        }

        private <T> CompletableFuture<T> declined(String message) {
            return CompletableFuture.failedFuture(
                    new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidRequest, message, null)));
        }
    }

    @Nested
    class OrganizingImports {

        private Path greeter;

        @BeforeEach
        void writeTheSources() throws IOException {
            greeter = write(
                    "src/main/java/demo/Greeter.java",
                    "package demo;\n\n"
                            + "import java.util.Set;\n"
                            + "import java.util.List;\n\n"
                            + "class Greeter {\n"
                            + "    List<String> names;\n"
                            + "}\n");
        }

        private CompletableFuture<List<Either<Command, CodeAction>>> offers(CodeAction action) {
            return CompletableFuture.completedFuture(List.of(Either.forRight(action)));
        }

        /** What jdtls offers first: the action, with its data and no edit yet. */
        private CodeAction lazyAction() {
            var action = new CodeAction("Organize imports");
            action.setKind(CodeActionKind.SourceOrganizeImports);
            action.setData("jdtls-data");
            return action;
        }

        /** The action once resolved: its edit is given as {@code changes}, as jdtls gives it. */
        private CodeAction resolvedAction(TextEdit... edits) {
            var action = lazyAction();
            action.setEdit(new WorkspaceEdit(Map.of(greeter.toUri().toString(), List.of(edits))));
            return action;
        }

        private TextEdit removeSet() {
            return new TextEdit(
                    new Range(new org.eclipse.lsp4j.Position(2, 0), new org.eclipse.lsp4j.Position(3, 0)), "");
        }

        private ChangeSet removalOfSet() {
            return new ChangeSet(List.of(new FileChange.EditFile(
                    greeter,
                    List.of(new fr.craft.gabier.editing.domain.TextEdit(
                            new fr.craft.gabier.editing.domain.Range(new Position(2, 0), new Position(3, 0)), "")))));
        }

        @Test
        void asksForTheOrganizeImportsActionOfTheFileAloneThenResolvesItAndAnswersInTheTermsOfTheDomain()
                throws Exception {
            fake.codeActionAnswers(offers(lazyAction()))
                    .resolveAnswers(CompletableFuture.completedFuture(resolvedAction(removeSet())));

            try (var server = started()) {
                assertThat(server.organizeImports(greeter)).contains(removalOfSet());

                assertThat(fake.codeActionRequests()).hasSize(1);
                CodeActionParams request = fake.codeActionRequests().getFirst();
                assertThat(request.getTextDocument().getUri())
                        .isEqualTo(greeter.toUri().toString());
                assertThat(request.getContext().getOnly()).containsExactly("source.organizeImports");
                assertThat(request.getContext().getDiagnostics()).isEmpty();
                assertThat(fake.resolveRequests()).hasSize(1);
                assertThat(fake.resolveRequests().getFirst().getData()).isEqualTo(new JsonPrimitive("jdtls-data"));
                assertThat(fake.received()).containsExactly("codeAction Greeter.java", "resolveCodeAction");
            }
        }

        @Test
        void usesTheEditOfTheActionWhenJdtlsGivesItWithoutBeingAskedToResolve() throws Exception {
            fake.codeActionAnswers(offers(resolvedAction(removeSet())));

            try (var server = started()) {
                assertThat(server.organizeImports(greeter)).contains(removalOfSet());
                assertThat(fake.resolveRequests()).isEmpty();
            }
        }

        @Test
        void answersNothingWhenTheResolvedActionHasNoEdit() throws Exception {
            // What jdtls answers for a file whose imports are as they should be.
            fake.codeActionAnswers(offers(lazyAction()))
                    .resolveAnswers(CompletableFuture.completedFuture(lazyAction()));

            try (var server = started()) {
                assertThat(server.organizeImports(greeter)).isEmpty();
            }
        }

        @Test
        void answersNothingWhenTheEditChangesNothing() throws Exception {
            fake.codeActionAnswers(offers(resolvedAction()));

            try (var server = started()) {
                assertThat(server.organizeImports(greeter)).isEmpty();
            }
        }

        @Test
        void answersNothingWhenJdtlsResolvesNothing() throws Exception {
            fake.codeActionAnswers(offers(lazyAction())).resolveAnswers(CompletableFuture.completedFuture(null));

            try (var server = started()) {
                assertThat(server.organizeImports(greeter)).isEmpty();
            }
        }

        @Test
        void answersNothingWhenJdtlsOffersNoAction() throws Exception {
            fake.codeActionAnswers(CompletableFuture.completedFuture(List.of()));

            try (var server = started()) {
                assertThat(server.organizeImports(greeter)).isEmpty();
                assertThat(fake.resolveRequests()).isEmpty();
            }
        }

        @Test
        void answersNothingWhenJdtlsOffersNothingAtAll() throws Exception {
            fake.codeActionAnswers(CompletableFuture.completedFuture(null));

            try (var server = started()) {
                assertThat(server.organizeImports(greeter)).isEmpty();
            }
        }

        @Test
        void answersNothingWhenJdtlsOffersACommandInsteadOfAnAction() throws Exception {
            fake.codeActionAnswers(CompletableFuture.completedFuture(
                    List.of(Either.forLeft(new Command("Organize imports", "java.edit.organizeImports")))));

            try (var server = started()) {
                assertThat(server.organizeImports(greeter)).isEmpty();
                assertThat(fake.resolveRequests()).isEmpty();
            }
        }

        @Test
        void answersNothingWhenJdtlsDeclinesTheRequest() throws Exception {
            fake.codeActionAnswers(declined("nothing to organize"));

            try (var server = started()) {
                assertThat(server.organizeImports(greeter)).isEmpty();
            }
        }

        @Test
        void clampsThePositionsOfTheEditToTheTextOfTheFileAsItIsOnDisk() throws Exception {
            var past = new TextEdit(
                    new Range(new org.eclipse.lsp4j.Position(2, 0), new org.eclipse.lsp4j.Position(99, 0)), "");
            fake.codeActionAnswers(offers(resolvedAction(past)));

            try (var server = started()) {
                // The text ends with a newline: its last line, the empty one, is line 8.
                assertThat(server.organizeImports(greeter))
                        .contains(new ChangeSet(List.of(new FileChange.EditFile(
                                greeter,
                                List.of(new fr.craft.gabier.editing.domain.TextEdit(
                                        new fr.craft.gabier.editing.domain.Range(
                                                new Position(2, 0), new Position(8, 0)),
                                        ""))))));
            }
        }

        @Test
        void tellsJdtlsOfTheFilesEditedSinceTheLastRequestBeforeAskingForTheAction() throws Exception {
            fake.registersWatchersFor("**/*.java").codeActionAnswers(offers(resolvedAction(removeSet())));

            try (var server = started()) {
                Files.writeString(greeter, Files.readString(greeter) + "// edited\n");

                server.organizeImports(greeter);

                assertThat(fake.received())
                        .containsExactly(
                                "didChangeWatchedFiles",
                                "acknowledged .gabier-changes-taken-1",
                                "codeAction Greeter.java");
                assertThat(fake.watched().getFirst().getChanges())
                        .extracting(change -> change.getType() + " " + change.getUri())
                        .containsExactly(
                                "Changed " + greeter.toUri(),
                                "Deleted "
                                        + root.resolve(".gabier-changes-taken-1")
                                                .toUri());
            }
        }

        @Test
        void opensNothing() throws Exception {
            fake.codeActionAnswers(offers(resolvedAction(removeSet())));

            try (var server = started()) {
                server.organizeImports(greeter);

                assertThat(fake.opened()).isEmpty();
            }
        }

        @Test
        void putsThePathOfTheFileInItsNormalFormInTheRequest() throws Exception {
            fake.codeActionAnswers(offers(resolvedAction(removeSet())));
            var spelled = root.resolve("src/main/../main/java/demo/./Greeter.java");

            try (var server = started()) {
                server.organizeImports(spelled);

                assertThat(fake.codeActionRequests()
                                .getFirst()
                                .getTextDocument()
                                .getUri())
                        .isEqualTo(greeter.toUri().toString());
            }
        }

        @Test
        void refusesAFileOutsideTheProjectWithoutAskingJdtls() throws Exception {
            Path outside = root.resolveSibling("outside").resolve("Elsewhere.java");

            try (var server = started()) {
                assertThatThrownBy(() -> server.organizeImports(outside))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT);
                            assertThat(refusal.parameters()).containsEntry("file", outside.toString());
                        });
                assertThat(fake.codeActionRequests()).isEmpty();
            }
        }

        @Test
        void refusesAsNotReadyWhileJdtlsIsStillImporting() throws Exception {
            fake.neverAnswersInitialize();

            try (var server = start()) {
                assertThatThrownBy(() -> server.organizeImports(greeter))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
                assertThat(fake.codeActionRequests()).isEmpty();
            }
        }

        @Test
        void saysWhatWentWrongWhenJdtlsFailsInsteadOfDeclining() throws Exception {
            var failure = new CompletableFuture<List<Either<Command, CodeAction>>>();
            failure.completeExceptionally(new IllegalStateException("the model is broken"));
            fake.codeActionAnswers(failure);

            try (var server = started()) {
                assertThatThrownBy(() -> server.organizeImports(greeter))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("waiting for the organization of the imports of " + greeter)
                        .hasMessageContaining("Internal error");
            }
        }

        @Test
        void failsWhenJdtlsDoesNotAnswerInTime() throws Exception {
            // No answer is scripted.
            try (var server = start(Duration.ofSeconds(1))) {
                server.awaitStarted();

                assertThatThrownBy(() -> server.organizeImports(greeter))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("Timed out")
                        .hasMessageContaining("the organization of the imports");
            }
        }

        @Test
        void failsWhenJdtlsDoesNotResolveTheActionInTime() throws Exception {
            fake.codeActionAnswers(offers(lazyAction()));

            try (var server = start(Duration.ofSeconds(1))) {
                server.awaitStarted();

                assertThatThrownBy(() -> server.organizeImports(greeter))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("Timed out")
                        .hasMessageContaining("the edit of the organization of the imports");
            }
        }

        @Test
        void keepsTheInterruptionOfTheThreadThatWaits() throws Exception {
            fake.codeActionAnswers(offers(resolvedAction(removeSet())));

            try (var server = started()) {
                Thread.currentThread().interrupt();

                assertThatThrownBy(() -> server.organizeImports(greeter))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("Interrupted while waiting for the organization of the imports");
                assertThat(Thread.interrupted()).isTrue();
            }
        }

        private <T> CompletableFuture<T> declined(String message) {
            return CompletableFuture.failedFuture(
                    new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidRequest, message, null)));
        }
    }

    /**
     * The extraction of a variable: jdtls proposes the variable with a name of its own and says where it is, the adapter
     * has the variable renamed to the name asked for on the text the edit gives, and answers one edit on the file as it
     * is on disk.
     */
    @Nested
    class ExtractingAVariable {

        private static final String ORIGINAL = """
            class Greeter {
                String greet(String name) {
                    return "Hello, " + name;
                }
            }
            """;

        /** The text once jdtls' edit is applied: the variable carries the name jdtls proposed. */
        private static final String PROPOSED = """
            class Greeter {
                String greet(String name) {
                    String string = "Hello, " + name;
                    return string;
                }
            }
            """;

        private static final String NAMED = """
            class Greeter {
                String greet(String name) {
                    String text = "Hello, " + name;
                    return text;
                }
            }
            """;

        private final fr.craft.gabier.editing.domain.Range selection =
                new fr.craft.gabier.editing.domain.Range(new Position(2, 15), new Position(2, 31));
        private Path greeter;

        @BeforeEach
        void writeTheSources() throws IOException {
            greeter = write("src/main/java/demo/Greeter.java", ORIGINAL);
        }

        private CompletableFuture<JdtlsServer.RefactorEdit> proposal(WorkspaceEdit edit) {
            var where = new JdtlsServer.RenamePosition(
                    greeter.toUri().toString(), PROPOSED.indexOf("string"), "string".length());
            var follow = new JdtlsServer.FollowUp("java.action.rename", List.of(where));

            return CompletableFuture.completedFuture(new JdtlsServer.RefactorEdit(edit, follow, null));
        }

        private static org.eclipse.lsp4j.Position at(int line, int column) {
            return new org.eclipse.lsp4j.Position(line, column);
        }

        private static WorkspaceEdit edit(TextDocumentEdit... edits) {
            return new WorkspaceEdit(Arrays.stream(edits)
                    .map(Either::<TextDocumentEdit, ResourceOperation>forLeft)
                    .toList());
        }

        private static TextDocumentEdit editsOf(Path file, TextEdit... edits) {
            return new TextDocumentEdit(
                    new VersionedTextDocumentIdentifier(file.toUri().toString(), 1),
                    Arrays.stream(edits)
                            .map(Either::<TextEdit, SnippetTextEdit>forLeft)
                            .toList());
        }

        /** What jdtls answers: the declaration is inserted before the statement, the expression is replaced. */
        private WorkspaceEdit declaresString() {
            return edit(editsOf(
                    greeter,
                    new TextEdit(new Range(at(2, 8), at(2, 8)), "String string = \"Hello, \" + name;\n        "),
                    new TextEdit(new Range(at(2, 15), at(2, 31)), "string")));
        }

        /** What jdtls answers to the rename, for the text it was given. */
        private WorkspaceEdit renamesToText() {
            return edit(editsOf(
                    greeter,
                    new TextEdit(new Range(at(2, 15), at(2, 21)), "text"),
                    new TextEdit(new Range(at(3, 15), at(3, 21)), "text")));
        }

        private WorkspaceEdit editsAnotherFile() throws IOException {
            Path other = write("src/main/java/demo/Other.java", "class Other {}\n");

            return edit(editsOf(other, new TextEdit(new Range(at(0, 0), at(0, 0)), "// x\n")));
        }

        /** jdtls proposes the variable, and publishes for the text it is given. */
        private void scriptTheExtraction() {
            fake.refactorEditAnswers(proposal(declaresString()))
                    .publishesOnOpen(greeter, List.of())
                    .renameAnswers(CompletableFuture.completedFuture(renamesToText()));
        }

        private static String applied(String text, ChangeSet changes) {
            var edit = (FileChange.EditFile) changes.changes().getFirst();

            return SourceText.edited(edit.file(), text, edit.edits());
        }

        private <T> CompletableFuture<T> declined() {
            return CompletableFuture.failedFuture(new ResponseErrorException(
                    new ResponseError(ResponseErrorCode.InvalidRequest, "Cannot do it here.", null)));
        }

        @Test
        void asksJdtlsForTheExtractionOfTheSelectionAsGiven() throws Exception {
            scriptTheExtraction();

            try (var server = started()) {
                server.extractVariable(greeter, selection, "text");

                assertThat(fake.refactorEditRequests()).hasSize(1);
                var request = fake.refactorEditRequests().getFirst();
                assertThat(request.command()).isEqualTo("extractVariable");
                assertThat(request.context().getTextDocument().getUri())
                        .isEqualTo(greeter.toUri().toString());
                assertThat(request.context().getRange()).isEqualTo(new Range(at(2, 15), at(2, 31)));
                assertThat(request.context().getContext().getDiagnostics()).isEmpty();
            }
        }

        @Test
        void renamesTheVariableToTheNameAskedForOnTheTextThatJdtlsGaveNotOnTheOneOnDisk() throws Exception {
            scriptTheExtraction();

            try (var server = started()) {
                server.extractVariable(greeter, selection, "text");

                assertThat(fake.opened())
                        .containsExactly(new FakeJdtls.Opened(greeter.toUri().toString(), PROPOSED));
                assertThat(fake.renamed()).hasSize(1);
                assertThat(fake.renamed().getFirst().getTextDocument().getUri())
                        .isEqualTo(greeter.toUri().toString());
                assertThat(fake.renamed().getFirst().getPosition()).isEqualTo(at(2, 15));
                assertThat(fake.renamed().getFirst().getNewName()).isEqualTo("text");
                assertThat(fake.received())
                        .containsExactly("getRefactorEdit Greeter.java", "didOpen Greeter.java", "rename Greeter.java");
            }
        }

        @Test
        void closesTheTextItOpenedAndWritesNothing() throws Exception {
            scriptTheExtraction();

            try (var server = started()) {
                server.extractVariable(greeter, selection, "text");

                assertThat(fake.closed(1)).containsExactly(greeter.toUri().toString());
                assertThat(greeter).hasContent(ORIGINAL);
            }
        }

        @Test
        void answersOneEditOfTheFileAsItIsOnDiskWithTheNameAskedFor() throws Exception {
            scriptTheExtraction();

            try (var server = started()) {
                ChangeSet changes = server.extractVariable(greeter, selection, "text");

                var edit = new fr.craft.gabier.editing.domain.TextEdit(
                        new fr.craft.gabier.editing.domain.Range(new Position(2, 8), new Position(2, 31)),
                        "String text = \"Hello, \" + name;\n        return text");
                assertThat(changes).isEqualTo(new ChangeSet(List.of(new FileChange.EditFile(greeter, List.of(edit)))));
                assertThat(applied(ORIGINAL, changes)).isEqualTo(NAMED);
            }
        }

        @Test
        void keepsTheNameJdtlsProposedWhenThatIsTheNameAskedForWithoutRenamingAnything() throws Exception {
            fake.refactorEditAnswers(proposal(declaresString()));

            try (var server = started()) {
                ChangeSet changes = server.extractVariable(greeter, selection, "string");

                assertThat(applied(ORIGINAL, changes)).isEqualTo(PROPOSED);
                assertThat(fake.opened()).isEmpty();
                assertThat(fake.renamed()).isEmpty();
            }
        }

        @Test
        void refusesASelectionJdtlsHasNoExtractionFor() throws Exception {
            fake.refactorEditAnswers(CompletableFuture.completedFuture(null));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractVariable(greeter, selection, "text"))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_AN_EXPRESSION);
                            assertThat(refusal.parameters())
                                    .containsEntry("file", greeter.toString())
                                    .containsEntry("line", "2")
                                    .containsEntry("column", "15")
                                    .containsEntry("endLine", "2")
                                    .containsEntry("endColumn", "31");
                        });
                assertThat(fake.opened()).isEmpty();
            }
        }

        @Test
        void refusesASelectionJdtlsDeclines() throws Exception {
            fake.refactorEditAnswers(declined());

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractVariable(greeter, selection, "text"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal ->
                                        assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_AN_EXPRESSION));
            }
        }

        @Test
        void refusesANameJdtlsDeclinesForTheVariableAndClosesTheText() throws Exception {
            fake.refactorEditAnswers(proposal(declaresString()))
                    .publishesOnOpen(greeter, List.of())
                    .renameAnswers(declined());

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractVariable(greeter, selection, "1st"))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.INVALID_NAME);
                            assertThat(refusal.parameters())
                                    .containsEntry("name", "1st")
                                    .containsEntry("line", "2")
                                    .containsEntry("column", "15");
                        });
                assertThat(fake.closed(1)).containsExactly(greeter.toUri().toString());
                assertThat(greeter).hasContent(ORIGINAL);
            }
        }

        @Test
        void takesTheAbsenceOfAnEditForTheRenameForAnInvalidName() throws Exception {
            fake.refactorEditAnswers(proposal(declaresString()))
                    .publishesOnOpen(greeter, List.of())
                    .renameAnswers(CompletableFuture.completedFuture(null));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractVariable(greeter, selection, "1st"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.INVALID_NAME));
            }
        }

        @Test
        void failsWhenJdtlsGivesNoEdit() throws Exception {
            var where = new JdtlsServer.RenamePosition(greeter.toUri().toString(), 0, 1);
            var follow = new JdtlsServer.FollowUp("java.action.rename", List.of(where));
            fake.refactorEditAnswers(
                    CompletableFuture.completedFuture(new JdtlsServer.RefactorEdit(null, follow, "nothing to do")));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractVariable(greeter, selection, "text"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("without an edit")
                        .hasMessageContaining("nothing to do");
            }
        }

        @Test
        void failsWhenJdtlsDoesNotSayWhereTheNameIs() throws Exception {
            fake.refactorEditAnswers(
                    CompletableFuture.completedFuture(new JdtlsServer.RefactorEdit(declaresString(), null, null)));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractVariable(greeter, selection, "text"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("did not say where its name is");
            }
        }

        @Test
        void failsWhenTheCommandOfJdtlsHasNoPosition() throws Exception {
            var follow = new JdtlsServer.FollowUp("java.action.rename", List.of());
            fake.refactorEditAnswers(
                    CompletableFuture.completedFuture(new JdtlsServer.RefactorEdit(declaresString(), follow, null)));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractVariable(greeter, selection, "text"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("did not say where its name is");
            }
        }

        @Test
        void failsWhenTheExtractionEditsAnotherFile() throws Exception {
            fake.refactorEditAnswers(proposal(editsAnotherFile()));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractVariable(greeter, selection, "text"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("does more than edit " + greeter);
            }
        }

        @Test
        void failsWhenTheExtractionDoesMoreThanEditTheFile() throws Exception {
            var create = new org.eclipse.lsp4j.CreateFile(
                    root.resolve("New.java").toUri().toString());
            fake.refactorEditAnswers(proposal(new WorkspaceEdit(List.of(Either.forRight(create)))));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractVariable(greeter, selection, "text"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("does more than edit " + greeter);
            }
        }

        @Test
        void failsWhenTheRenameEditsAnotherFile() throws Exception {
            fake.refactorEditAnswers(proposal(declaresString()))
                    .publishesOnOpen(greeter, List.of())
                    .renameAnswers(CompletableFuture.completedFuture(editsAnotherFile()));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractVariable(greeter, selection, "text"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("does more than edit " + greeter);
                assertThat(fake.closed(1)).containsExactly(greeter.toUri().toString());
            }
        }

        @Test
        void clampsThePositionsOfTheRenameToTheTextItIsAbout() throws Exception {
            // The edit of the second line ends past it, as the protocol allows: "        return string;" has 22
            // columns.
            var edit = edit(editsOf(
                    greeter,
                    new TextEdit(new Range(at(2, 15), at(2, 21)), "text"),
                    new TextEdit(new Range(at(3, 15), at(3, 99)), "text;")));
            fake.refactorEditAnswers(proposal(declaresString()))
                    .publishesOnOpen(greeter, List.of())
                    .renameAnswers(CompletableFuture.completedFuture(edit));

            try (var server = started()) {
                ChangeSet changes = server.extractVariable(greeter, selection, "text");

                assertThat(applied(ORIGINAL, changes)).isEqualTo(NAMED);
            }
        }

        @Test
        void tellsJdtlsOfTheFilesEditedSinceTheLastRequestBeforeAskingForTheExtraction() throws Exception {
            fake.registersWatchersFor("**/*.java");
            scriptTheExtraction();

            try (var server = started()) {
                Files.writeString(greeter, ORIGINAL + "// edited\n");

                server.extractVariable(greeter, selection, "text");

                assertThat(fake.received())
                        .containsExactly(
                                "didChangeWatchedFiles",
                                "acknowledged .gabier-changes-taken-1",
                                "getRefactorEdit Greeter.java",
                                "didOpen Greeter.java",
                                "rename Greeter.java");
            }
        }

        @Test
        void putsThePathOfTheFileInItsNormalFormInTheRequests() throws Exception {
            scriptTheExtraction();
            var spelled = root.resolve("src/main/../main/java/demo/./Greeter.java");

            try (var server = started()) {
                server.extractVariable(spelled, selection, "text");

                assertThat(fake.refactorEditRequests()
                                .getFirst()
                                .context()
                                .getTextDocument()
                                .getUri())
                        .isEqualTo(greeter.toUri().toString());
                assertThat(fake.opened().getFirst().uri())
                        .isEqualTo(greeter.toUri().toString());
            }
        }

        @Test
        void refusesAFileOutsideTheProjectWithoutAskingJdtls() throws Exception {
            Path outside = root.resolveSibling("outside").resolve("Elsewhere.java");

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractVariable(outside, selection, "text"))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT);
                            assertThat(refusal.parameters()).containsEntry("file", outside.toString());
                        });
                assertThat(fake.refactorEditRequests()).isEmpty();
            }
        }

        @Test
        void refusesAsNotReadyWhileJdtlsIsStillImporting() throws Exception {
            fake.neverAnswersInitialize();

            try (var server = start()) {
                assertThatThrownBy(() -> server.extractVariable(greeter, selection, "text"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
                assertThat(fake.refactorEditRequests()).isEmpty();
            }
        }

        @Test
        void saysWhatWentWrongWhenJdtlsFailsInsteadOfDeclining() throws Exception {
            fake.refactorEditAnswers(CompletableFuture.failedFuture(new IllegalStateException("the model is broken")));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractVariable(greeter, selection, "text"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("waiting for the extraction of the expression at")
                        .hasMessageContaining("Internal error");
            }
        }

        @Test
        void failsWhenJdtlsDoesNotAnswerTheExtractionInTime() throws Exception {
            // No answer is scripted.
            try (var server = start(Duration.ofSeconds(1))) {
                server.awaitStarted();

                assertThatThrownBy(() -> server.extractVariable(greeter, selection, "text"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("Timed out")
                        .hasMessageContaining("the extraction of the expression");
            }
        }

        @Test
        void failsAndClosesTheTextWhenJdtlsDoesNotTakeItInInTime() throws Exception {
            fake.refactorEditAnswers(proposal(declaresString()));

            try (var server = start(Duration.ofSeconds(1))) {
                server.awaitStarted();

                assertThatThrownBy(() -> server.extractVariable(greeter, selection, "text"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("Timed out")
                        .hasMessageContaining("take in the extracted variable");
                assertThat(fake.closed(1)).containsExactly(greeter.toUri().toString());
            }
        }

        @Test
        void failsWhenJdtlsDoesNotAnswerTheRenameInTime() throws Exception {
            fake.refactorEditAnswers(proposal(declaresString())).publishesOnOpen(greeter, List.of());

            try (var server = start(Duration.ofSeconds(1))) {
                server.awaitStarted();

                assertThatThrownBy(() -> server.extractVariable(greeter, selection, "text"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("Timed out")
                        .hasMessageContaining("the rename of the extracted variable");
            }
        }

        @Test
        void keepsTheInterruptionOfTheThreadThatWaits() throws Exception {
            scriptTheExtraction();

            try (var server = started()) {
                Thread.currentThread().interrupt();

                assertThatThrownBy(() -> server.extractVariable(greeter, selection, "text"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("Interrupted while waiting for the extraction");
                assertThat(Thread.interrupted()).isTrue();
            }
        }
    }
}
