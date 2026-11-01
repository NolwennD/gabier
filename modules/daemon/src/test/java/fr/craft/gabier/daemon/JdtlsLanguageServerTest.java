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
import fr.craft.gabier.editing.domain.TypeKind;
import fr.craft.gabier.protocol.JdtlsState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CreateFile;
import org.eclipse.lsp4j.CreateFileOptions;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.SnippetTextEdit;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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
        void refusesAFileThatDoesNotExistAsMissing() throws Exception {
            Path missing = root.resolve("src/main/java/demo/Missing.java");

            try (var server = started()) {
                assertThatThrownBy(() -> server.diagnostics(missing))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.FILE_MISSING);
                            assertThat(refusal.parameters()).containsEntry("file", missing.toString());
                        });
                assertThat(fake.opened()).isEmpty();
            }
        }

        @Test
        void refusesAFolderOfTheProjectAsNotBeingAFile() throws Exception {
            Path folder = root.resolve("src/main/java/demo");

            try (var server = started()) {
                assertThatThrownBy(() -> server.diagnostics(folder))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_A_FILE);
                            assertThat(refusal.parameters()).containsEntry("file", folder.toString());
                        });
                assertThat(fake.opened()).isEmpty();
            }
        }

        @Test
        void failsWhenTheFileCannotBeReadAtAll() throws Exception {
            Files.setPosixFilePermissions(clean, Set.of());

            try (var server = started()) {
                assertThatThrownBy(() -> server.diagnostics(clean)).isInstanceOf(UncheckedIOException.class);
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

        private static final String SQL_DATE_AND_UTIL_DATE = """
            [{"candidates": [{"fullyQualifiedName": "java.util.Date", "id": "java.util.Date@2"},
                             {"fullyQualifiedName": "sun.util.calendar.BaseCalendar.Date", "id": "x@3"},
                             {"fullyQualifiedName": "java.sql.Date", "id": "java.sql.Date@1"}],
              "range": {"start": {"line": 6, "character": 4}, "end": {"line": 6, "character": 8}}}]
            """;

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
            // An import that jdtls removes is judged by the diagnostics of the file: none, unless a test says.
            fake.publishesOnOpen(greeter, List.of());
        }

        private org.eclipse.lsp4j.Diagnostic problem(DiagnosticSeverity severity, int line) {
            return new org.eclipse.lsp4j.Diagnostic(
                    new Range(new org.eclipse.lsp4j.Position(line, 7), new org.eclipse.lsp4j.Position(line, 20)),
                    "The import java.util.Set cannot be resolved",
                    severity,
                    "jdt");
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
                assertThat(fake.received())
                        .containsExactly("codeAction Greeter.java", "resolveCodeAction", "didOpen Greeter.java");
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
                                "codeAction Greeter.java",
                                "didOpen Greeter.java");
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
        void opensNothingWhenTheEditRemovesNoImport() throws Exception {
            var reorder = new TextEdit(
                    new Range(new org.eclipse.lsp4j.Position(2, 0), new org.eclipse.lsp4j.Position(4, 0)),
                    "import java.util.List;\nimport java.util.Set;\n");
            fake.codeActionAnswers(offers(resolvedAction(reorder)));

            try (var server = started()) {
                assertThat(server.organizeImports(greeter)).isPresent();

                assertThat(fake.opened()).isEmpty();
            }
        }

        @Test
        void asksTheDiagnosticsOfTheFileWhenTheEditRemovesAnImportAndKeepsTheEditWithoutError() throws Exception {
            fake.codeActionAnswers(offers(resolvedAction(removeSet())))
                    .publishesOnOpen(greeter, List.of(problem(DiagnosticSeverity.Warning, 2)));

            try (var server = started()) {
                // An import that nothing uses has a warning, never used: that is jdtls judging it.
                assertThat(server.organizeImports(greeter)).contains(removalOfSet());

                assertThat(fake.opened()).hasSize(1);
            }
        }

        @Test
        void refusesAnImportThatJdtlsRemovesAndReportsAnErrorOnAndNamesIt() throws Exception {
            fake.codeActionAnswers(offers(resolvedAction(removeSet())))
                    .publishesOnOpen(greeter, List.of(problem(DiagnosticSeverity.Error, 2)));

            try (var server = started()) {
                assertThatThrownBy(() -> server.organizeImports(greeter))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.UNRESOLVED_IMPORTS);
                            assertThat(refusal.parameters())
                                    .containsEntry("file", greeter.toString())
                                    .containsEntry("imports", "java.util.Set");
                        });
            }
        }

        @Test
        void namesTheStaticImportsAndEveryImportRemovedThatHasAnError() throws Exception {
            Path statics = write(
                    "src/main/java/demo/Statics.java",
                    "package demo;\n\n"
                            + "import static java.util.Map.entry;\n"
                            + "import java.util.Set;\n"
                            + "import java.util.List;\n\n"
                            + "class Statics {\n"
                            + "    List<String> names;\n"
                            + "}\n");
            var action = lazyAction();
            action.setEdit(new WorkspaceEdit(Map.of(
                    statics.toUri().toString(),
                    List.of(new TextEdit(
                            new Range(new org.eclipse.lsp4j.Position(2, 0), new org.eclipse.lsp4j.Position(4, 0)),
                            "")))));
            fake.codeActionAnswers(offers(action))
                    .publishesOnOpen(
                            statics,
                            List.of(
                                    problem(DiagnosticSeverity.Error, 2),
                                    problem(DiagnosticSeverity.Error, 3),
                                    problem(DiagnosticSeverity.Error, 3),
                                    problem(DiagnosticSeverity.Error, 6)));

            try (var server = started()) {
                assertThatThrownBy(() -> server.organizeImports(statics))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.parameters())
                                        .containsEntry("imports", "java.util.Map.entry, java.util.Set"));
            }
        }

        @Test
        void keepsTheEditWhenTheErrorsAreNotOnTheImportsItRemoves() throws Exception {
            fake.codeActionAnswers(offers(resolvedAction(removeSet())))
                    .publishesOnOpen(greeter, List.of(problem(DiagnosticSeverity.Error, 6)));

            try (var server = started()) {
                assertThat(server.organizeImports(greeter)).contains(removalOfSet());
            }
        }

        @Test
        void refusesATypeSeveralPackagesDeclareWithItsCandidatesInOrderAndChoosesNone() throws Exception {
            fake.codeActionAnswers(offers(resolvedAction(removeSet()))).asksToChooseImports(SQL_DATE_AND_UTIL_DATE);

            try (var server = started()) {
                assertThatThrownBy(() -> server.organizeImports(greeter))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.AMBIGUOUS_IMPORTS);
                            assertThat(refusal.parameters())
                                    .containsEntry(
                                            "candidates",
                                            "java.sql.Date, java.util.Date, sun.util.calendar.BaseCalendar.Date");
                        });
                assertThat(fake.commandAnswers()).containsExactly(List.of());
            }
        }

        @Test
        void refusesATypeSeveralPackagesDeclareEvenWhenJdtlsHasNoEditToGive() throws Exception {
            fake.codeActionAnswers(CompletableFuture.completedFuture(List.of()))
                    .asksToChooseImports(SQL_DATE_AND_UTIL_DATE);

            try (var server = started()) {
                assertThatThrownBy(() -> server.organizeImports(greeter))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal ->
                                        assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.AMBIGUOUS_IMPORTS));
            }
        }

        @Test
        void doesNotCarryTheCandidatesOfARequestOverToTheNext() throws Exception {
            fake.codeActionAnswers(offers(resolvedAction(removeSet()))).asksToChooseImports(SQL_DATE_AND_UTIL_DATE);

            try (var server = started()) {
                assertThatThrownBy(() -> server.organizeImports(greeter)).isInstanceOf(LanguageServerException.class);
                fake.asksToChooseImports("[]");

                assertThat(server.organizeImports(greeter)).contains(removalOfSet());
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
        void refusesANameJdtlsRenamesIntoAnEditThatDoesNotLeaveTheDeclarationAsItWas() throws Exception {
            // The name of the parameter: jdtls does not decline it, and its one merged edit loses the space before the
            // =.
            var merged =
                    new TextEdit(new Range(at(2, 15), at(3, 21)), "name= \"Hello, \" + name;\n        return name");
            fake.refactorEditAnswers(proposal(declaresString()))
                    .publishesOnOpen(greeter, List.of())
                    .renameAnswers(CompletableFuture.completedFuture(edit(editsOf(greeter, merged))));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractVariable(greeter, selection, "name"))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.INVALID_NAME);
                            assertThat(refusal.parameters()).containsEntry("name", "name");
                        });
            }
        }

        @Test
        void refusesANameJdtlsRenamesWithoutTouchingTheDeclaration() throws Exception {
            fake.refactorEditAnswers(proposal(declaresString()))
                    .publishesOnOpen(greeter, List.of())
                    .renameAnswers(CompletableFuture.completedFuture(edit(editsOf(
                            greeter, new TextEdit(new Range(at(3, 15), at(3, 21)), "text")))));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractVariable(greeter, selection, "text"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.INVALID_NAME));
            }
        }

        @Test
        void refusesASelectionJdtlsHasNoExtractionFor() throws Exception {
            fake.refactorEditAnswers(CompletableFuture.completedFuture(null));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractVariable(greeter, selection, "text"))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_EXTRACTABLE);
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
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_EXTRACTABLE));
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
        void refusesAsNotAnExpressionWhenJdtlsGivesNoEditButSaysWhy() throws Exception {
            fake.refactorEditAnswers(CompletableFuture.completedFuture(
                    new JdtlsServer.RefactorEdit(null, null, "Cannot extract a statement")));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractVariable(greeter, selection, "text"))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_EXTRACTABLE);
                            assertThat(refusal.parameters()).containsEntry("file", greeter.toString());
                        });
                assertThat(fake.opened()).isEmpty();
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

    @Nested
    class ExtractingAMethod {

        private static final String ORIGINAL = """
            class Totals {
                int quadrupled(int count) {
                    return count * 2 * 2;
                }

                int doubled(int count) {
                    return count * 2;
                }
            }
            """;

        /** The text once jdtls' edit is applied: the method carries the name jdtls proposed. */
        private static final String PROPOSED = """
            class Totals {
                int quadrupled(int count) {
                    return extracted(count);
                }

                private int extracted(int count) {
                    return count * 2 * 2;
                }

                int doubled(int count) {
                    return count * 2;
                }
            }
            """;

        /** What jdtls inserts after the method: a blank line and the declaration of the new one. */
        private static final String DECLARATION = """


                private int extracted(int count) {
                    return count * 2 * 2;
                }\
            """;

        private final fr.craft.gabier.editing.domain.Range selection =
                new fr.craft.gabier.editing.domain.Range(new Position(2, 15), new Position(2, 28));
        private Path totals;

        @BeforeEach
        void writeTheSources() throws IOException {
            totals = write("src/main/java/demo/Totals.java", ORIGINAL);
        }

        private static org.eclipse.lsp4j.Position at(int line, int column) {
            return new org.eclipse.lsp4j.Position(line, column);
        }

        private WorkspaceEdit extractsTheMethod() {
            return new WorkspaceEdit(List.of(Either.forLeft(new TextDocumentEdit(
                    new VersionedTextDocumentIdentifier(totals.toUri().toString(), 1),
                    List.of(
                            Either.forLeft(new TextEdit(new Range(at(2, 15), at(2, 28)), "extracted(count)")),
                            Either.forLeft(new TextEdit(new Range(at(3, 5), at(3, 5)), DECLARATION)))))));
        }

        /** What jdtls answers to the rename, for the text it was given: the call and the declaration. */
        private WorkspaceEdit renamesTo(String name) {
            return new WorkspaceEdit(List.of(Either.forLeft(new TextDocumentEdit(
                    new VersionedTextDocumentIdentifier(totals.toUri().toString(), 1),
                    List.of(
                            Either.forLeft(new TextEdit(new Range(at(2, 15), at(2, 24)), name)),
                            Either.forLeft(new TextEdit(new Range(at(5, 16), at(5, 25)), name)))))));
        }

        private CompletableFuture<JdtlsServer.RefactorEdit> proposal() {
            var where = new JdtlsServer.RenamePosition(
                    totals.toUri().toString(), PROPOSED.indexOf("extracted"), "extracted".length());
            var follow = new JdtlsServer.FollowUp("java.action.rename", List.of(where));

            return CompletableFuture.completedFuture(new JdtlsServer.RefactorEdit(extractsTheMethod(), follow, null));
        }

        private Either<SymbolInformation, DocumentSymbol> symbol(String label, SymbolKind kind) {
            return Either.forLeft(new SymbolInformation(
                    label, kind, new Location(totals.toUri().toString(), new Range(at(0, 0), at(0, 0))), "Totals"));
        }

        /** The members of the text jdtls was given, as it lists them: its label is {@code name(parameters) : type}. */
        private CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> members(String... methods) {
            var listed = new ArrayList<Either<SymbolInformation, DocumentSymbol>>();
            listed.add(symbol("Totals", SymbolKind.Class));
            for (String method : methods) {
                listed.add(symbol(method, SymbolKind.Method));
            }

            return CompletableFuture.completedFuture(listed);
        }

        private void scriptTheExtraction(String name) {
            fake.refactorEditAnswers(proposal())
                    .publishesOnOpen(totals, List.of())
                    .documentSymbolAnswers(
                            members("quadrupled(int) : int", "extracted(int) : int", "doubled(int) : int"))
                    .renameAnswers(CompletableFuture.completedFuture(renamesTo(name)));
        }

        private static String applied(String text, ChangeSet changes) {
            var edit = (FileChange.EditFile) changes.changes().getFirst();

            return SourceText.edited(edit.file(), text, edit.edits());
        }

        @Test
        void asksJdtlsForTheExtractionOfTheSelectionIntoAMethodAsGiven() throws Exception {
            scriptTheExtraction("scaled");

            try (var server = started()) {
                server.extractMethod(totals, selection, "scaled");

                assertThat(fake.refactorEditRequests()).hasSize(1);
                var request = fake.refactorEditRequests().getFirst();
                assertThat(request.command()).isEqualTo("extractMethod");
                assertThat(request.context().getTextDocument().getUri())
                        .isEqualTo(totals.toUri().toString());
                assertThat(request.context().getRange()).isEqualTo(new Range(at(2, 15), at(2, 28)));
            }
        }

        @Test
        void renamesTheMethodToTheNameAskedForOnTheTextThatJdtlsGaveAfterLookingAtItsMethods() throws Exception {
            scriptTheExtraction("scaled");

            try (var server = started()) {
                server.extractMethod(totals, selection, "scaled");

                assertThat(fake.opened())
                        .containsExactly(new FakeJdtls.Opened(totals.toUri().toString(), PROPOSED));
                assertThat(fake.renamed()).hasSize(1);
                assertThat(fake.renamed().getFirst().getPosition()).isEqualTo(at(2, 15));
                assertThat(fake.renamed().getFirst().getNewName()).isEqualTo("scaled");
                assertThat(fake.received())
                        .containsExactly(
                                "getRefactorEdit Totals.java",
                                "didOpen Totals.java",
                                "documentSymbol Totals.java",
                                "rename Totals.java");
                assertThat(fake.closed(1)).containsExactly(totals.toUri().toString());
                assertThat(totals).hasContent(ORIGINAL);
            }
        }

        @Test
        void answersOneEditOfTheFileAsItIsOnDiskWithTheNameAskedFor() throws Exception {
            scriptTheExtraction("scaled");

            try (var server = started()) {
                ChangeSet changes = server.extractMethod(totals, selection, "scaled");

                assertThat(changes.changes()).hasSize(1);
                assertThat(applied(ORIGINAL, changes)).isEqualTo(PROPOSED.replace("extracted", "scaled"));
            }
        }

        @Test
        void keepsTheNameJdtlsProposedWhenThatIsTheNameAskedForWithoutRenamingAnything() throws Exception {
            fake.refactorEditAnswers(proposal());

            try (var server = started()) {
                ChangeSet changes = server.extractMethod(totals, selection, "extracted");

                assertThat(applied(ORIGINAL, changes)).isEqualTo(PROPOSED);
                assertThat(fake.opened()).isEmpty();
                assertThat(fake.renamed()).isEmpty();
            }
        }

        @Test
        void refusesANameOfAMethodTheClassHasWithTheParametersOfTheExtractedOneAndRenamesNothing() throws Exception {
            scriptTheExtraction("doubled");

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractMethod(totals, selection, "doubled"))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.INVALID_NAME);
                            assertThat(refusal.parameters())
                                    .containsEntry("name", "doubled")
                                    .containsEntry("line", "2")
                                    .containsEntry("column", "15");
                        });
                assertThat(fake.renamed()).isEmpty();
                assertThat(fake.closed(1)).containsExactly(totals.toUri().toString());
                assertThat(totals).hasContent(ORIGINAL);
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {"doubled(int) : String", "doubled(int)"})
        void refusesTheNameOfAMethodThatDiffersOnlyByItsReturnTypeOrHasNone(String existing) throws Exception {
            fake.refactorEditAnswers(proposal())
                    .publishesOnOpen(totals, List.of())
                    .documentSymbolAnswers(members("extracted(int) : int", existing));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractMethod(totals, selection, "doubled"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.INVALID_NAME));
            }
        }

        @Test
        void acceptsTheNameOfAMethodThatTheClassHasWithOtherParameters() throws Exception {
            fake.refactorEditAnswers(proposal())
                    .publishesOnOpen(totals, List.of())
                    .documentSymbolAnswers(members("extracted(int) : int", "doubled(String) : int"))
                    .renameAnswers(CompletableFuture.completedFuture(renamesTo("doubled")));

            try (var server = started()) {
                ChangeSet changes = server.extractMethod(totals, selection, "doubled");

                assertThat(applied(ORIGINAL, changes)).isEqualTo(PROPOSED.replace("extracted", "doubled"));
            }
        }

        @Test
        void acceptsTheNameOfAMethodOfAnotherClassAndOfAFieldWithTheSameParameters() throws Exception {
            var other = symbol("doubled(int) : int", SymbolKind.Method).getLeft();
            other.setContainerName("Other");
            fake.refactorEditAnswers(proposal())
                    .publishesOnOpen(totals, List.of())
                    .documentSymbolAnswers(CompletableFuture.completedFuture(List.of(
                            symbol("extracted(int) : int", SymbolKind.Method),
                            Either.forLeft(other),
                            symbol("doubled(int) : int", SymbolKind.Field))))
                    .renameAnswers(CompletableFuture.completedFuture(renamesTo("doubled")));

            try (var server = started()) {
                ChangeSet changes = server.extractMethod(totals, selection, "doubled");

                assertThat(applied(ORIGINAL, changes)).isEqualTo(PROPOSED.replace("extracted", "doubled"));
            }
        }

        @Test
        void failsWhenJdtlsDoesNotListTheMethodItExtracted() throws Exception {
            fake.refactorEditAnswers(proposal())
                    .publishesOnOpen(totals, List.of())
                    .documentSymbolAnswers(members("doubled(int) : int"));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractMethod(totals, selection, "scaled"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("did not list the method extracted");
                assertThat(fake.closed(1)).containsExactly(totals.toUri().toString());
            }
        }

        @Test
        void failsWhenJdtlsListsNothingForTheText() throws Exception {
            fake.refactorEditAnswers(proposal())
                    .publishesOnOpen(totals, List.of())
                    .documentSymbolAnswers(CompletableFuture.completedFuture(null));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractMethod(totals, selection, "scaled"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("did not list the method extracted");
            }
        }

        @Test
        void refusesANameJdtlsDeclinesForTheMethod() throws Exception {
            fake.refactorEditAnswers(proposal())
                    .publishesOnOpen(totals, List.of())
                    .documentSymbolAnswers(members("extracted(int) : int"))
                    .renameAnswers(CompletableFuture.failedFuture(new ResponseErrorException(
                            new ResponseError(ResponseErrorCode.InvalidRequest, "not an identifier", null))));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractMethod(totals, selection, "1st"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.INVALID_NAME));
                assertThat(fake.closed(1)).containsExactly(totals.toUri().toString());
            }
        }

        @Test
        void refusesASelectionJdtlsHasNoExtractionFor() throws Exception {
            fake.refactorEditAnswers(CompletableFuture.completedFuture(null));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractMethod(totals, selection, "scaled"))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_EXTRACTABLE);
                            assertThat(refusal.parameters())
                                    .containsEntry("file", totals.toString())
                                    .containsEntry("line", "2")
                                    .containsEntry("column", "15")
                                    .containsEntry("endLine", "2")
                                    .containsEntry("endColumn", "28");
                        });
                assertThat(fake.opened()).isEmpty();
            }
        }

        @Test
        void refusesASelectionForWhichJdtlsGivesNoEdit() throws Exception {
            fake.refactorEditAnswers(CompletableFuture.completedFuture(
                    new JdtlsServer.RefactorEdit(null, null, "Cannot extract this selection")));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractMethod(totals, selection, "scaled"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_EXTRACTABLE));
            }
        }

        @Test
        void refusesAFileOutsideTheProjectWithoutAskingJdtls() throws Exception {
            try (var server = started()) {
                assertThatThrownBy(() -> server.extractMethod(root.resolve("../Elsewhere.java"), selection, "scaled"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key())
                                        .isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));
                assertThat(fake.refactorEditRequests()).isEmpty();
            }
        }

        @Test
        void keepsTheInterruptionOfTheThreadThatWaits() throws Exception {
            scriptTheExtraction("scaled");

            try (var server = started()) {
                Thread.currentThread().interrupt();

                assertThatThrownBy(() -> server.extractMethod(totals, selection, "scaled"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining(
                                "Interrupted while waiting for the extraction of the selection into a method");
                assertThat(Thread.interrupted()).isTrue();
            }
        }
    }

    /**
     * The extraction of a constant: as for a variable, jdtls proposes a name of its own and says where it is, the adapter
     * has it renamed to the name asked for on the text the edit gives, and answers one edit on the file as it is on disk.
     */
    @Nested
    class ExtractingAConstant {

        private static final String ORIGINAL = """
            class Totals {
                void print(int count) {
                    System.out.println("count: " + count);
                }
            }
            """;

        /** The text once jdtls' edit is applied: the constant carries the name jdtls proposed. */
        private static final String PROPOSED = """
            class Totals {
                private static final String COUNT = "count: ";

                void print(int count) {
                    System.out.println(COUNT + count);
                }
            }
            """;

        private final fr.craft.gabier.editing.domain.Range selection =
                new fr.craft.gabier.editing.domain.Range(new Position(2, 27), new Position(2, 36));
        private Path totals;

        @BeforeEach
        void writeTheSources() throws IOException {
            totals = write("src/main/java/demo/Totals.java", ORIGINAL);
        }

        private static org.eclipse.lsp4j.Position at(int line, int column) {
            return new org.eclipse.lsp4j.Position(line, column);
        }

        private WorkspaceEdit workspaceEdit(TextEdit... edits) {
            return new WorkspaceEdit(List.of(Either.forLeft(new TextDocumentEdit(
                    new VersionedTextDocumentIdentifier(totals.toUri().toString(), 1),
                    Arrays.stream(edits)
                            .map(Either::<TextEdit, SnippetTextEdit>forLeft)
                            .toList()))));
        }

        /** What jdtls answers: the declaration is inserted before the first member, the literal is replaced. */
        private WorkspaceEdit declaresTheConstant() {
            return workspaceEdit(
                    new TextEdit(
                            new Range(at(1, 4), at(1, 4)), "private static final String COUNT = \"count: \";\n\n    "),
                    new TextEdit(new Range(at(2, 27), at(2, 36)), "COUNT"));
        }

        /** What jdtls answers to the rename, for the text it was given: the declaration and the use. */
        private WorkspaceEdit renamesTo(String name) {
            return workspaceEdit(
                    new TextEdit(new Range(at(1, 32), at(1, 37)), name),
                    new TextEdit(new Range(at(4, 27), at(4, 32)), name));
        }

        private CompletableFuture<JdtlsServer.RefactorEdit> proposal() {
            var where = new JdtlsServer.RenamePosition(
                    totals.toUri().toString(), PROPOSED.indexOf("COUNT"), "COUNT".length());
            var follow = new JdtlsServer.FollowUp("java.action.rename", List.of(where));

            return CompletableFuture.completedFuture(new JdtlsServer.RefactorEdit(declaresTheConstant(), follow, null));
        }

        private void scriptTheExtraction(String name) {
            fake.refactorEditAnswers(proposal())
                    .publishesOnOpen(totals, List.of())
                    .renameAnswers(CompletableFuture.completedFuture(renamesTo(name)));
        }

        private static String applied(String text, ChangeSet changes) {
            var edit = (FileChange.EditFile) changes.changes().getFirst();

            return SourceText.edited(edit.file(), text, edit.edits());
        }

        @Test
        void asksJdtlsForTheExtractionOfTheSelectionIntoAConstantAsGiven() throws Exception {
            scriptTheExtraction("COUNT_LABEL");

            try (var server = started()) {
                server.extractConstant(totals, selection, "COUNT_LABEL");

                assertThat(fake.refactorEditRequests()).hasSize(1);
                var request = fake.refactorEditRequests().getFirst();
                assertThat(request.command()).isEqualTo("extractConstant");
                assertThat(request.context().getTextDocument().getUri())
                        .isEqualTo(totals.toUri().toString());
                assertThat(request.context().getRange()).isEqualTo(new Range(at(2, 27), at(2, 36)));
            }
        }

        @Test
        void renamesTheConstantToTheNameAskedForOnTheTextThatJdtlsGave() throws Exception {
            scriptTheExtraction("COUNT_LABEL");

            try (var server = started()) {
                server.extractConstant(totals, selection, "COUNT_LABEL");

                assertThat(fake.opened())
                        .containsExactly(new FakeJdtls.Opened(totals.toUri().toString(), PROPOSED));
                assertThat(fake.renamed()).hasSize(1);
                assertThat(fake.renamed().getFirst().getPosition()).isEqualTo(at(1, 32));
                assertThat(fake.renamed().getFirst().getNewName()).isEqualTo("COUNT_LABEL");
                assertThat(fake.closed(1)).containsExactly(totals.toUri().toString());
                assertThat(totals).hasContent(ORIGINAL);
            }
        }

        @Test
        void answersOneEditOfTheFileAsItIsOnDiskWithTheNameAskedFor() throws Exception {
            scriptTheExtraction("COUNT_LABEL");

            try (var server = started()) {
                ChangeSet changes = server.extractConstant(totals, selection, "COUNT_LABEL");

                assertThat(changes.changes()).hasSize(1);
                assertThat(applied(ORIGINAL, changes)).isEqualTo(PROPOSED.replace("COUNT", "COUNT_LABEL"));
            }
        }

        @Test
        void keepsTheNameJdtlsProposedWhenThatIsTheNameAskedForWithoutRenamingAnything() throws Exception {
            fake.refactorEditAnswers(proposal());

            try (var server = started()) {
                ChangeSet changes = server.extractConstant(totals, selection, "COUNT");

                assertThat(applied(ORIGINAL, changes)).isEqualTo(PROPOSED);
                assertThat(fake.renamed()).isEmpty();
            }
        }

        @Test
        void refusesAnExpressionJdtlsHasNoExtractionFor() throws Exception {
            fake.refactorEditAnswers(CompletableFuture.completedFuture(null));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractConstant(totals, selection, "COUNT_LABEL"))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_EXTRACTABLE);
                            assertThat(refusal.parameters())
                                    .containsEntry("file", totals.toString())
                                    .containsEntry("line", "2")
                                    .containsEntry("column", "27")
                                    .containsEntry("endLine", "2")
                                    .containsEntry("endColumn", "36");
                        })
                        .hasMessageContaining(
                                "cannot be extracted: it is not an expression or statements that can be extracted"
                                        + " here");
                assertThat(fake.opened()).isEmpty();
            }
        }

        @Test
        void refusesAnExpressionForWhichJdtlsGivesNoEdit() throws Exception {
            fake.refactorEditAnswers(CompletableFuture.completedFuture(
                    new JdtlsServer.RefactorEdit(null, null, "Cannot extract this expression")));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractConstant(totals, selection, "COUNT_LABEL"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_EXTRACTABLE));
            }
        }

        @Test
        void refusesANameJdtlsDeclinesForTheConstant() throws Exception {
            fake.refactorEditAnswers(proposal())
                    .publishesOnOpen(totals, List.of())
                    .renameAnswers(CompletableFuture.failedFuture(new ResponseErrorException(
                            new ResponseError(ResponseErrorCode.InvalidRequest, "not an identifier", null))));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractConstant(totals, selection, "1ST"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.INVALID_NAME));
                assertThat(fake.closed(1)).containsExactly(totals.toUri().toString());
            }
        }

        @Test
        void refusesAFileOutsideTheProjectWithoutAskingJdtls() throws Exception {
            try (var server = started()) {
                assertThatThrownBy(() -> server.extractConstant(root.resolve("../Elsewhere.java"), selection, "ANY"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key())
                                        .isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));
                assertThat(fake.refactorEditRequests()).isEmpty();
            }
        }

        @Test
        void keepsTheInterruptionOfTheThreadThatWaits() throws Exception {
            scriptTheExtraction("COUNT_LABEL");

            try (var server = started()) {
                Thread.currentThread().interrupt();

                assertThatThrownBy(() -> server.extractConstant(totals, selection, "COUNT_LABEL"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining(
                                "Interrupted while waiting for the extraction of the expression into a constant");
                assertThat(Thread.interrupted()).isTrue();
            }
        }
    }

    /**
     * The extraction of a field: jdtls declares it at the top of the class and, by default, assigns it in the current
     * method. The place it says the name is, the first in its own order, is the assignment and not the declaration, so
     * the rename changes the text before it: the adapter has the field renamed from there and still answers one edit.
     */
    @Nested
    class ExtractingAField {

        private static final String ORIGINAL = """
            class Totals {
                void print(int count) {
                    System.out.println("count: " + count);
                }
            }
            """;

        /** The text once jdtls' edit is applied: the field carries the name jdtls proposed. */
        private static final String PROPOSED = """
            class Totals {
                private String string;

                void print(int count) {
                    string = "count: ";
                    System.out.println(string + count);
                }
            }
            """;

        private final fr.craft.gabier.editing.domain.Range selection =
                new fr.craft.gabier.editing.domain.Range(new Position(2, 27), new Position(2, 36));
        private Path totals;

        @BeforeEach
        void writeTheSources() throws IOException {
            totals = write("src/main/java/demo/Totals.java", ORIGINAL);
        }

        private static org.eclipse.lsp4j.Position at(int line, int column) {
            return new org.eclipse.lsp4j.Position(line, column);
        }

        private WorkspaceEdit workspaceEdit(TextEdit... edits) {
            return new WorkspaceEdit(List.of(Either.forLeft(new TextDocumentEdit(
                    new VersionedTextDocumentIdentifier(totals.toUri().toString(), 1),
                    Arrays.stream(edits)
                            .map(Either::<TextEdit, SnippetTextEdit>forLeft)
                            .toList()))));
        }

        /** What jdtls answers: the declaration before the first member, the assignment before the statement. */
        private WorkspaceEdit declaresTheField() {
            return workspaceEdit(
                    new TextEdit(new Range(at(1, 4), at(1, 4)), "private String string;\n\n    "),
                    new TextEdit(new Range(at(2, 8), at(2, 8)), "string = \"count: \";\n        "),
                    new TextEdit(new Range(at(2, 27), at(2, 36)), "string"));
        }

        /** What jdtls answers to the rename, for the text it was given: the declaration, the assignment and the use. */
        private WorkspaceEdit renamesTo(String name) {
            return workspaceEdit(
                    new TextEdit(new Range(at(1, 19), at(1, 25)), name),
                    new TextEdit(new Range(at(4, 8), at(4, 14)), name),
                    new TextEdit(new Range(at(5, 27), at(5, 33)), name));
        }

        private CompletableFuture<JdtlsServer.RefactorEdit> proposal() {
            var where = new JdtlsServer.RenamePosition(
                    totals.toUri().toString(), PROPOSED.indexOf("string = "), "string".length());
            var follow = new JdtlsServer.FollowUp("java.action.rename", List.of(where));

            return CompletableFuture.completedFuture(new JdtlsServer.RefactorEdit(declaresTheField(), follow, null));
        }

        private void scriptTheExtraction(WorkspaceEdit rename) {
            fake.refactorEditAnswers(proposal())
                    .publishesOnOpen(totals, List.of())
                    .renameAnswers(CompletableFuture.completedFuture(rename));
        }

        private static String applied(String text, ChangeSet changes) {
            var edit = (FileChange.EditFile) changes.changes().getFirst();

            return SourceText.edited(edit.file(), text, edit.edits());
        }

        @Test
        void asksJdtlsForTheExtractionOfTheSelectionIntoAFieldAsGivenAndLeavesItWhereToInitialiseIt() throws Exception {
            scriptTheExtraction(renamesTo("label"));

            try (var server = started()) {
                server.extractField(totals, selection, "label");

                assertThat(fake.refactorEditRequests()).hasSize(1);
                var request = fake.refactorEditRequests().getFirst();
                assertThat(request.command()).isEqualTo("extractField");
                assertThat(request.context().getTextDocument().getUri())
                        .isEqualTo(totals.toUri().toString());
                assertThat(request.context().getRange()).isEqualTo(new Range(at(2, 27), at(2, 36)));
            }
        }

        @Test
        void renamesTheFieldFromThePlaceJdtlsSaidOnTheTextItGave() throws Exception {
            scriptTheExtraction(renamesTo("label"));

            try (var server = started()) {
                server.extractField(totals, selection, "label");

                assertThat(fake.opened())
                        .containsExactly(new FakeJdtls.Opened(totals.toUri().toString(), PROPOSED));
                assertThat(fake.renamed()).hasSize(1);
                assertThat(fake.renamed().getFirst().getPosition()).isEqualTo(at(4, 8));
                assertThat(fake.renamed().getFirst().getNewName()).isEqualTo("label");
                assertThat(fake.closed(1)).containsExactly(totals.toUri().toString());
                assertThat(totals).hasContent(ORIGINAL);
            }
        }

        @Test
        void answersOneEditOfTheFileAsItIsOnDiskWithTheNameAskedFor() throws Exception {
            scriptTheExtraction(renamesTo("label"));

            try (var server = started()) {
                ChangeSet changes = server.extractField(totals, selection, "label");

                assertThat(changes.changes()).hasSize(1);
                assertThat(applied(ORIGINAL, changes)).isEqualTo(PROPOSED.replace("string", "label"));
            }
        }

        @Test
        void keepsTheNameJdtlsProposedWhenThatIsTheNameAskedForWithoutRenamingAnything() throws Exception {
            fake.refactorEditAnswers(proposal());

            try (var server = started()) {
                ChangeSet changes = server.extractField(totals, selection, "string");

                assertThat(applied(ORIGINAL, changes)).isEqualTo(PROPOSED);
                assertThat(fake.renamed()).isEmpty();
            }
        }

        @Test
        void refusesANameThatLeavesTheLineOfTheAssignmentOtherwiseThanItWas() throws Exception {
            scriptTheExtraction(workspaceEdit(new TextEdit(new Range(at(4, 8), at(4, 14)), "label=")));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractField(totals, selection, "label"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.INVALID_NAME));
                assertThat(totals).hasContent(ORIGINAL);
            }
        }

        @Test
        void refusesASelectionJdtlsHasNoExtractionFor() throws Exception {
            fake.refactorEditAnswers(CompletableFuture.completedFuture(null));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractField(totals, selection, "label"))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_EXTRACTABLE);
                            assertThat(refusal.parameters())
                                    .containsEntry("file", totals.toString())
                                    .containsEntry("line", "2")
                                    .containsEntry("column", "27")
                                    .containsEntry("endLine", "2")
                                    .containsEntry("endColumn", "36");
                        });
                assertThat(fake.opened()).isEmpty();
            }
        }

        @Test
        void refusesASelectionForWhichJdtlsGivesNoEdit() throws Exception {
            fake.refactorEditAnswers(CompletableFuture.completedFuture(
                    new JdtlsServer.RefactorEdit(null, null, "Cannot extract this selection")));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractField(totals, selection, "label"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_EXTRACTABLE));
            }
        }

        @Test
        void refusesANameJdtlsDeclinesForTheField() throws Exception {
            fake.refactorEditAnswers(proposal())
                    .publishesOnOpen(totals, List.of())
                    .renameAnswers(CompletableFuture.failedFuture(new ResponseErrorException(
                            new ResponseError(ResponseErrorCode.InvalidRequest, "not an identifier", null))));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractField(totals, selection, "1st"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.INVALID_NAME));
                assertThat(fake.closed(1)).containsExactly(totals.toUri().toString());
            }
        }

        @Test
        void refusesAFileOutsideTheProjectWithoutAskingJdtls() throws Exception {
            try (var server = started()) {
                assertThatThrownBy(() -> server.extractField(root.resolve("../Elsewhere.java"), selection, "any"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key())
                                        .isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));
                assertThat(fake.refactorEditRequests()).isEmpty();
            }
        }

        @Test
        void keepsTheInterruptionOfTheThreadThatWaits() throws Exception {
            scriptTheExtraction(renamesTo("label"));

            try (var server = started()) {
                Thread.currentThread().interrupt();

                assertThatThrownBy(() -> server.extractField(totals, selection, "label"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining(
                                "Interrupted while waiting for the extraction of the expression into a field");
                assertThat(Thread.interrupted()).isTrue();
            }
        }
    }

    /** What an extraction does with a selection that jdtls cannot extract: the same, for the four, whatever jdtls says. */
    @Nested
    class ExtractingWhatJdtlsCannotExtract {

        @FunctionalInterface
        interface Extractor {
            ChangeSet extract(
                    JdtlsLanguageServer server, Path file, fr.craft.gabier.editing.domain.Range selection, String name);
        }

        private final fr.craft.gabier.editing.domain.Range selection =
                new fr.craft.gabier.editing.domain.Range(new Position(2, 15), new Position(2, 31));
        private Path greeter;

        @BeforeEach
        void writeTheSources() throws IOException {
            greeter = write("src/main/java/demo/Greeter.java", """
                class Greeter {
                    String greet(String name) {
                        return "Hello, " + name;
                    }
                }
                """);
        }

        static Stream<Arguments> everyExtraction() {
            return Stream.of(
                    Arguments.of("variable", (Extractor) JdtlsLanguageServer::extractVariable),
                    Arguments.of("method", (Extractor) JdtlsLanguageServer::extractMethod),
                    Arguments.of("constant", (Extractor) JdtlsLanguageServer::extractConstant),
                    Arguments.of("field", (Extractor) JdtlsLanguageServer::extractField));
        }

        static Stream<Arguments> everyAnswerThatIsNoEdit() {
            return Stream.of(
                    Arguments.of("nothing", (Supplier<CompletableFuture<JdtlsServer.RefactorEdit>>)
                            () -> CompletableFuture.completedFuture(null)),
                    Arguments.of("an error message and no edit", (Supplier<CompletableFuture<JdtlsServer.RefactorEdit>>)
                            () -> CompletableFuture.completedFuture(
                                    new JdtlsServer.RefactorEdit(null, null, "Cannot extract this selection"))),
                    Arguments.of("no edit and no message", (Supplier<CompletableFuture<JdtlsServer.RefactorEdit>>)
                            () -> CompletableFuture.completedFuture(new JdtlsServer.RefactorEdit(null, null, null))),
                    Arguments.of("a refusal of the request", (Supplier<CompletableFuture<JdtlsServer.RefactorEdit>>)
                            () -> CompletableFuture.failedFuture(new ResponseErrorException(new ResponseError(
                                    ResponseErrorCode.InvalidRequest, "Cannot extract this selection", null)))));
        }

        static Stream<Arguments> everyExtractionWithEveryAnswer() {
            return everyExtraction()
                    .flatMap(extraction -> everyAnswerThatIsNoEdit()
                            .map(answer -> Arguments.of(
                                    extraction.get()[0], extraction.get()[1], answer.get()[0], answer.get()[1])));
        }

        @ParameterizedTest(name = "{0}, jdtls answers {2}")
        @MethodSource("everyExtractionWithEveryAnswer")
        void refusesInOneWayWhateverTheCommandAndTheAnswer(
                String command,
                Extractor extractor,
                String answerName,
                Supplier<CompletableFuture<JdtlsServer.RefactorEdit>> answer)
                throws Exception {
            fake.refactorEditAnswers(answer.get());

            try (var server = started()) {
                assertThatThrownBy(() -> extractor.extract(server, greeter, selection, "name"))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            var expected = LanguageServerException.notExtractable(greeter, selection);
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_EXTRACTABLE);
                            assertThat(refusal.parameters()).isEqualTo(expected.parameters());
                            assertThat(refusal).hasMessage(expected.getMessage());
                            assertThat(refusal.getMessage()).doesNotContain("Cannot extract this selection");
                        });
                assertThat(fake.opened()).isEmpty();
            }
        }
    }

    @Nested
    class MovingATypeToAFile {

        private static final String OUTER = """
            package demo;

            public class Shapes {
                public static class Circle {}
            }
            """;

        private final Position inCircle = new Position(3, 24);
        private Path shapes;
        private Path circle;

        @BeforeEach
        void writeTheSources() throws IOException {
            shapes = write("src/main/java/demo/Shapes.java", OUTER);
            circle = root.resolve("src/main/java/demo/Circle.java");
        }

        private static org.eclipse.lsp4j.Position at(int line, int column) {
            return new org.eclipse.lsp4j.Position(line, column);
        }

        /** What jdtls answers: the outer class without the type, then the file with the type, in this order. */
        private CompletableFuture<JdtlsServer.RefactorEdit> moves() {
            var outer = new TextDocumentEdit(
                    new VersionedTextDocumentIdentifier(shapes.toUri().toString(), null),
                    List.of(Either.forLeft(new TextEdit(new Range(at(3, 4), at(3, 35)), ""))));
            var create = new CreateFile(circle.toUri().toString());
            create.setOptions(new CreateFileOptions(false, true));
            var inner = new TextDocumentEdit(
                    new VersionedTextDocumentIdentifier(circle.toUri().toString(), null),
                    List.of(Either.forLeft(
                            new TextEdit(new Range(at(0, 0), at(0, 0)), "package demo;\n\npublic class Circle {}"))));
            var edit = new WorkspaceEdit(List.of(
                    Either.forLeft(outer),
                    Either.<TextDocumentEdit, ResourceOperation>forRight(create),
                    Either.forLeft(inner)));

            return CompletableFuture.completedFuture(new JdtlsServer.RefactorEdit(edit, null, null));
        }

        @Test
        void asksJdtlsToMoveTheTypeToANewFileWithAnEmptyRangeAtThePositionAndTheReferencesUpdated() throws Exception {
            fake.moveAnswers(moves());

            try (var server = started()) {
                server.moveTypeToFile(shapes, inCircle);

                assertThat(fake.moveRequests()).hasSize(1);
                var request = fake.moveRequests().getFirst();
                assertThat(request.moveKind()).isEqualTo("moveTypeToNewFile");
                assertThat(request.updateReferences()).isTrue();
                assertThat(request.params().getTextDocument().getUri())
                        .isEqualTo(shapes.toUri().toString());
                assertThat(request.params().getRange()).isEqualTo(new Range(at(3, 24), at(3, 24)));
            }
        }

        @Test
        void answersTheEditsOfTheOuterFileTheCreationOfTheNewFileAndItsTextInTheOrderOfJdtls() throws Exception {
            fake.moveAnswers(moves());

            try (var server = started()) {
                ChangeSet changes = server.moveTypeToFile(shapes, inCircle);

                assertThat(changes.changes()).hasSize(3);
                assertThat(changes.changes().get(0)).isInstanceOfSatisfying(FileChange.EditFile.class, edit -> {
                    assertThat(edit.file()).isEqualTo(shapes);
                    assertThat(SourceText.edited(shapes, OUTER, edit.edits()))
                            .isEqualTo("package demo;\n\npublic class Shapes {\n    \n}\n");
                });
                assertThat(changes.changes().get(1)).isEqualTo(new FileChange.CreateFile(circle));
                assertThat(changes.changes().get(2)).isInstanceOfSatisfying(FileChange.EditFile.class, edit -> {
                    assertThat(edit.file()).isEqualTo(circle);
                    assertThat(SourceText.edited(circle, "", edit.edits()))
                            .isEqualTo("package demo;\n\npublic class Circle {}");
                });
                assertThat(shapes).hasContent(OUTER);
                assertThat(circle).doesNotExist();
            }
        }

        @Test
        void refusesATypeJdtlsCannotMoveWhateverItsReason() throws Exception {
            fake.moveAnswers(CompletableFuture.completedFuture(new JdtlsServer.RefactorEdit(
                    null, null, "Failed to move type to new file because no type is selected.")));

            try (var server = started()) {
                assertThatThrownBy(() -> server.moveTypeToFile(shapes, inCircle))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_MOVABLE);
                            assertThat(refusal.parameters())
                                    .containsEntry("file", shapes.toString())
                                    .containsEntry("line", "3")
                                    .containsEntry("column", "24");
                        });
            }
        }

        @Test
        void refusesWhenJdtlsAnswersNothing() throws Exception {
            fake.moveAnswers(CompletableFuture.completedFuture(null));

            try (var server = started()) {
                assertThatThrownBy(() -> server.moveTypeToFile(shapes, inCircle))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_MOVABLE));
            }
        }

        @Test
        void refusesWhenJdtlsDeclinesTheRequest() throws Exception {
            fake.moveAnswers(CompletableFuture.failedFuture(new ResponseErrorException(
                    new ResponseError(ResponseErrorCode.InvalidRequest, "cannot move", null))));

            try (var server = started()) {
                assertThatThrownBy(() -> server.moveTypeToFile(shapes, inCircle))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_MOVABLE));
            }
        }

        @Test
        void refusesAnEditThatChangesNothing() throws Exception {
            fake.moveAnswers(CompletableFuture.completedFuture(
                    new JdtlsServer.RefactorEdit(new WorkspaceEdit(List.of()), null, null)));

            try (var server = started()) {
                assertThatThrownBy(() -> server.moveTypeToFile(shapes, inCircle))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_MOVABLE));
            }
        }

        @Test
        void refusesAFileOutsideTheProjectWithoutAskingJdtls() throws Exception {
            try (var server = started()) {
                assertThatThrownBy(() -> server.moveTypeToFile(root.resolve("../Elsewhere.java"), inCircle))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key())
                                        .isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));
                assertThat(fake.moveRequests()).isEmpty();
            }
        }

        @Test
        void refusesAsNotReadyWhileJdtlsIsStillImporting() throws Exception {
            fake.neverAnswersInitialize();

            try (var server = start()) {
                assertThatThrownBy(() -> server.moveTypeToFile(shapes, inCircle))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
                assertThat(fake.moveRequests()).isEmpty();
            }
        }

        @Test
        void saysWhatWentWrongWhenJdtlsFailsInsteadOfDeclining() throws Exception {
            fake.moveAnswers(CompletableFuture.failedFuture(new IllegalStateException("the model is broken")));

            try (var server = started()) {
                assertThatThrownBy(() -> server.moveTypeToFile(shapes, inCircle))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("waiting for the move of the type at Position[line=3, column=24]")
                        .hasMessageContaining("Internal error");
            }
        }

        @Test
        void refusesAFileThatDoesNotExistAndAFolderWithoutAskingJdtls() throws Exception {
            try (var server = started()) {
                assertThatThrownBy(() -> server.moveTypeToFile(root.resolve("src/main/java/demo/Nope.java"), inCircle))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.FILE_MISSING));
                assertThatThrownBy(() -> server.moveTypeToFile(root.resolve("src/main/java/demo"), inCircle))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_A_FILE));
                assertThat(fake.moveRequests()).isEmpty();
            }
        }

        @Test
        void keepsTheInterruptionOfTheThreadThatWaits() throws Exception {
            fake.moveAnswers(moves());

            try (var server = started()) {
                Thread.currentThread().interrupt();

                assertThatThrownBy(() -> server.moveTypeToFile(shapes, inCircle))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining(
                                "Interrupted while waiting for the move of the type at Position[line=3, column=24]");
                assertThat(Thread.interrupted()).isTrue();
            }
        }
    }

    @Nested
    class Inlining {

        private static final String PRICES = """
            package demo;

            public class Prices {
                static final int VAT = 20;

                int gross(int net) {
                    return net * VAT / 100;
                }
            }
            """;

        private final Position onTheConstant = new Position(3, 21);
        private Path prices;

        @BeforeEach
        void writeTheSources() throws IOException {
            prices = write("src/main/java/demo/Prices.java", PRICES);
        }

        private org.eclipse.lsp4j.Position at(int line, int column) {
            return new org.eclipse.lsp4j.Position(line, column);
        }

        private CompletableFuture<List<Either<Command, CodeAction>>> offers(CodeAction action) {
            return CompletableFuture.completedFuture(List.of(Either.forRight(action)));
        }

        /** What jdtls offers first: the action, with its data and no edit yet. */
        private CodeAction lazyAction() {
            var action = new CodeAction("Inline Constant");
            action.setKind(CodeActionKind.RefactorInline);
            action.setData("jdtls-data");
            return action;
        }

        /** The action once resolved: the edit is given as {@code documentChanges}, as jdtls gives a refactoring. */
        private CodeAction resolvedAction() {
            var edit = new TextDocumentEdit(
                    new VersionedTextDocumentIdentifier(prices.toUri().toString(), null),
                    List.of(
                            Either.forLeft(new TextEdit(new Range(at(3, 0), at(5, 0)), "")),
                            Either.forLeft(new TextEdit(new Range(at(6, 20), at(6, 23)), "20"))));
            var action = lazyAction();
            action.setEdit(new WorkspaceEdit(List.of(Either.forLeft(edit))));
            return action;
        }

        private ChangeSet inlined() {
            return new ChangeSet(List.of(new FileChange.EditFile(
                    prices,
                    List.of(
                            new fr.craft.gabier.editing.domain.TextEdit(
                                    new fr.craft.gabier.editing.domain.Range(new Position(3, 0), new Position(5, 0)),
                                    ""),
                            new fr.craft.gabier.editing.domain.TextEdit(
                                    new fr.craft.gabier.editing.domain.Range(new Position(6, 20), new Position(6, 23)),
                                    "20")))));
        }

        @Test
        void asksForTheInlineActionAtThePositionAloneThenResolvesItAndAnswersInTheTermsOfTheDomain() throws Exception {
            fake.codeActionAnswers(offers(lazyAction()))
                    .resolveAnswers(CompletableFuture.completedFuture(resolvedAction()));

            try (var server = started()) {
                assertThat(server.inline(prices, onTheConstant)).isEqualTo(inlined());

                assertThat(fake.codeActionRequests()).hasSize(1);
                CodeActionParams request = fake.codeActionRequests().getFirst();
                assertThat(request.getTextDocument().getUri())
                        .isEqualTo(prices.toUri().toString());
                assertThat(request.getRange()).isEqualTo(new Range(at(3, 21), at(3, 21)));
                assertThat(request.getContext().getOnly()).containsExactly("refactor.inline");
                assertThat(request.getContext().getDiagnostics()).isEmpty();
                assertThat(fake.resolveRequests()).hasSize(1);
                assertThat(fake.resolveRequests().getFirst().getData()).isEqualTo(new JsonPrimitive("jdtls-data"));
                assertThat(prices).hasContent(PRICES);
            }
        }

        @Test
        void usesTheEditOfTheActionWhenJdtlsGivesItWithoutBeingAskedToResolve() throws Exception {
            fake.codeActionAnswers(offers(resolvedAction()));

            try (var server = started()) {
                assertThat(server.inline(prices, onTheConstant)).isEqualTo(inlined());
                assertThat(fake.resolveRequests()).isEmpty();
            }
        }

        private void assertNotInlinable(JdtlsLanguageServer server) {
            assertThatThrownBy(() -> server.inline(prices, onTheConstant))
                    .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                        assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_INLINABLE);
                        assertThat(refusal.parameters())
                                .containsEntry("file", prices.toString())
                                .containsEntry("line", "3")
                                .containsEntry("column", "21");
                    });
        }

        @Test
        void refusesWhenJdtlsOffersNoInlineAction() throws Exception {
            fake.codeActionAnswers(CompletableFuture.completedFuture(List.of()));

            try (var server = started()) {
                assertNotInlinable(server);
                assertThat(fake.resolveRequests()).isEmpty();
            }
        }

        @Test
        void refusesWhenJdtlsAnswersNothing() throws Exception {
            fake.codeActionAnswers(CompletableFuture.completedFuture(null));

            try (var server = started()) {
                assertNotInlinable(server);
            }
        }

        /** What jdtls offers beside the inline action: all its refactorings, not only the kind that was asked for. */
        private CodeAction otherAction() {
            var action = new CodeAction("Change type to 'var'");
            action.setKind(CodeActionKind.RefactorRewrite);
            action.setEdit(new WorkspaceEdit(List.of(Either.forLeft(new TextDocumentEdit(
                    new VersionedTextDocumentIdentifier(prices.toUri().toString(), null),
                    List.of(Either.forLeft(new TextEdit(new Range(at(6, 8), at(6, 11)), "var"))))))));
            return action;
        }

        @Test
        void takesTheInlineActionAmongTheRefactoringsJdtlsOffersWhateverTheKindAsked() throws Exception {
            fake.codeActionAnswers(CompletableFuture.completedFuture(
                    List.of(Either.forRight(otherAction()), Either.forRight(resolvedAction()))));

            try (var server = started()) {
                assertThat(server.inline(prices, onTheConstant)).isEqualTo(inlined());
            }
        }

        @Test
        void skipsMakeStaticWhichJdtlsOffersWithTheKindOfTheInlinings() throws Exception {
            var makeStatic = otherAction();
            makeStatic.setTitle("Make static");
            makeStatic.setKind(CodeActionKind.RefactorInline);
            fake.codeActionAnswers(CompletableFuture.completedFuture(
                    List.of(Either.forRight(makeStatic), Either.forRight(resolvedAction()))));

            try (var server = started()) {
                assertThat(server.inline(prices, onTheConstant)).isEqualTo(inlined());
            }
        }

        @Test
        void refusesWhenJdtlsOffersOnlyOtherRefactorings() throws Exception {
            fake.codeActionAnswers(offers(otherAction()));

            try (var server = started()) {
                assertNotInlinable(server);
                assertThat(fake.resolveRequests()).isEmpty();
            }
        }

        @Test
        void refusesWhenJdtlsOffersOnlyACommand() throws Exception {
            fake.codeActionAnswers(CompletableFuture.completedFuture(
                    List.of(Either.forLeft(new Command("Inline", "java.apply.workspaceEdit")))));

            try (var server = started()) {
                assertNotInlinable(server);
            }
        }

        @Test
        void refusesWhenJdtlsDeclinesTheRequest() throws Exception {
            fake.codeActionAnswers(CompletableFuture.failedFuture(new ResponseErrorException(
                    new ResponseError(ResponseErrorCode.InvalidRequest, "cannot inline", null))));

            try (var server = started()) {
                assertNotInlinable(server);
            }
        }

        @Test
        void refusesWhenTheResolvedActionHasNoEdit() throws Exception {
            // What jdtls answers when the conditions of the refactoring fail once it is computed.
            fake.codeActionAnswers(offers(lazyAction()))
                    .resolveAnswers(CompletableFuture.completedFuture(lazyAction()));

            try (var server = started()) {
                assertNotInlinable(server);
            }
        }

        @Test
        void refusesWhenTheResolveIsDeclined() throws Exception {
            fake.codeActionAnswers(offers(lazyAction())).resolveAnswers(CompletableFuture.completedFuture(null));

            try (var server = started()) {
                assertNotInlinable(server);
            }
        }

        @Test
        void refusesAnEditThatChangesNothing() throws Exception {
            var action = lazyAction();
            action.setEdit(new WorkspaceEdit(List.of()));
            fake.codeActionAnswers(offers(action));

            try (var server = started()) {
                assertNotInlinable(server);
            }
        }

        @Test
        void refusesAFileOutsideTheProjectWithoutAskingJdtls() throws Exception {
            try (var server = started()) {
                assertThatThrownBy(() -> server.inline(root.resolve("../Elsewhere.java"), onTheConstant))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key())
                                        .isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));
                assertThat(fake.codeActionRequests()).isEmpty();
            }
        }

        @Test
        void refusesAFileThatDoesNotExistAndAFolderWithoutAskingJdtls() throws Exception {
            try (var server = started()) {
                assertThatThrownBy(() -> server.inline(root.resolve("src/main/java/demo/Nope.java"), onTheConstant))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.FILE_MISSING));
                assertThatThrownBy(() -> server.inline(root.resolve("src/main/java/demo"), onTheConstant))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_A_FILE));
                assertThat(fake.codeActionRequests()).isEmpty();
            }
        }

        @Test
        void refusesAsNotReadyWhileJdtlsIsStillImporting() throws Exception {
            fake.neverAnswersInitialize();

            try (var server = start()) {
                assertThatThrownBy(() -> server.inline(prices, onTheConstant))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
                assertThat(fake.codeActionRequests()).isEmpty();
            }
        }

        @Test
        void saysWhatWentWrongWhenJdtlsFailsInsteadOfDeclining() throws Exception {
            fake.codeActionAnswers(CompletableFuture.failedFuture(new IllegalStateException("the model is broken")));

            try (var server = started()) {
                assertThatThrownBy(() -> server.inline(prices, onTheConstant))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("waiting for the inlining of the symbol at Position[line=3, column=21]")
                        .hasMessageContaining("Internal error");
            }
        }

        @Test
        void keepsTheInterruptionOfTheThreadThatWaits() throws Exception {
            fake.codeActionAnswers(offers(resolvedAction()));

            try (var server = started()) {
                Thread.currentThread().interrupt();

                assertThatThrownBy(() -> server.inline(prices, onTheConstant))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining(
                                "Interrupted while waiting for the inlining of the symbol at Position[line=3,"
                                        + " column=21]");
                assertThat(Thread.interrupted()).isTrue();
            }
        }
    }

    @Nested
    class CreatingAType {

        private static final String SNIPPET = "package demo;\n\npublic class Widget {\n\n\t${0}\n}";

        private Path widget;
        private Path sources;

        @BeforeEach
        void chooseTheFile() {
            sources = root.resolve("src/main/java");
            widget = root.resolve("src/main/java/demo/Widget.java");
            fake.listsSourcePaths(sources);
        }

        private static CompletionItem snippet(String label, String text) {
            var item = new CompletionItem(label);
            item.setKind(CompletionItemKind.Snippet);
            item.setInsertText(text);
            return item;
        }

        private static CompletableFuture<Either<List<CompletionItem>, CompletionList>> items(CompletionItem... items) {
            return CompletableFuture.completedFuture(Either.forLeft(List.of(items)));
        }

        private static org.eclipse.lsp4j.Position at(int line, int column) {
            return new org.eclipse.lsp4j.Position(line, column);
        }

        @Test
        void opensTheFileEmptyWhereverItIsAndAsksForTheCompletionAtItsStartWithoutWritingIt() throws Exception {
            fake.completionAnswers(items(snippet("class", SNIPPET)));

            try (var server = started()) {
                server.createType(widget, TypeKind.CLASS);

                assertThat(fake.opened())
                        .containsExactly(new FakeJdtls.Opened(widget.toUri().toString(), ""));
                assertThat(fake.completionRequests()).hasSize(1);
                var request = fake.completionRequests().getFirst();
                assertThat(request.getTextDocument().getUri())
                        .isEqualTo(widget.toUri().toString());
                assertThat(request.getPosition()).isEqualTo(at(0, 0));
                assertThat(fake.closed(1)).containsExactly(widget.toUri().toString());
                assertThat(widget).doesNotExist();
            }
        }

        @Test
        void answersTheCreationOfTheFileAndTheOneEditThatGivesItsTextWithoutTheCursor() throws Exception {
            fake.completionAnswers(items(snippet("class", SNIPPET)));

            try (var server = started()) {
                ChangeSet changes = server.createType(widget, TypeKind.CLASS);

                assertThat(changes.changes()).hasSize(2);
                assertThat(changes.changes().get(0)).isEqualTo(new FileChange.CreateFile(widget));
                assertThat(changes.changes().get(1)).isInstanceOfSatisfying(FileChange.EditFile.class, edit -> {
                    assertThat(edit.file()).isEqualTo(widget);
                    assertThat(SourceText.edited(widget, "", edit.edits()))
                            .isEqualTo("package demo;\n\npublic class Widget {\n\n}\n");
                });
            }
        }

        @ParameterizedTest
        @CsvSource({"CLASS,class", "INTERFACE,interface", "RECORD,record", "ENUM,enum"})
        void takesTheSnippetOfTheKindAskedAmongTheOthers(TypeKind kind, String label) throws Exception {
            fake.completionAnswers(items(
                    snippet("class", "public class Widget {\n}"),
                    snippet("interface", "public interface Widget {\n}"),
                    snippet("record", "public record Widget(${0}) {\n}"),
                    snippet("enum", "public enum Widget {\n}")));

            try (var server = started()) {
                ChangeSet changes = server.createType(widget, kind);

                assertThat(changes.changes().get(1))
                        .isInstanceOfSatisfying(
                                FileChange.EditFile.class,
                                edit -> assertThat(edit.edits().getFirst().newText())
                                        .startsWith("public " + label + " Widget"));
            }
        }

        @Test
        void readsTheItemsOfACompletionList() throws Exception {
            fake.completionAnswers(CompletableFuture.completedFuture(
                    Either.forRight(new CompletionList(false, List.of(snippet("class", SNIPPET))))));

            try (var server = started()) {
                assertThat(server.createType(widget, TypeKind.CLASS).changes()).hasSize(2);
            }
        }

        @Test
        void ignoresAnItemThatIsNotTheSnippetOfTheKindOrHasNoText() throws Exception {
            var keyword = new CompletionItem("class");
            keyword.setKind(CompletionItemKind.Keyword);
            keyword.setInsertText("class");
            var withoutText = new CompletionItem("class");
            withoutText.setKind(CompletionItemKind.Snippet);
            fake.completionAnswers(items(keyword, withoutText, snippet("class", SNIPPET)));

            try (var server = started()) {
                ChangeSet changes = server.createType(widget, TypeKind.CLASS);

                assertThat(changes.changes().get(1))
                        .isInstanceOfSatisfying(
                                FileChange.EditFile.class,
                                edit -> assertThat(edit.edits().getFirst().newText())
                                        .contains("public class Widget"));
            }
        }

        @Test
        void refusesAKindJdtlsOffersNoSnippetFor() throws Exception {
            fake.completionAnswers(items(snippet("class", SNIPPET)));

            try (var server = started()) {
                assertThatThrownBy(() -> server.createType(widget, TypeKind.RECORD))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_CREATABLE);
                            assertThat(refusal.parameters())
                                    .containsEntry("file", widget.toString())
                                    .containsEntry("kind", "record");
                        });
                assertThat(fake.closed(1)).containsExactly(widget.toUri().toString());
            }
        }

        @Test
        void refusesWhenJdtlsAnswersNothing() throws Exception {
            fake.completionAnswers(CompletableFuture.completedFuture(null));

            try (var server = started()) {
                assertThatThrownBy(() -> server.createType(widget, TypeKind.CLASS))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_CREATABLE));
            }
        }

        @Test
        void refusesWhenJdtlsDeclinesTheRequest() throws Exception {
            fake.completionAnswers(CompletableFuture.failedFuture(new ResponseErrorException(
                    new ResponseError(ResponseErrorCode.InvalidRequest, "cannot complete", null))));

            try (var server = started()) {
                assertThatThrownBy(() -> server.createType(widget, TypeKind.CLASS))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_CREATABLE));
            }
        }

        @Test
        void refusesAFileOutsideEverySourceFolderWithoutOpeningItAndAsksJdtlsWhichTheyAre() throws Exception {
            fake.completionAnswers(items(snippet("class", SNIPPET)));

            try (var server = started()) {
                assertThatThrownBy(() -> server.createType(root.resolve("Unsourced.java"), TypeKind.CLASS))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_CREATABLE));
                assertThat(fake.sourcePathRequests()).isEqualTo(1);
                assertThat(fake.opened()).isEmpty();
                assertThat(fake.completionRequests()).isEmpty();
            }
        }

        @Test
        void acceptsAFileUnderAnyOfTheSourceFoldersAndNotOneThatOnlySharesTheStartOfTheName() throws Exception {
            fake.listsSourcePaths(root.resolve("src/main/java"), root.resolve("src/test/java"));
            fake.completionAnswers(items(snippet("class", "public class Widget {\n}")));

            try (var server = started()) {
                assertThat(server.createType(root.resolve("src/test/java/Widget.java"), TypeKind.CLASS)
                                .changes())
                        .hasSize(2);
                assertThatThrownBy(() -> server.createType(root.resolve("src/main/javax/Widget.java"), TypeKind.CLASS))
                        .isInstanceOf(LanguageServerException.class);
            }
        }

        @ParameterizedTest
        @ValueSource(
                strings = {"no workspace", "no data", "data without a path", "data that is no folder", "not a result"})
        void saysWhatJdtlsAnsweredWhenItDoesNotListTheSourceFoldersAsExpected(String shape) throws Exception {
            Object answer = switch (shape) {
                case "no workspace" -> Map.of("status", false, "message", "no workspace");
                case "no data" -> Map.of("status", true);
                case "data without a path" -> Map.of("status", true, "data", List.of(Map.of("projectName", "p")));
                case "data that is no folder" -> Map.of("status", true, "data", List.of("src/main/java"));
                default -> "nothing";
            };
            fake.sourcePathsAnswers(CompletableFuture.completedFuture(answer));

            try (var server = started()) {
                assertThatThrownBy(() -> server.createType(widget, TypeKind.CLASS))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("java.project.listSourcePaths answered")
                        .hasMessageContaining(String.valueOf(answer));
            }
        }

        @Test
        void refusesAFileOutsideTheProjectWithoutAskingJdtlsAnything() throws Exception {
            try (var server = started()) {
                assertThatThrownBy(() -> server.createType(root.resolve("../Elsewhere.java"), TypeKind.CLASS))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key())
                                        .isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));
                assertThat(fake.sourcePathRequests()).isZero();
                assertThat(fake.completionRequests()).isEmpty();
            }
        }

        @Test
        void refusesAsNotReadyWhileJdtlsIsStillImporting() throws Exception {
            fake.neverAnswersInitialize();

            try (var server = start()) {
                assertThatThrownBy(() -> server.createType(widget, TypeKind.CLASS))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
                assertThat(fake.completionRequests()).isEmpty();
            }
        }

        @Test
        void saysWhatWentWrongWhenJdtlsFailsInsteadOfDeclining() throws Exception {
            fake.completionAnswers(CompletableFuture.failedFuture(new IllegalStateException("the model is broken")));

            try (var server = started()) {
                assertThatThrownBy(() -> server.createType(widget, TypeKind.CLASS))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("waiting for the snippet of a class for " + widget)
                        .hasMessageContaining("Internal error");
                assertThat(fake.closed(1)).containsExactly(widget.toUri().toString());
            }
        }

        @Test
        void keepsTheInterruptionOfTheThreadThatWaits() throws Exception {
            fake.completionAnswers(items(snippet("class", SNIPPET)));

            try (var server = started()) {
                Thread.currentThread().interrupt();

                assertThatThrownBy(() -> server.createType(widget, TypeKind.CLASS))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("Interrupted while waiting for the snippet of a class for " + widget);
                assertThat(Thread.interrupted()).isTrue();
            }
        }
    }

    @Nested
    class Referencing {

        private static final String LEDGER = """
            package demo;

            public class Ledger {
                int balance;

                void reset() {
                    balance = 0;
                }
            }
            """;

        private Path ledger;
        private Path bank;

        @BeforeEach
        void writeTheSources() throws IOException {
            ledger = write("src/main/java/demo/Ledger.java", LEDGER);
            bank = write("src/main/java/demo/Bank.java", "package demo;\n\nclass Bank {\n    Ledger ledger;\n}\n");
        }

        private CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> renamable() {
            var range = new Range(new org.eclipse.lsp4j.Position(3, 8), new org.eclipse.lsp4j.Position(3, 15));
            return CompletableFuture.completedFuture(Either3.forFirst(range));
        }

        private Location at(Path file, int line, int column) {
            var start = new org.eclipse.lsp4j.Position(line, column);
            var end = new org.eclipse.lsp4j.Position(line, column + 7);

            return new Location(file.toUri().toString(), new Range(start, end));
        }

        private CompletableFuture<List<? extends Location>> found(Location... locations) {
            return CompletableFuture.completedFuture(List.of(locations));
        }

        private fr.craft.gabier.editing.domain.Location place(Path file, int line, int column) {
            return new fr.craft.gabier.editing.domain.Location(file, new Position(line, column));
        }

        private CompletableFuture<List<? extends Location>> unanswered() {
            return new CompletableFuture<>();
        }

        @Test
        void asksWhetherThereIsASymbolThenForTheReferencesAndAnswersTheirPositionsInTheTermsOfTheDomain()
                throws Exception {
            fake.prepareRenameAnswers(renamable()).referencesAnswers(false, found(at(bank, 3, 4), at(ledger, 6, 8)));

            try (var server = started()) {
                var found = server.references(ledger, new Position(3, 8), false);

                assertThat(found.declarations()).isEmpty();
                assertThat(found.references()).containsExactly(place(bank, 3, 4), place(ledger, 6, 8));
                assertThat(fake.prepared()).hasSize(1);
                assertThat(fake.prepared().getFirst().getTextDocument().getUri())
                        .isEqualTo(ledger.toUri().toString());
                assertThat(fake.prepared().getFirst().getPosition()).isEqualTo(new org.eclipse.lsp4j.Position(3, 8));
                assertThat(fake.referenced()).hasSize(1);
                var request = fake.referenced().getFirst();
                assertThat(request.getTextDocument().getUri())
                        .isEqualTo(ledger.toUri().toString());
                assertThat(request.getPosition()).isEqualTo(new org.eclipse.lsp4j.Position(3, 8));
                assertThat(request.getContext().isIncludeDeclaration()).isFalse();
                assertThat(fake.opened()).isEmpty();
            }
        }

        @Test
        void putsTheLocationsInTheOrderOfTheFilesThenOfThePositionsWhateverTheOrderOfJdtls() throws Exception {
            fake.prepareRenameAnswers(renamable())
                    .referencesAnswers(false, found(at(ledger, 6, 8), at(bank, 3, 4), at(ledger, 3, 8)));

            try (var server = started()) {
                var found = server.references(ledger, new Position(3, 8), false);

                assertThat(found.references())
                        .containsExactly(place(bank, 3, 4), place(ledger, 3, 8), place(ledger, 6, 8));
            }
        }

        @Test
        void asksAgainWithTheDeclarationAndAnswersWhatIsNewAsTheDeclaration() throws Exception {
            fake.prepareRenameAnswers(renamable())
                    .referencesAnswers(false, found(at(bank, 3, 4), at(ledger, 6, 8)))
                    .referencesAnswers(true, found(at(ledger, 3, 8), at(bank, 3, 4), at(ledger, 6, 8)));

            try (var server = started()) {
                var found = server.references(bank, new Position(3, 4), true);

                assertThat(found.declarations()).containsExactly(place(ledger, 3, 8));
                assertThat(found.references()).containsExactly(place(bank, 3, 4), place(ledger, 6, 8));
                assertThat(fake.referenced())
                        .extracting(request -> request.getContext().isIncludeDeclaration())
                        .containsExactly(false, true);
            }
        }

        @Test
        void answersNothingForASymbolNothingUsesAndForAnAnswerOfNull() throws Exception {
            fake.prepareRenameAnswers(renamable())
                    .referencesAnswers(false, CompletableFuture.completedFuture(null))
                    .referencesAnswers(true, found(at(ledger, 3, 8)));

            try (var server = started()) {
                var found = server.references(ledger, new Position(3, 8), true);

                assertThat(found.references()).isEmpty();
                assertThat(found.declarations()).containsExactly(place(ledger, 3, 8));
            }
        }

        @Test
        void leavesOutWhatIsNotAFileOfTheProject() throws Exception {
            var inAJar = new Location(
                    "jdt://contents/rt.jar/java.lang/String.class",
                    new Range(new org.eclipse.lsp4j.Position(0, 0), new org.eclipse.lsp4j.Position(0, 1)));
            fake.prepareRenameAnswers(renamable()).referencesAnswers(false, found(inAJar, at(bank, 3, 4)));

            try (var server = started()) {
                var found = server.references(ledger, new Position(3, 8), false);

                assertThat(found.references()).containsExactly(place(bank, 3, 4));
            }
        }

        @Test
        void tellsJdtlsOfTheFilesEditedSinceTheLastRequestBeforeAskingForTheReferences() throws Exception {
            fake.registersWatchersFor("**/*.java")
                    .prepareRenameAnswers(renamable())
                    .referencesAnswers(false, found());

            try (var server = started()) {
                Files.writeString(bank, "package demo;\n\nclass Bank {\n    Ledger other;\n}\n");

                server.references(ledger, new Position(3, 8), false);

                assertThat(fake.received())
                        .containsSubsequence(
                                "didChangeWatchedFiles", "prepareRename Ledger.java", "references Ledger.java");
            }
        }

        @Test
        void refusesWhereJdtlsHasNothingToRenameAsNoSymbolWithoutAskingForTheReferences() throws Exception {
            fake.prepareRenameAnswers(declined("Renaming this element is not supported."));

            try (var server = started()) {
                assertThatThrownBy(() -> server.references(ledger, new Position(4, 0), false))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NO_SUCH_SYMBOL);
                            assertThat(refusal.parameters())
                                    .containsEntry("file", ledger.toString())
                                    .containsEntry("line", "4")
                                    .containsEntry("column", "0");
                        });
                assertThat(fake.referenced()).isEmpty();
            }
        }

        @Test
        void refusesAsNoSymbolWhenJdtlsAnswersNullToThePreparation() throws Exception {
            fake.prepareRenameAnswers(CompletableFuture.completedFuture(null));

            try (var server = started()) {
                assertThatThrownBy(() -> server.references(ledger, new Position(4, 0), false))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NO_SUCH_SYMBOL));
                assertThat(fake.referenced()).isEmpty();
            }
        }

        @Test
        void refusesAFileOutsideTheProjectWithoutAskingJdtls() throws Exception {
            Path outside = root.resolveSibling("outside").resolve("Elsewhere.java");

            try (var server = started()) {
                assertThatThrownBy(() -> server.references(outside, new Position(0, 0), false))
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
                assertThatThrownBy(() -> server.references(ledger, new Position(3, 8), false))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
                assertThat(fake.prepared()).isEmpty();
            }
        }

        @Test
        void saysWhatWentWrongWhenJdtlsFailsInsteadOfAnswering() throws Exception {
            fake.prepareRenameAnswers(renamable())
                    .referencesAnswers(
                            false, CompletableFuture.failedFuture(new IllegalStateException("the index is broken")));

            try (var server = started()) {
                assertThatThrownBy(() -> server.references(ledger, new Position(3, 8), false))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("waiting for the references of the symbol at")
                        .hasMessageContaining("Internal error");
            }
        }

        @Test
        void failsWhenJdtlsDoesNotAnswerInTime() throws Exception {
            fake.prepareRenameAnswers(renamable()).referencesAnswers(false, unanswered());

            try (var server = start(Duration.ofSeconds(1))) {
                server.awaitStarted();

                assertThatThrownBy(() -> server.references(ledger, new Position(3, 8), false))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("Timed out")
                        .hasMessageContaining("references");
            }
        }

        @Test
        void keepsTheInterruptionOfTheThreadThatWaits() throws Exception {
            fake.prepareRenameAnswers(renamable()).referencesAnswers(false, found());

            try (var server = started()) {
                Thread.currentThread().interrupt();

                assertThatThrownBy(() -> server.references(ledger, new Position(3, 8), false))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("Interrupted while waiting for the references");
                assertThat(Thread.interrupted()).isTrue();
            }
        }

        private <T> CompletableFuture<T> declined(String message) {
            return CompletableFuture.failedFuture(
                    new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidRequest, message, null)));
        }
    }
}
