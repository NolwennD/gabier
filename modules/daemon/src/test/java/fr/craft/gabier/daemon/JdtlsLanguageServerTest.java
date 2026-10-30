package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.Diagnostic;
import fr.craft.gabier.editing.domain.Diagnostic.Severity;
import fr.craft.gabier.editing.domain.FileChange;
import fr.craft.gabier.editing.domain.LanguageServerErrorKey;
import fr.craft.gabier.editing.domain.LanguageServerException;
import fr.craft.gabier.editing.domain.Position;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
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
}
