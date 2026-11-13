package eu.gabier.daemon;

import static eu.gabier.daemon.FakeOnlyReasons.A_START_THAT_LASTS;
import static eu.gabier.daemon.FakeOnlyReasons.INTERRUPTED_WHILE_WAITING;
import static eu.gabier.daemon.FakeOnlyReasons.JDTLS_DIED;
import static eu.gabier.daemon.FakeOnlyReasons.JDTLS_FAILS;
import static eu.gabier.daemon.FakeOnlyReasons.MALFORMED_ANSWER;
import static eu.gabier.daemon.FakeOnlyReasons.NOTHING_IS_THE_ONLY_ANSWER;
import static eu.gabier.daemon.FakeOnlyReasons.NO_ANSWER_IN_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import com.google.gson.JsonPrimitive;
import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.editing.domain.Diagnostic.Severity;
import eu.gabier.editing.domain.EditingErrorKey;
import eu.gabier.editing.domain.EditingException;
import eu.gabier.editing.domain.FileChange;
import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.SourceText;
import eu.gabier.editing.domain.TypeKind;
import eu.gabier.editing.fixtures.FakeOnly;
import eu.gabier.protocol.JdtlsProgress;
import eu.gabier.protocol.JdtlsState;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
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
import org.eclipse.lsp4j.LocationLink;
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
import org.junit.jupiter.api.extension.ExtendWith;
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
@ExtendWith(FakeOnlyGuard.class)
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
        var settings = new JdtlsSettings(
                root, List.of(root), timeout, Duration.ofMillis(20), Duration.ofSeconds(5), JdtlsWorkspace.temporary());
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

                assertThat(answers).hasSize(2);
                assertThat(answers.get(0)).isEmpty();
                assertThat(answers.get(1)).hasSize(1);
                assertThat(fake.opened()).hasSize(2);
                assertThat(fake.closed(2)).hasSize(2);
            }
        }

        @Test
        void asksSeveralFilesTogetherSoThatTheyShareTheWaitForJdtlsToBeQuiet() throws Exception {
            fake.publishesOnOpen(clean, List.of()).publishesOnOpen(broken, List.of());

            try (var server = started()) {
                server.diagnostics(List.of(clean, broken));

                // One wait: both files are open when it ends, and only then closed. One wait each would close the
                // first before the second is opened.
                assertThat(fake.documentEvents(2))
                        .containsExactly(
                                "open Clean.java", "open Broken.java", "close Clean.java", "close Broken.java");
            }
        }

        @Test
        void answersAFileGivenTwiceTwiceAndOpensItOnce() throws Exception {
            fake.publishesOnOpen(broken, List.of(lsp(0, 0, DiagnosticSeverity.Error, "m")))
                    .publishesOnOpen(clean, List.of());

            try (var server = started()) {
                var answers = server.diagnostics(
                        List.of(broken, clean, root.resolve("src/main/../main/java/demo/./Broken.java")));

                assertThat(answers).hasSize(3);
                assertThat(answers.get(0)).hasSize(1);
                assertThat(answers.get(1)).isEmpty();
                assertThat(answers.get(2)).isEqualTo(answers.get(0));
                assertThat(fake.opened()).hasSize(2);
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

        @FakeOnly(reason = INTERRUPTED_WHILE_WAITING)
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

        @FakeOnly(reason = NO_ANSWER_IN_TIME)
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

        @FakeOnly(reason = NO_ANSWER_IN_TIME)
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

        @FakeOnly(reason = NO_ANSWER_IN_TIME)
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

        @FakeOnly(reason = JDTLS_DIED)
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

        @FakeOnly(reason = JDTLS_DIED)
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
        void saysWhatJdtlsReportsWhileItStartsAndNothingOnceItIsReady() throws Exception {
            fake.sendsStatus("")
                    .reportsWhileStarting(
                            "Init...",
                            "45% Starting Java Language Server",
                            "70% Starting Java Language Server - Configure project :core");

            try (var server = start()) {
                awaitProgress(server, JdtlsProgress.Phase.IMPORTING);

                assertThat(server.progress())
                        .hasValueSatisfying(
                                progress -> assertThat(progress.message()).isEqualTo("Configure project :core"));
            }
        }

        @Test
        void saysItIsLaunchingBeforeJdtlsHasReportedAnything() throws Exception {
            fake.neverAnswersInitialize();

            try (var server = start()) {
                assertThat(server.progress())
                        .hasValueSatisfying(
                                progress -> assertThat(progress.phase()).isEqualTo(JdtlsProgress.Phase.LAUNCHING));
            }
        }

        @Test
        void saysNothingOfItsProgressOnceJdtlsIsReady() throws Exception {
            fake.reportsWhileStarting("70% Starting Java Language Server - Build");

            try (var server = start()) {
                server.awaitReady();

                assertThat(server.progress()).isEmpty();
            }
        }

        private void awaitProgress(JdtlsLanguageServer server, JdtlsProgress.Phase phase) throws InterruptedException {
            for (var attempt = 0;
                    attempt < 1500
                            && !server.progress().map(JdtlsProgress::phase).equals(Optional.of(phase));
                    attempt++) {
                Thread.sleep(10);
            }
            assertThat(server.progress().map(JdtlsProgress::phase)).contains(phase);
        }

        @Test
        void isReadyOnceJdtlsHasImported() throws Exception {
            try (var server = start()) {
                server.awaitReady();

                assertThat(server.state()).isEqualTo(JdtlsState.READY);
                assertThat(server.failure()).isEmpty();
            }
        }

        @FakeOnly(reason = JDTLS_DIED)
        @Test
        void isFailedWhenJdtlsDiedAfterItWasReady() throws Exception {
            try (var server = started()) {
                fake.process().destroyForcibly().onExit().get();

                assertThat(server.state()).isEqualTo(JdtlsState.FAILED);
            }
        }

        @FakeOnly(reason = JDTLS_DIED)
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

        @FakeOnly(reason = JDTLS_DIED)
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

        @FakeOnly(reason = JDTLS_DIED)
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

        private FileChange domainEdits(Path file, eu.gabier.editing.domain.TextEdit... edits) {
            return new FileChange.EditFile(file, List.of(edits));
        }

        private eu.gabier.editing.domain.TextEdit domainReplace(int line, int from, int to, String text) {
            return new eu.gabier.editing.domain.TextEdit(
                    new eu.gabier.editing.domain.Range(new Position(line, from), new Position(line, to)), text);
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

        @FakeOnly(reason = A_START_THAT_LASTS)
        @Test
        void asksJdtlsNothingBeforeItHasRegisteredTheFilesItWatchesThenRenames() throws Exception {
            fake.holdsBackTheWatchers()
                    .registersWatchersFor("**/*.java")
                    .prepareRenameAnswers(renamable())
                    .renameAnswers(CompletableFuture.completedFuture(edit(editsOf(greeter, replace(1, 9, 14, "hi")))));

            try (var server = started()) {
                var answer = CompletableFuture.supplyAsync(() -> server.rename(greeter, new Position(1, 10), "hi"));

                LockSupport.parkNanos(Duration.ofMillis(500).toNanos());
                assertThat(answer).isNotDone();
                assertThat(fake.received()).doesNotContain("prepareRename Greeter.java");

                fake.registersTheHeldBackWatchers();

                assertThat(answer.get(10, TimeUnit.SECONDS))
                        .isEqualTo(new ChangeSet(List.of(domainEdits(greeter, domainReplace(1, 9, 14, "hi")))));
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
        void asksForTheRenameOfAPackageNameEvenThoughJdtlsDeclinesToPrepareIt() throws Exception {
            Path ruler = write("src/main/java/legacy/tools/Ruler.java", "package legacy.tools;\n\nclass Ruler {}\n");
            Path moved = root.resolve("src/main/java/tools/Ruler.java");
            Path temp = root.resolve("src/main/java/tools/.temp");
            var answer = new WorkspaceEdit(List.of(
                    Either.<TextDocumentEdit, ResourceOperation>forLeft(editsOf(ruler, replace(0, 8, 20, "tools"))),
                    Either.<TextDocumentEdit, ResourceOperation>forRight(
                            new org.eclipse.lsp4j.CreateFile(temp.toUri().toString())),
                    Either.<TextDocumentEdit, ResourceOperation>forRight(new org.eclipse.lsp4j.RenameFile(
                            ruler.toUri().toString(), moved.toUri().toString())),
                    Either.<TextDocumentEdit, ResourceOperation>forRight(
                            new org.eclipse.lsp4j.DeleteFile(temp.toUri().toString()))));
            fake.prepareRenameAnswers(declined("Renaming this element is not supported."))
                    .renameAnswers(CompletableFuture.completedFuture(answer));

            try (var server = started()) {
                assertThat(server.rename(ruler, new Position(0, 18), "tools").changes())
                        .containsExactly(
                                domainEdits(ruler, domainReplace(0, 8, 20, "tools")),
                                new FileChange.RenameFile(ruler, moved));
                assertThat(fake.prepared()).hasSize(1);
                assertThat(fake.renamed()).hasSize(1);
                assertThat(fake.renamed().getFirst().getNewName()).isEqualTo("tools");
                assertThat(fake.renamed().getFirst().getPosition()).isEqualTo(new org.eclipse.lsp4j.Position(0, 18));
            }
        }

        @ParameterizedTest
        @CsvSource({"8,legacy", "13,legacy", "15,legacy.tools", "19,legacy.tools"})
        void refusesAPackageThatHoldsNoClassWhereJdtlsAnswersTheRenameOfThePackageWithNoChange(
                int column, String emptyPackage) throws Exception {
            Path ruler = write("src/main/java/legacy/tools/Ruler.java", "package legacy.tools;\n\nclass Ruler {}\n");
            fake.prepareRenameAnswers(declined("Renaming this element is not supported."))
                    .renameAnswers(CompletableFuture.completedFuture(edit()));

            try (var server = started()) {
                assertThatThrownBy(() -> server.rename(ruler, new Position(0, column), "elsewhere.tools"))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.EMPTY_PACKAGE);
                            assertThat(refusal.parameters())
                                    .containsEntry("file", ruler.toString())
                                    .containsEntry("line", "0")
                                    .containsEntry("column", String.valueOf(column))
                                    .containsEntry("package", emptyPackage)
                                    .containsEntry("declaredPackage", "legacy.tools");
                        });
            }
        }

        @Test
        void refusesAnEmptyRenameOfASymbolThatIsNotAPackageAsNoSymbol() throws Exception {
            fake.prepareRenameAnswers(renamable()).renameAnswers(CompletableFuture.completedFuture(edit()));

            try (var server = started()) {
                assertThatThrownBy(() -> server.rename(greeter, new Position(1, 10), "hello"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NO_SUCH_SYMBOL));
            }
        }

        @ParameterizedTest
        @CsvSource({"0,0", "0,7", "0,20", "0,21", "2,0", "9,0"})
        void doesNotAskForTheRenameWhereJdtlsDeclinesToPrepareItOutsideTheNameOfAPackageLine(int line, int column)
                throws Exception {
            Path ruler = write("src/main/java/legacy/tools/Ruler.java", "package legacy.tools;\n\nclass Ruler {}\n");
            fake.prepareRenameAnswers(declined("Renaming this element is not supported."));

            try (var server = started()) {
                assertThatThrownBy(() -> server.rename(ruler, new Position(line, column), "tools"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NO_SUCH_SYMBOL));
                assertThat(fake.renamed()).isEmpty();
            }
        }

        @ParameterizedTest
        @ValueSource(
                strings = {"// package legacy.tools;", "String s = \"package legacy.tools;\";", "packages legacy.tools;"
                })
        void doesNotTakeALineThatIsNotAPackageDeclarationForOne(String line) throws Exception {
            Path ruler = write("src/main/java/legacy/tools/Ruler.java", line + "\n");
            fake.prepareRenameAnswers(declined("Renaming this element is not supported."));

            try (var server = started()) {
                assertThatThrownBy(() -> server.rename(ruler, new Position(0, line.length() - 3), "tools"))
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
        void refusesWithWhatJdtlsSaysWhenItAnswersAnInternalErrorAndKeepsItsDataAsTheCause() throws Exception {
            var data = "java.lang.NullPointerException: element is null";
            var error = new ResponseErrorException(
                    new ResponseError(ResponseErrorCode.InternalError, "Internal error.", data));
            fake.prepareRenameAnswers(renamable()).renameAnswers(CompletableFuture.failedFuture(error));

            try (var server = started()) {
                assertThatThrownBy(() -> server.rename(greeter, new Position(1, 10), "hello"))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.REQUEST_FAILED);
                            assertThat(refusal.parameters()).containsOnly(entry("code", "-32603"));
                            assertThat(refusal)
                                    .hasMessage("The language server failed on the request, with its error -32603:"
                                            + " Internal error.");
                            assertThat(refusal.getCause())
                                    .isInstanceOfSatisfying(
                                            ResponseErrorException.class,
                                            cause -> assertThat(cause.getResponseError()
                                                            .getData()
                                                            .toString())
                                                    .contains(data));
                        });
            }
        }

        @FakeOnly(reason = JDTLS_FAILS)
        @Test
        void saysWhatWentWrongWhenJdtlsFailsInsteadOfDeclining() throws Exception {
            var failure = new CompletableFuture<WorkspaceEdit>();
            failure.completeExceptionally(new IllegalStateException("the model is broken"));
            fake.prepareRenameAnswers(renamable()).renameAnswers(failure);

            try (var server = started()) {
                assertThatThrownBy(() -> server.rename(greeter, new Position(1, 10), "hello"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.REQUEST_FAILED))
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

        @FakeOnly(reason = NO_ANSWER_IN_TIME)
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

        @FakeOnly(reason = INTERRUPTED_WHILE_WAITING)
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
            greeter = write("src/main/java/demo/Greeter.java", """
                package demo;

                import java.util.Set;
                import java.util.List;

                class Greeter {
                    List<String> names;
                    Set<String> seen;
                }
                """);
            // An import that jdtls removes and the file still uses is judged by the diagnostics of the file: none,
            // unless a test says.
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
                    List.of(new eu.gabier.editing.domain.TextEdit(
                            new eu.gabier.editing.domain.Range(new Position(2, 0), new Position(3, 0)), "")))));
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
                // The text ends with a newline: its last line, the empty one, is line 9.
                assertThat(server.organizeImports(greeter))
                        .contains(new ChangeSet(List.of(new FileChange.EditFile(
                                greeter,
                                List.of(new eu.gabier.editing.domain.TextEdit(
                                        new eu.gabier.editing.domain.Range(new Position(2, 0), new Position(9, 0)),
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
        void doesNotWaitForTheDiagnosticsWhenTheImportItRemovesIsNotUsed() throws Exception {
            Path unused = write("src/main/java/demo/Unused.java", """
                package demo;

                import java.util.Set;
                import java.util.List;

                class Unused {
                    List<String> names;
                    String label = "Setter";
                }
                """);
            var action = lazyAction();
            action.setEdit(new WorkspaceEdit(Map.of(unused.toUri().toString(), List.of(removeSet()))));
            // No diagnostics are scripted: a request for them would wait, and fail on the missing answer.
            fake.codeActionAnswers(offers(action));

            try (var server = started()) {
                assertThat(server.organizeImports(unused)).isPresent();

                assertThat(fake.opened()).isEmpty();
            }
        }

        @Test
        void organizesAnUnusedImportOfAClassThatDoesNotExist() throws Exception {
            Path gone = write("src/main/java/demo/Keeper.java", """
                package demo;

                import demo.Gone;
                import java.util.List;

                class Keeper {
                    List<String> names;
                }
                """);
            var action = lazyAction();
            action.setEdit(new WorkspaceEdit(Map.of(gone.toUri().toString(), List.of(removeSet()))));
            fake.codeActionAnswers(offers(action)).publishesOnOpen(gone, List.of(problem(DiagnosticSeverity.Error, 2)));

            try (var server = started()) {
                assertThat(server.organizeImports(gone)).isPresent();

                assertThat(fake.opened()).isEmpty();
            }
        }

        @Test
        void asksTheDiagnosticsWhenTheNameOfARemovedStarImportCannotBeTold() throws Exception {
            Path star = write("src/main/java/demo/Star.java", """
                package demo;

                import java.util.*;
                import java.util.List;

                class Star {
                    List<String> names;
                }
                """);
            var action = lazyAction();
            action.setEdit(new WorkspaceEdit(Map.of(star.toUri().toString(), List.of(removeSet()))));
            fake.codeActionAnswers(offers(action)).publishesOnOpen(star, List.of(problem(DiagnosticSeverity.Error, 2)));

            try (var server = started()) {
                assertThatThrownBy(() -> server.organizeImports(star))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.parameters()).containsEntry("imports", "java.util.*"));
            }
        }

        @Test
        void ignoresTheErrorOnARemovedImportTheFileDoesNotUseWhenAnotherOneIsUsed() throws Exception {
            Path mixed = write("src/main/java/demo/Mixed.java", """
                package demo;

                import java.util.Set;
                import java.util.Map;
                import java.util.List;

                class Mixed {
                    List<String> names;
                    Set<String> seen;
                }
                """);
            var action = lazyAction();
            action.setEdit(new WorkspaceEdit(Map.of(
                    mixed.toUri().toString(),
                    List.of(new TextEdit(
                            new Range(new org.eclipse.lsp4j.Position(2, 0), new org.eclipse.lsp4j.Position(4, 0)),
                            "")))));
            fake.codeActionAnswers(offers(action))
                    .publishesOnOpen(
                            mixed, List.of(problem(DiagnosticSeverity.Error, 2), problem(DiagnosticSeverity.Error, 3)));

            try (var server = started()) {
                assertThatThrownBy(() -> server.organizeImports(mixed))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.parameters()).containsEntry("imports", "java.util.Set"));
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
            Path statics = write("src/main/java/demo/Statics.java", """
                package demo;

                import static java.util.Map.entry;
                import java.util.Set;
                import java.util.List;

                class Statics {
                    List<String> names;
                    Object pair = entry("a", "b");
                    Set<String> seen;
                }
                """);
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

        @FakeOnly(reason = JDTLS_FAILS)
        @Test
        void saysWhatWentWrongWhenJdtlsFailsInsteadOfDeclining() throws Exception {
            var failure = new CompletableFuture<List<Either<Command, CodeAction>>>();
            failure.completeExceptionally(new IllegalStateException("the model is broken"));
            fake.codeActionAnswers(failure);

            try (var server = started()) {
                assertThatThrownBy(() -> server.organizeImports(greeter))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.REQUEST_FAILED))
                        .hasMessageContaining("Internal error");
            }
        }

        @FakeOnly(reason = NO_ANSWER_IN_TIME)
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

        @FakeOnly(reason = NO_ANSWER_IN_TIME)
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

        @FakeOnly(reason = INTERRUPTED_WHILE_WAITING)
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

        private final eu.gabier.editing.domain.Range selection =
                new eu.gabier.editing.domain.Range(new Position(2, 15), new Position(2, 31));
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

                var edit = new eu.gabier.editing.domain.TextEdit(
                        new eu.gabier.editing.domain.Range(new Position(2, 8), new Position(2, 31)),
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

        @FakeOnly(reason = MALFORMED_ANSWER)
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

        @FakeOnly(reason = MALFORMED_ANSWER)
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

        @FakeOnly(reason = MALFORMED_ANSWER)
        @Test
        void failsWhenTheExtractionEditsAnotherFile() throws Exception {
            fake.refactorEditAnswers(proposal(editsAnotherFile()));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractVariable(greeter, selection, "text"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("does more than edit " + greeter);
            }
        }

        @FakeOnly(reason = MALFORMED_ANSWER)
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

        @FakeOnly(reason = MALFORMED_ANSWER)
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

        @FakeOnly(reason = JDTLS_FAILS)
        @Test
        void saysWhatWentWrongWhenJdtlsFailsInsteadOfDeclining() throws Exception {
            fake.refactorEditAnswers(CompletableFuture.failedFuture(new IllegalStateException("the model is broken")));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractVariable(greeter, selection, "text"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.REQUEST_FAILED))
                        .hasMessageContaining("Internal error");
            }
        }

        @FakeOnly(reason = NO_ANSWER_IN_TIME)
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

        @FakeOnly(reason = NO_ANSWER_IN_TIME)
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

        @FakeOnly(reason = NO_ANSWER_IN_TIME)
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

        @FakeOnly(reason = INTERRUPTED_WHILE_WAITING)
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

        private final eu.gabier.editing.domain.Range selection =
                new eu.gabier.editing.domain.Range(new Position(2, 15), new Position(2, 28));
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

        /**
         * A symbol as jdtls lists it for the text it was given: the label is {@code name(parameters)}, or {@code
         * name(parameters) <type parameters>}, with no return type, and the range is that of the name, on one line.
         */
        private Either<SymbolInformation, DocumentSymbol> symbol(
                String label, SymbolKind kind, String container, int line) {
            return Either.forLeft(new SymbolInformation(
                    label, kind, new Location(totals.toUri().toString(), nameRange(label, line)), container));
        }

        private static Range nameRange(String label, int line) {
            int length = label.indexOf('(') < 0 ? label.length() : label.indexOf('(');

            return new Range(at(line, 8), at(line, 8 + length));
        }

        private Either<SymbolInformation, DocumentSymbol> method(String label, String container, int line) {
            return symbol(label, SymbolKind.Method, container, line);
        }

        private Either<SymbolInformation, DocumentSymbol> type(String name, String container, int line) {
            return symbol(name, SymbolKind.Class, container, line);
        }

        /** What jdtls lists, in the order of its outline: the members of a type, then the type. */
        @SafeVarargs
        private static CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> listing(
                Either<SymbolInformation, DocumentSymbol>... symbols) {
            return CompletableFuture.completedFuture(List.of(symbols));
        }

        /** The methods of {@code Totals}, the new one declared on the line 5, and then the class. */
        private CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> members(String... methods) {
            var listed = new ArrayList<Either<SymbolInformation, DocumentSymbol>>();
            int line = 1;
            for (String method : methods) {
                listed.add(method(method, "Totals", line));
                line += 4;
            }
            listed.add(type("Totals", "", 0));

            return CompletableFuture.completedFuture(listed);
        }

        /** The answer to the definition of the new name: the declaration of the method, on that line. */
        private CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> declaredOnLine(
                int line) {
            var declaration = new Location(totals.toUri().toString(), nameRange("extracted(int)", line));

            return CompletableFuture.completedFuture(Either.forLeft(List.of(declaration)));
        }

        private void scriptTheExtraction(String name) {
            fake.refactorEditAnswers(proposal())
                    .publishesOnOpen(totals, List.of())
                    .documentSymbolAnswers(members("quadrupled(int)", "extracted(int)", "doubled(int)"))
                    .definitionAnswers(declaredOnLine(5))
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
                assertThat(fake.definitionRequests()).hasSize(1);
                assertThat(fake.definitionRequests().getFirst().getPosition()).isEqualTo(at(2, 15));
                assertThat(fake.received())
                        .containsExactly(
                                "getRefactorEdit Totals.java",
                                "didOpen Totals.java",
                                "definition Totals.java",
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
                assertThat(fake.definitionRequests()).isEmpty();
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

        /** Scripts the extraction in a text that has the symbols listed, the new method being declared on that line. */
        private void scriptTheExtractionIn(List<Either<SymbolInformation, DocumentSymbol>> listed, int declaredOnLine) {
            fake.refactorEditAnswers(proposal())
                    .publishesOnOpen(totals, List.of())
                    .documentSymbolAnswers(CompletableFuture.completedFuture(listed))
                    .definitionAnswers(declaredOnLine(declaredOnLine))
                    .renameAnswers(CompletableFuture.completedFuture(renamesTo("doubled")));
        }

        private void assertRefused() throws Exception {
            try (var server = started()) {
                assertThatThrownBy(() -> server.extractMethod(totals, selection, "doubled"))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.INVALID_NAME));
            }
        }

        private void assertAccepted() throws Exception {
            try (var server = started()) {
                ChangeSet changes = server.extractMethod(totals, selection, "doubled");

                assertThat(applied(ORIGINAL, changes)).isEqualTo(PROPOSED.replace("extracted", "doubled"));
            }
        }

        @Test
        void refusesTheNameOfAGenericMethodWhoseLabelCarriesItsTypeParametersAfterTheParameters() throws Exception {
            scriptTheExtractionIn(
                    List.of(
                            method("doubled(T) <T>", "Totals", 1),
                            method("extracted(T) <T>", "Totals", 5),
                            type("Totals", "", 0)),
                    5);

            assertRefused();
        }

        @Test
        void acceptsTheNameOfAMethodThatTheClassHasWithOtherParametersWhereverTheExtractedOneIsListed()
                throws Exception {
            scriptTheExtractionIn(
                    List.of(
                            method("tripled(int)", "Totals", 9),
                            method("doubled(String)", "Totals", 1),
                            method("extracted(int)", "Totals", 5),
                            type("Totals", "", 0)),
                    5);

            assertAccepted();
        }

        @Test
        void acceptsTheNameOfAMethodOfAnotherClassAndOfAFieldWithTheSameParameters() throws Exception {
            scriptTheExtractionIn(
                    List.of(
                            method("doubled(int)", "Other", 1),
                            type("Other", "", 0),
                            symbol("doubled(int)", SymbolKind.Field, "Totals", 3),
                            method("extracted(int)", "Totals", 5),
                            type("Totals", "", 4)),
                    5);

            assertAccepted();
        }

        @Test
        void checksTheMethodJdtlsDeclaredAndNotTheFirstOneWhoseNameStartsLikeTheOneItProposed() throws Exception {
            // Outer.extracted(String) comes first; the extraction is in Inner, which has doubled(int).
            scriptTheExtractionIn(
                    List.of(
                            method("extracted(String)", "Outer", 1),
                            method("doubled(int)", "Inner", 3),
                            method("extracted(int)", "Inner", 5),
                            type("Inner", "Outer", 2),
                            type("Outer", "", 0)),
                    5);

            assertRefused();
        }

        @Test
        void doesNotTakeTheParametersOfTheOuterClassMethodForThoseOfTheExtractedOne() throws Exception {
            // Outer.extracted(String) comes first and Outer has doubled(String); Inner gets extracted(int).
            scriptTheExtractionIn(
                    List.of(
                            method("extracted(String)", "Outer", 1),
                            method("doubled(String)", "Outer", 2),
                            method("extracted(int)", "Inner", 5),
                            type("Inner", "Outer", 4),
                            type("Outer", "", 0)),
                    5);

            assertAccepted();
        }

        @Test
        void acceptsTheNameOfAMethodOfAClassNestedInAnotherOneThatHasTheSameSimpleName() throws Exception {
            // A.Inner.doubled(int), and the extraction goes into B.Inner.
            scriptTheExtractionIn(
                    List.of(
                            method("doubled(int)", "Inner", 1),
                            type("Inner", "A", 2),
                            type("A", "", 0),
                            method("extracted(int)", "Inner", 5),
                            type("Inner", "B", 6),
                            type("B", "", 4)),
                    5);

            assertAccepted();
        }

        @Test
        void refusesTheNameOfAMethodOfTheSameClassWhenAnotherNestedClassHasTheSameSimpleName() throws Exception {
            scriptTheExtractionIn(
                    List.of(
                            method("doubled(int)", "Inner", 1),
                            type("Inner", "A", 2),
                            type("A", "", 0),
                            method("doubled(int)", "Inner", 4),
                            method("extracted(int)", "Inner", 5),
                            type("Inner", "B", 6),
                            type("B", "", 3)),
                    5);

            assertRefused();
        }

        @Test
        void tellsApartTheMethodsOfTwoClassesThatHaveNoName() throws Exception {
            // Two anonymous classes, each with doubled(int); the extraction goes into the second, which has none.
            scriptTheExtractionIn(
                    List.of(
                            method("doubled(int)", "", 1),
                            type("{...}", "run", 2),
                            method("extracted(int)", "", 5),
                            type("{...}", "run", 6),
                            type("Totals", "", 0)),
                    5);

            assertAccepted();
        }

        @Test
        void refusesTheNameOfAMethodOfAClassThatHasNoNameWhenTheExtractedMethodIsInIt() throws Exception {
            scriptTheExtractionIn(
                    List.of(
                            method("doubled(int)", "", 1),
                            method("extracted(int)", "", 5),
                            type("{...}", "run", 6),
                            type("Totals", "", 0)),
                    5);

            assertRefused();
        }

        @Test
        void doesNotTakeAClassThatIsNestedInAClassWithNoNameForThatClass() throws Exception {
            // Helper is a member of the anonymous class, so its container is empty as well, and it is listed first.
            scriptTheExtractionIn(
                    List.of(
                            method("doubled(int)", "", 1),
                            type("Helper", "", 2),
                            method("extracted(int)", "", 5),
                            type("{...}", "run", 6),
                            type("Totals", "", 0)),
                    5);

            assertRefused();
        }

        @Test
        void refusesTheNameOfAMethodOfAGenericClassWhoseLabelCarriesItsTypeParameters() throws Exception {
            scriptTheExtractionIn(
                    List.of(
                            method("doubled(int)", "Totals", 1),
                            method("extracted(int)", "Totals", 5),
                            type("Totals<T>", "", 0)),
                    5);

            assertRefused();
        }

        @FakeOnly(reason = MALFORMED_ANSWER)
        @Test
        void failsWhenJdtlsDoesNotListTheMethodItExtracted() throws Exception {
            fake.refactorEditAnswers(proposal())
                    .publishesOnOpen(totals, List.of())
                    .documentSymbolAnswers(members("doubled(int)"))
                    .definitionAnswers(declaredOnLine(5));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractMethod(totals, selection, "scaled"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("did not list the method extracted");
                assertThat(fake.closed(1)).containsExactly(totals.toUri().toString());
            }
        }

        @FakeOnly(reason = MALFORMED_ANSWER)
        @Test
        void failsWhenJdtlsListsNothingForTheText() throws Exception {
            fake.refactorEditAnswers(proposal())
                    .publishesOnOpen(totals, List.of())
                    .documentSymbolAnswers(CompletableFuture.completedFuture(null))
                    .definitionAnswers(declaredOnLine(5));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractMethod(totals, selection, "scaled"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("did not list the method extracted");
            }
        }

        @FakeOnly(reason = MALFORMED_ANSWER)
        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void failsWhenJdtlsDoesNotSayWhereTheNewMethodIsDeclared(boolean nothing) throws Exception {
            fake.refactorEditAnswers(proposal())
                    .publishesOnOpen(totals, List.of())
                    .documentSymbolAnswers(members("extracted(int)"))
                    .definitionAnswers(
                            CompletableFuture.completedFuture(nothing ? null : Either.forLeft(List.<Location>of())));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractMethod(totals, selection, "scaled"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("did not say where the method extracted is declared");
                assertThat(fake.closed(1)).containsExactly(totals.toUri().toString());
            }
        }

        @FakeOnly(reason = MALFORMED_ANSWER)
        @Test
        void failsWhenJdtlsGivesTheDeclarationOfTheNewMethodAsLinks() throws Exception {
            var link = new LocationLink(
                    totals.toUri().toString(), nameRange("extracted(int)", 5), nameRange("extracted(int)", 5));
            fake.refactorEditAnswers(proposal())
                    .publishesOnOpen(totals, List.of())
                    .documentSymbolAnswers(members("extracted(int)"))
                    .definitionAnswers(CompletableFuture.completedFuture(Either.forRight(List.of(link))));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractMethod(totals, selection, "scaled"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("did not say where the method extracted is declared");
            }
        }

        @Test
        void saysItIsTheMethodWhenJdtlsDoesNotSayWhereTheNameIs() throws Exception {
            fake.refactorEditAnswers(
                    CompletableFuture.completedFuture(new JdtlsServer.RefactorEdit(extractsTheMethod(), null, null)));

            try (var server = started()) {
                assertThatThrownBy(() -> server.extractMethod(totals, selection, "scaled"))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("jdtls made the extracted method but did not say where its name is");
            }
        }

        @Test
        void refusesANameJdtlsDeclinesForTheMethod() throws Exception {
            fake.refactorEditAnswers(proposal())
                    .publishesOnOpen(totals, List.of())
                    .documentSymbolAnswers(members("extracted(int)"))
                    .definitionAnswers(declaredOnLine(1))
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

        @FakeOnly(reason = INTERRUPTED_WHILE_WAITING)
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

        private final eu.gabier.editing.domain.Range selection =
                new eu.gabier.editing.domain.Range(new Position(2, 27), new Position(2, 36));
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

        @FakeOnly(reason = INTERRUPTED_WHILE_WAITING)
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

        private final eu.gabier.editing.domain.Range selection =
                new eu.gabier.editing.domain.Range(new Position(2, 27), new Position(2, 36));
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

        @FakeOnly(reason = INTERRUPTED_WHILE_WAITING)
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
    @FakeOnly(reason = NOTHING_IS_THE_ONLY_ANSWER)
    @Nested
    class ExtractingWhatJdtlsCannotExtract {

        @FunctionalInterface
        interface Extractor {
            ChangeSet extract(
                    JdtlsLanguageServer server, Path file, eu.gabier.editing.domain.Range selection, String name);
        }

        private final eu.gabier.editing.domain.Range selection =
                new eu.gabier.editing.domain.Range(new Position(2, 15), new Position(2, 31));
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

        @FakeOnly(reason = JDTLS_FAILS)
        @Test
        void saysWhatWentWrongWhenJdtlsFailsInsteadOfDeclining() throws Exception {
            fake.moveAnswers(CompletableFuture.failedFuture(new IllegalStateException("the model is broken")));

            try (var server = started()) {
                assertThatThrownBy(() -> server.moveTypeToFile(shapes, inCircle))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.REQUEST_FAILED))
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

        @FakeOnly(reason = INTERRUPTED_WHILE_WAITING)
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
                            new eu.gabier.editing.domain.TextEdit(
                                    new eu.gabier.editing.domain.Range(new Position(3, 0), new Position(5, 0)), ""),
                            new eu.gabier.editing.domain.TextEdit(
                                    new eu.gabier.editing.domain.Range(new Position(6, 20), new Position(6, 23)),
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

        @FakeOnly(reason = JDTLS_FAILS)
        @Test
        void saysWhatWentWrongWhenJdtlsFailsInsteadOfDeclining() throws Exception {
            fake.codeActionAnswers(CompletableFuture.failedFuture(new IllegalStateException("the model is broken")));

            try (var server = started()) {
                assertThatThrownBy(() -> server.inline(prices, onTheConstant))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.REQUEST_FAILED))
                        .hasMessageContaining("Internal error");
            }
        }

        @FakeOnly(reason = INTERRUPTED_WHILE_WAITING)
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
    class OfferingCodeActions {

        private static final String OFFERS = """
            package demo;

            public class Offers {
                List<String> names() {
                    return null;
                }

                int count() {
                    return 1;
                }
            }
            """;

        private final eu.gabier.editing.domain.Range onTheType =
                new eu.gabier.editing.domain.Range(new Position(3, 4), new Position(3, 8));
        private Path offers;

        @BeforeEach
        void writeTheSources() throws IOException {
            offers = write("src/main/java/demo/Offers.java", OFFERS);
        }

        private org.eclipse.lsp4j.Position at(int line, int column) {
            return new org.eclipse.lsp4j.Position(line, column);
        }

        private org.eclipse.lsp4j.Diagnostic problem(int line, int from, int to) {
            return new org.eclipse.lsp4j.Diagnostic(
                    new Range(at(line, from), at(line, to)),
                    "List cannot be resolved",
                    DiagnosticSeverity.Error,
                    "Java");
        }

        private CodeAction action(String kind, String title) {
            var action = new CodeAction(title);
            action.setKind(kind);
            action.setData("data of " + title);
            return action;
        }

        private CodeAction withEdit(CodeAction action) {
            var edit = new TextDocumentEdit(
                    new VersionedTextDocumentIdentifier(offers.toUri().toString(), null),
                    List.of(Either.forLeft(new TextEdit(new Range(at(1, 0), at(1, 0)), "\nimport java.util.List;\n"))));
            action.setEdit(new WorkspaceEdit(List.of(Either.forLeft(edit))));
            return action;
        }

        private ChangeSet imported() {
            return new ChangeSet(List.of(new FileChange.EditFile(
                    offers,
                    List.of(new eu.gabier.editing.domain.TextEdit(
                            new eu.gabier.editing.domain.Range(new Position(1, 0), new Position(1, 0)),
                            "\nimport java.util.List;\n")))));
        }

        private CompletableFuture<List<Either<Command, CodeAction>>> offered(CodeAction... actions) {
            return CompletableFuture.completedFuture(Arrays.stream(actions)
                    .map(Either::<Command, CodeAction>forRight)
                    .toList());
        }

        private eu.gabier.editing.domain.OfferedAction importList() {
            return new eu.gabier.editing.domain.OfferedAction("quickfix", "Import 'List' (java.util)");
        }

        @Test
        void listsTheActionsWithTheirKindAndTheirTitleInTheOrderOfJdtls() throws Exception {
            fake.publishesOnOpen(offers, List.of(problem(3, 4, 8)))
                    .codeActionAnswers(offered(
                            action(CodeActionKind.QuickFix, "Import 'List' (java.util)"),
                            action("quickassist", "Add Javadoc comment"),
                            action(CodeActionKind.RefactorExtract, "Extract to method")));

            try (var server = started()) {
                assertThat(server.codeActions(offers, onTheType).actions())
                        .containsExactly(
                                importList(),
                                new eu.gabier.editing.domain.OfferedAction("quickassist", "Add Javadoc comment"),
                                new eu.gabier.editing.domain.OfferedAction("refactor.extract", "Extract to method"));
            }
        }

        @Test
        void asksForTheKindsOfTheFixesAndTheRewritesNotTheActionsOfTheWholeFile() throws Exception {
            fake.publishesOnOpen(offers, List.of(problem(3, 4, 8))).codeActionAnswers(offered());

            try (var server = started()) {
                server.codeActions(offers, onTheType);

                assertThat(fake.codeActionRequests()).hasSize(1);
                CodeActionParams request = fake.codeActionRequests().getFirst();
                assertThat(request.getTextDocument().getUri())
                        .isEqualTo(offers.toUri().toString());
                assertThat(request.getRange()).isEqualTo(new Range(at(3, 4), at(3, 8)));
                assertThat(request.getContext().getOnly()).containsExactly("quickfix", "refactor", "quickassist");
                assertThat(offers).hasContent(OFFERS);
            }
        }

        @Test
        void sendsTheDiagnosticsOfTheFileThatJdtlsPublishedAsTheContextOfTheRequest() throws Exception {
            fake.publishesOnOpen(offers, List.of(problem(3, 4, 8))).codeActionAnswers(offered());

            try (var server = started()) {
                server.codeActions(offers, onTheType);

                assertThat(fake.opened())
                        .containsExactly(new FakeJdtls.Opened(offers.toUri().toString(), OFFERS));
                assertThat(fake.codeActionRequests().getFirst().getContext().getDiagnostics())
                        .containsExactly(problem(3, 4, 8));
            }
        }

        @ParameterizedTest
        @CsvSource({
            "3, 0, 3, 4, true", // the range ends where the problem starts
            "3, 8, 3, 12, true", // the range starts where the problem ends
            "3, 5, 3, 5, true", // a point inside the problem
            "3, 0, 3, 3, false", // before it
            "3, 9, 3, 12, false", // after it
            "2, 0, 2, 30, false", // on another line above
            "4, 0, 5, 0, false", // on other lines below
            "1, 0, 6, 0, true" // lines that hold it
        })
        void givesTheServerTheDiagnosticsThatTouchTheRange(
                int startLine, int startColumn, int endLine, int endColumn, boolean given) throws Exception {
            fake.publishesOnOpen(offers, List.of(problem(3, 4, 8))).codeActionAnswers(offered());

            try (var server = started()) {
                server.codeActions(
                        offers,
                        new eu.gabier.editing.domain.Range(
                                new Position(startLine, startColumn), new Position(endLine, endColumn)));

                assertThat(fake.codeActionRequests().getFirst().getContext().getDiagnostics())
                        .hasSize(given ? 1 : 0);
            }
        }

        @Test
        void listsNothingWhenJdtlsOffersNothingAnswersNothingOrDeclines() throws Exception {
            fake.publishesOnOpen(offers, List.of()).codeActionAnswers(CompletableFuture.completedFuture(List.of()));

            try (var server = started()) {
                assertThat(server.codeActions(offers, onTheType).actions()).isEmpty();

                fake.codeActionAnswers(CompletableFuture.completedFuture(null));
                assertThat(server.codeActions(offers, onTheType).actions()).isEmpty();

                fake.codeActionAnswers(CompletableFuture.failedFuture(new ResponseErrorException(
                        new ResponseError(ResponseErrorCode.InvalidRequest, "cannot offer", null))));
                assertThat(server.codeActions(offers, onTheType).actions()).isEmpty();
            }
        }

        @Test
        void leavesOutACommandWhichIsNoActionAndTakesAnActionWithoutAKindForOneOfAnEmptyKind() throws Exception {
            fake.publishesOnOpen(offers, List.of())
                    .codeActionAnswers(CompletableFuture.completedFuture(List.of(
                            Either.forLeft(new Command("Fix", "java.apply.workspaceEdit")),
                            Either.forRight(new CodeAction("Without a kind")))));

            try (var server = started()) {
                assertThat(server.codeActions(offers, onTheType).actions())
                        .containsExactly(new eu.gabier.editing.domain.OfferedAction("", "Without a kind"));
            }
        }

        @Test
        void refusesAFileOutsideTheProjectAMissingFileAndAFolderWithoutAskingJdtls() throws Exception {
            try (var server = started()) {
                assertThatThrownBy(() -> server.codeActions(root.resolve("../Elsewhere.java"), onTheType))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key())
                                        .isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));
                assertThatThrownBy(() -> server.codeActions(root.resolve("src/main/java/demo/Nope.java"), onTheType))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.FILE_MISSING));
                assertThatThrownBy(() -> server.codeActions(root.resolve("src/main/java/demo"), onTheType))
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
                assertThatThrownBy(() -> server.codeActions(offers, onTheType))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
                assertThat(fake.codeActionRequests()).isEmpty();
            }
        }

        @FakeOnly(reason = JDTLS_FAILS)
        @Test
        void saysWhatWentWrongWhenJdtlsFailsInsteadOfDeclining() throws Exception {
            fake.publishesOnOpen(offers, List.of())
                    .codeActionAnswers(
                            CompletableFuture.failedFuture(new IllegalStateException("the model is broken")));

            try (var server = started()) {
                assertThatThrownBy(() -> server.codeActions(offers, onTheType))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.REQUEST_FAILED))
                        .hasMessageContaining("Internal error");
            }
        }

        /** Interrupts the thread that calls the adapter once jdtls has been asked what the test waits for. */
        private void interruptTheCallerOnceAsked(Supplier<Boolean> asked) {
            var caller = Thread.currentThread();
            Thread.ofPlatform().daemon().start(() -> {
                try {
                    while (!asked.get()) {
                        Thread.sleep(5);
                    }
                    caller.interrupt();
                } catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        @Test
        void keepsTheInterruptionOfTheThreadThatWaitsForTheDiagnosticsOfTheActions() throws Exception {
            fake.publishesOnOpen(offers, List.of()).codeActionAnswers(offered());

            try (var server = started()) {
                Thread.currentThread().interrupt();

                assertThatThrownBy(() -> server.codeActions(offers, onTheType)).isInstanceOf(JdtlsException.class);
                assertThat(Thread.interrupted()).isTrue();
            }
        }

        @FakeOnly(reason = INTERRUPTED_WHILE_WAITING)
        @Test
        void keepsTheInterruptionOfTheThreadThatWaitsForTheActions() throws Exception {
            fake.publishesOnOpen(offers, List.of()).codeActionAnswers(new CompletableFuture<>());

            try (var server = started()) {
                interruptTheCallerOnceAsked(() -> !fake.codeActionRequests().isEmpty());

                assertThatThrownBy(() -> server.codeActions(offers, onTheType))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("Interrupted while waiting for the code actions on");
                assertThat(Thread.interrupted()).isTrue();
            }
        }

        @FakeOnly(reason = INTERRUPTED_WHILE_WAITING)
        @Test
        void keepsTheInterruptionOfTheThreadThatWaitsForTheEditOfAnAction() throws Exception {
            fake.publishesOnOpen(offers, List.of())
                    .codeActionAnswers(offered(action(CodeActionKind.QuickFix, "Import 'List' (java.util)")))
                    .resolveAnswers(new CompletableFuture<>());

            try (var server = started()) {
                interruptTheCallerOnceAsked(() -> !fake.resolveRequests().isEmpty());

                assertThatThrownBy(() -> server.codeActionEdit(offers, onTheType, importList()))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining(
                                "Interrupted while waiting for the edit of the action \"Import 'List' (java.util)\"");
                assertThat(Thread.interrupted()).isTrue();
            }
        }

        @Test
        void resolvesTheActionOfTheKindAndTheTitleAndAnswersItsEditInTheTermsOfTheDomain() throws Exception {
            fake.publishesOnOpen(offers, List.of(problem(3, 4, 8)))
                    .codeActionAnswers(offered(
                            action(CodeActionKind.QuickFix, "Import 'List' (java.awt)"),
                            action(CodeActionKind.QuickFix, "Import 'List' (java.util)")))
                    .resolveAnswers(CompletableFuture.completedFuture(
                            withEdit(action(CodeActionKind.QuickFix, "Import 'List' (java.util)"))));

            try (var server = started()) {
                assertThat(server.codeActionEdit(offers, onTheType, importList()))
                        .isEqualTo(imported());

                assertThat(fake.resolveRequests()).hasSize(1);
                assertThat(fake.resolveRequests().getFirst().getData())
                        .isEqualTo(new JsonPrimitive("data of Import 'List' (java.util)"));
                assertThat(fake.codeActionRequests().getFirst().getContext().getDiagnostics())
                        .containsExactly(problem(3, 4, 8));
                assertThat(offers).hasContent(OFFERS);
            }
        }

        @Test
        void takesTheEditOfTheActionWhenJdtlsGivesItWithoutBeingAskedToResolve() throws Exception {
            fake.publishesOnOpen(offers, List.of())
                    .codeActionAnswers(offered(withEdit(action(CodeActionKind.QuickFix, "Import 'List' (java.util)"))));

            try (var server = started()) {
                assertThat(server.codeActionEdit(offers, onTheType, importList()))
                        .isEqualTo(imported());
                assertThat(fake.resolveRequests()).isEmpty();
            }
        }

        private void assertNotApplicable(JdtlsLanguageServer server) {
            assertThatThrownBy(() -> server.codeActionEdit(offers, onTheType, importList()))
                    .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                        assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_APPLICABLE);
                        assertThat(refusal.parameters())
                                .containsEntry("file", offers.toString())
                                .containsEntry("title", "Import 'List' (java.util)")
                                .containsEntry("line", "3")
                                .containsEntry("endColumn", "8");
                    });
        }

        @Test
        void refusesAnActionThatIsACommandForAnEditor() throws Exception {
            var command = action(CodeActionKind.QuickFix, "Import 'List' (java.util)");
            command.setCommand(new Command("Import", "java.action.applyRefactoringCommand"));
            fake.publishesOnOpen(offers, List.of())
                    .codeActionAnswers(offered(command))
                    .resolveAnswers(CompletableFuture.completedFuture(command));

            try (var server = started()) {
                assertNotApplicable(server);
            }
        }

        @Test
        void refusesAnActionJdtlsNoLongerOffers() throws Exception {
            fake.publishesOnOpen(offers, List.of())
                    .codeActionAnswers(offered(action(CodeActionKind.QuickFix, "Another fix")));

            try (var server = started()) {
                assertNotApplicable(server);
                assertThat(fake.resolveRequests()).isEmpty();
            }
        }

        @Test
        void refusesTheActionOfTheSameTitleButAnotherKind() throws Exception {
            fake.publishesOnOpen(offers, List.of())
                    .codeActionAnswers(offered(withEdit(action("source", "Import 'List' (java.util)"))));

            try (var server = started()) {
                assertNotApplicable(server);
            }
        }

        @Test
        void refusesWhenTheResolveIsDeclinedOrAnswersNothing() throws Exception {
            fake.publishesOnOpen(offers, List.of())
                    .codeActionAnswers(offered(action(CodeActionKind.QuickFix, "Import 'List' (java.util)")))
                    .resolveAnswers(CompletableFuture.completedFuture(null));

            try (var server = started()) {
                assertNotApplicable(server);

                fake.resolveAnswers(CompletableFuture.failedFuture(new ResponseErrorException(
                        new ResponseError(ResponseErrorCode.InvalidRequest, "cannot resolve", null))));
                assertNotApplicable(server);
            }
        }

        @Test
        void refusesAnEditThatChangesNothing() throws Exception {
            var empty = action(CodeActionKind.QuickFix, "Import 'List' (java.util)");
            empty.setEdit(new WorkspaceEdit(List.of()));
            fake.publishesOnOpen(offers, List.of()).codeActionAnswers(offered(empty));

            try (var server = started()) {
                assertNotApplicable(server);
            }
        }

        @Test
        void refusesAFileOutsideTheProjectWithoutAskingJdtls() throws Exception {
            try (var server = started()) {
                assertThatThrownBy(
                                () -> server.codeActionEdit(root.resolve("../Elsewhere.java"), onTheType, importList()))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key())
                                        .isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));
                assertThat(fake.codeActionRequests()).isEmpty();
            }
        }

        @FakeOnly(reason = JDTLS_FAILS)
        @Test
        void saysWhatWentWrongWhenTheResolveFailsInsteadOfDeclining() throws Exception {
            fake.publishesOnOpen(offers, List.of())
                    .codeActionAnswers(offered(action(CodeActionKind.QuickFix, "Import 'List' (java.util)")))
                    .resolveAnswers(CompletableFuture.failedFuture(new IllegalStateException("the model is broken")));

            try (var server = started()) {
                assertThatThrownBy(() -> server.codeActionEdit(offers, onTheType, importList()))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.REQUEST_FAILED))
                        .hasMessageContaining("Internal error");
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

        @FakeOnly(reason = JDTLS_FAILS)
        @Test
        void saysWhatWentWrongWhenJdtlsFailsInsteadOfDeclining() throws Exception {
            fake.completionAnswers(CompletableFuture.failedFuture(new IllegalStateException("the model is broken")));

            try (var server = started()) {
                assertThatThrownBy(() -> server.createType(widget, TypeKind.CLASS))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.REQUEST_FAILED))
                        .hasMessageContaining("Internal error");
                assertThat(fake.closed(1)).containsExactly(widget.toUri().toString());
            }
        }

        @FakeOnly(reason = INTERRUPTED_WHILE_WAITING)
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
    class CreatingAPackageInfo {

        private Path sources;
        private Path packageInfo;

        @BeforeEach
        void chooseTheFile() {
            sources = root.resolve("src/main/java");
            packageInfo = root.resolve("src/main/java/demo/package-info.java");
            fake.listsSourcePaths(sources);
        }

        private String textOf(ChangeSet changes, Path file) {
            assertThat(changes.changes()).hasSize(2);
            assertThat(changes.changes().get(0)).isEqualTo(new FileChange.CreateFile(file));

            return ((FileChange.EditFile) changes.changes().get(1))
                    .edits().stream().map(edit -> edit.newText()).collect(java.util.stream.Collectors.joining());
        }

        @Test
        void answersTheCreationOfTheFileAndTheOneEditThatGivesItsAnnotationAndItsPackageLineOnly() throws Exception {
            try (var server = started()) {
                ChangeSet changes = server.createPackageInfo(packageInfo);

                assertThat(changes.changes().get(1)).isInstanceOfSatisfying(FileChange.EditFile.class, edit -> {
                    assertThat(edit.file()).isEqualTo(packageInfo);
                    assertThat(SourceText.edited(packageInfo, "", edit.edits()))
                            .isEqualTo("@org.jspecify.annotations.NullMarked\npackage demo;\n");
                });
                assertThat(changes.changes().get(0)).isEqualTo(new FileChange.CreateFile(packageInfo));
            }
        }

        @Test
        void asksJdtlsForTheSourceFoldersOnlyAndOpensNothingNorWritesTheFile() throws Exception {
            try (var server = started()) {
                server.createPackageInfo(packageInfo);

                assertThat(fake.sourcePathRequests()).isEqualTo(1);
                assertThat(fake.opened()).isEmpty();
                assertThat(fake.completionRequests()).isEmpty();
                assertThat(packageInfo).doesNotExist();
            }
        }

        @Test
        void namesTheSubpackageWithDotsAndKeepsItsFolderToItselfWhenItDoesNotExist() throws Exception {
            Path file = root.resolve("src/main/java/demo/app/ui/package-info.java");

            try (var server = started()) {
                assertThat(textOf(server.createPackageInfo(file), file)).endsWith("package demo.app.ui;\n");
                assertThat(file.getParent()).doesNotExist();
            }
        }

        @Test
        void givesTheFileOfAnyOfTheSourceFoldersThePackageOfItsFolderUnderThatOne() throws Exception {
            fake.listsSourcePaths(root.resolve("src/main/java"), root.resolve("src/test/java"));
            Path file = root.resolve("src/test/java/demo/package-info.java");

            try (var server = started()) {
                assertThat(textOf(server.createPackageInfo(file), file)).endsWith("package demo;\n");
            }
        }

        @Test
        void usesTheInnermostSourceFolderThatHoldsTheFile() throws Exception {
            fake.listsSourcePaths(root.resolve("src"), root.resolve("src/main/java"), root.resolve("lib"));

            try (var server = started()) {
                assertThat(textOf(server.createPackageInfo(packageInfo), packageInfo))
                        .endsWith("package demo;\n");
            }
        }

        @Test
        void refusesAFileStraightUnderASourceFolderWhichIsTheDefaultPackage() throws Exception {
            Path file = root.resolve("src/main/java/package-info.java");

            try (var server = started()) {
                assertThatThrownBy(() -> server.createPackageInfo(file))
                        .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_CREATABLE);
                            assertThat(refusal.parameters()).containsEntry("file", file.toString());
                        });
            }
        }

        @Test
        void refusesAFileUnderNoSourceFolderAndNotOneThatOnlySharesTheStartOfTheName() throws Exception {
            try (var server = started()) {
                for (String relative : List.of("docs/package-info.java", "src/main/javax/demo/package-info.java")) {
                    assertThatThrownBy(() -> server.createPackageInfo(root.resolve(relative)))
                            .as(relative)
                            .isInstanceOfSatisfying(
                                    LanguageServerException.class,
                                    refusal ->
                                            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_CREATABLE));
                }
            }
        }

        @Test
        void refusesAFolderThatIsNotTheNameOfAPackageAndSaysSo() throws Exception {
            try (var server = started()) {
                assertThatThrownBy(
                                () -> server.createPackageInfo(root.resolve("src/main/java/my-app/package-info.java")))
                        .isInstanceOfSatisfying(
                                EditingException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(EditingErrorKey.NOT_A_PACKAGE_NAME));
                assertThatThrownBy(() ->
                                server.createPackageInfo(root.resolve("src/main/java/demo/class/package-info.java")))
                        .isInstanceOf(EditingException.class);
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {"no workspace", "not a result"})
        void saysWhatJdtlsAnsweredWhenItDoesNotListTheSourceFoldersAsExpected(String shape) throws Exception {
            Object answer = "no workspace".equals(shape) ? Map.of("status", false, "message", "no workspace") : shape;
            fake.sourcePathsAnswers(CompletableFuture.completedFuture(answer));

            try (var server = started()) {
                assertThatThrownBy(() -> server.createPackageInfo(packageInfo))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("java.project.listSourcePaths answered")
                        .hasMessageContaining(String.valueOf(answer));
            }
        }

        @Test
        void refusesAFileOutsideTheProjectWithoutAskingJdtlsAnything() throws Exception {
            try (var server = started()) {
                assertThatThrownBy(() -> server.createPackageInfo(root.resolve("../outside/package-info.java")))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key())
                                        .isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));
                assertThat(fake.sourcePathRequests()).isZero();
            }
        }

        @Test
        void refusesAsNotReadyWhileJdtlsIsStillImporting() throws Exception {
            fake.neverAnswersInitialize();

            try (var server = start()) {
                assertThatThrownBy(() -> server.createPackageInfo(packageInfo))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
                assertThat(fake.sourcePathRequests()).isZero();
            }
        }

        @FakeOnly(reason = INTERRUPTED_WHILE_WAITING)
        @Test
        void keepsTheInterruptionOfTheThreadThatWaits() throws Exception {
            try (var server = started()) {
                Thread.currentThread().interrupt();

                assertThatThrownBy(() -> server.createPackageInfo(packageInfo))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("Interrupted while waiting for the source folders for " + packageInfo);
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

        private eu.gabier.editing.domain.Location place(Path file, int line, int column) {
            return new eu.gabier.editing.domain.Location(file, new Position(line, column));
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

        @FakeOnly(reason = JDTLS_FAILS)
        @Test
        void saysWhatWentWrongWhenJdtlsFailsInsteadOfAnswering() throws Exception {
            fake.prepareRenameAnswers(renamable())
                    .referencesAnswers(
                            false, CompletableFuture.failedFuture(new IllegalStateException("the index is broken")));

            try (var server = started()) {
                assertThatThrownBy(() -> server.references(ledger, new Position(3, 8), false))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.REQUEST_FAILED))
                        .hasMessageContaining("Internal error");
            }
        }

        @FakeOnly(reason = NO_ANSWER_IN_TIME)
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

        @FakeOnly(reason = INTERRUPTED_WHILE_WAITING)
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
