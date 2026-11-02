package eu.gabier.daemon;

import static eu.gabier.daemon.FakeOnlyReasons.JDTLS_FAILS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.FileChange;
import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.PackageName;
import eu.gabier.editing.fixtures.FakeOnly;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code moveToPackage} of the jdtls adapter against {@link FakeJdtls}: what it asks of jdtls ({@code java/move} with
 * the move kind {@code moveResource}), what it decides itself before asking (jdtls skips a file it does not know and
 * moves a file onto a file of its name), and the folder it makes for the time of the request when the package has none
 * (jdtls answers no change for a package whose folder it does not know).
 */
@ExtendWith(FakeOnlyGuard.class)
class JdtlsMoveToPackageTest {

    private static final Duration LONG = Duration.ofSeconds(30);
    private static final Duration QUIET = Duration.ofMillis(300);
    private static final PackageName BILLING = PackageName.of("demo.billing");
    private static final PackageName ARCHIVE = PackageName.of("demo.archive");

    @TempDir
    Path root;

    private final FakeJdtls fake = new FakeJdtls();
    private final List<String> log = new CopyOnWriteArrayList<>();
    private Path invoice;
    private Path payment;
    private Path invoiceTest;
    private Path main;

    @BeforeEach
    void writeTheProject() throws IOException {
        root = root.toRealPath();
        main = root.resolve("src/main/java");
        invoice = write("src/main/java/demo/Invoice.java", "package demo;\n\npublic class Invoice {}\n");
        payment = write("src/main/java/demo/Payment.java", "package demo;\n\npublic class Payment {}\n");
        write("src/main/java/demo/Ledger.java", "package demo;\n\npublic class Ledger {}\n");
        write("src/main/java/demo/billing/Ledger.java", "package demo.billing;\n\npublic class Ledger {}\n");
        write("src/main/java/demo/other/Ledger.java", "package demo.other;\n\npublic class Ledger {}\n");
        write("src/main/java/demo/notes.txt", "not Java\n");
        write("Unsourced.java", "class Unsourced {}\n");
        invoiceTest = write("src/test/java/demo/InvoiceTest.java", "package demo;\n\nclass InvoiceTest {}\n");
        fake.listsProjects(root.toUri().toString());
        fake.listsSourcePaths(main, root.resolve("src/test/java"));
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

    private JdtlsLanguageServer start() {
        var settings = new JdtlsSettings(
                root, List.of(root), LONG, Duration.ofMillis(20), Duration.ofSeconds(5), JdtlsWorkspace.temporary());
        var server = JdtlsLanguageServer.start(fake, settings, QUIET, log::add);
        fake.initializeParams();
        return server;
    }

    private JdtlsLanguageServer started() throws Exception {
        var server = start();
        server.awaitStarted();
        return server;
    }

    private static org.eclipse.lsp4j.Position at(int line, int column) {
        return new org.eclipse.lsp4j.Position(line, column);
    }

    /** What jdtls answers for a move of files to the folder: the package line of each, then the move of each. */
    private static CompletableFuture<JdtlsServer.RefactorEdit> moves(Path folder, Path... files) {
        List<Either<TextDocumentEdit, ResourceOperation>> changes = new ArrayList<>();
        for (Path file : files) {
            changes.add(Either.forLeft(new TextDocumentEdit(
                    new VersionedTextDocumentIdentifier(file.toUri().toString(), null),
                    List.of(Either.forLeft(new TextEdit(new Range(at(0, 8), at(0, 12)), "x"))))));
            changes.add(Either.forRight(new RenameFile(
                    file.toUri().toString(),
                    folder.resolve(file.getFileName()).toUri().toString())));
        }

        return CompletableFuture.completedFuture(new JdtlsServer.RefactorEdit(new WorkspaceEdit(changes), null, null));
    }

    private static LanguageServerException refusalOf(Throwable thrown) {
        assertThat(thrown).isInstanceOf(LanguageServerException.class);
        return (LanguageServerException) thrown;
    }

    private List<String> eventsOfWatchedFiles() {
        return fake.watched().stream()
                .map(DidChangeWatchedFilesParams::getChanges)
                .flatMap(List::stream)
                .map(event -> event.getType() + " " + Path.of(URI.create(event.getUri())))
                .toList();
    }

    @Test
    void asksJdtlsToMoveTheFilesToTheFolderOfThePackageWithTheReferencesUpdated() throws Exception {
        Path billing = main.resolve("demo/billing");
        fake.moveAnswers(moves(billing, invoice, payment));

        try (var server = started()) {
            server.moveToPackage(List.of(invoice, payment), BILLING);

            assertThat(fake.moveRequests()).hasSize(1);
            var request = fake.moveRequests().getFirst();
            assertThat(request.moveKind()).isEqualTo("moveResource");
            assertThat(request.updateReferences()).isTrue();
            assertThat(request.sourceUris())
                    .containsExactly(invoice.toUri().toString(), payment.toUri().toString());
            assertThat(request.destination()).isEqualTo(billing.toUri().toString());
            assertThat(request.params()).isNull();
        }
    }

    @Test
    void answersTheEditsAndTheMovesInTheOrderOfJdtlsAndWritesNothing() throws Exception {
        Path billing = main.resolve("demo/billing");
        fake.moveAnswers(moves(billing, invoice));

        try (var server = started()) {
            ChangeSet changes = server.moveToPackage(List.of(invoice), BILLING);

            assertThat(changes.changes()).hasSize(2);
            assertThat(changes.changes().get(0)).isInstanceOf(FileChange.EditFile.class);
            assertThat(changes.changes().get(1))
                    .isEqualTo(new FileChange.RenameFile(invoice, billing.resolve("Invoice.java")));
            assertThat(invoice).hasContent("package demo;\n\npublic class Invoice {}\n");
            assertThat(billing.resolve("Invoice.java")).doesNotExist();
        }
    }

    @Test
    void answersTheEditsOfEveryFileByPathThenTheMovesByPathWhateverTheOrderOfJdtls() throws Exception {
        Path billing = main.resolve("demo/billing");
        String firstEdit = "first";
        String secondEdit = "second";
        List<Either<TextDocumentEdit, ResourceOperation>> shuffled = List.of(
                Either.forRight(new RenameFile(
                        payment.toUri().toString(),
                        billing.resolve("Payment.java").toUri().toString())),
                editOf(invoiceTest, firstEdit),
                editOf(payment, firstEdit),
                Either.forRight(new RenameFile(
                        invoice.toUri().toString(),
                        billing.resolve("Invoice.java").toUri().toString())),
                editOf(invoice, firstEdit),
                editOf(invoice, secondEdit));
        fake.moveAnswers(CompletableFuture.completedFuture(
                new JdtlsServer.RefactorEdit(new WorkspaceEdit(shuffled), null, null)));

        try (var server = started()) {
            ChangeSet changes = server.moveToPackage(List.of(invoice, payment), BILLING);

            assertThat(changes.changes())
                    .containsExactly(
                            edited(invoice, firstEdit),
                            edited(invoice, secondEdit),
                            edited(payment, firstEdit),
                            edited(invoiceTest, firstEdit),
                            new FileChange.RenameFile(invoice, billing.resolve("Invoice.java")),
                            new FileChange.RenameFile(payment, billing.resolve("Payment.java")));
        }
    }

    private static Either<TextDocumentEdit, ResourceOperation> editOf(Path file, String text) {
        return Either.forLeft(new TextDocumentEdit(
                new VersionedTextDocumentIdentifier(file.toUri().toString(), null),
                List.of(Either.forLeft(new TextEdit(new Range(at(0, 8), at(0, 12)), text)))));
    }

    private static FileChange edited(Path file, String text) {
        return new FileChange.EditFile(
                file,
                List.of(new eu.gabier.editing.domain.TextEdit(
                        new eu.gabier.editing.domain.Range(
                                new eu.gabier.editing.domain.Position(0, 8),
                                new eu.gabier.editing.domain.Position(0, 12)),
                        text)));
    }

    @Test
    void tellsJdtlsAboutTheChangesOnDiskBeforeAsking() throws Exception {
        fake.moveAnswers(moves(main.resolve("demo/billing"), invoice));
        fake.registersWatchersFor("**/*.java");

        try (var server = started()) {
            Files.writeString(invoice, "package demo;\n\npublic class Invoice { int x; }\n");

            server.moveToPackage(List.of(invoice), BILLING);

            assertThat(fake.received()).containsSubsequence("didChangeWatchedFiles", "move Invoice.java");
        }
    }

    @Test
    void makesTheFolderOfAPackageJdtlsDoesNotKnowForTheTimeOfTheRequestAndTellsJdtlsWhenItIsMadeAndWhenItIsGone()
            throws Exception {
        Path archive = main.resolve("demo/archive");
        List<Object> seen = new ArrayList<>();
        fake.moveAnswers(moves(archive, invoice)).onMove(request -> {
            seen.add(Files.isDirectory(archive));
            seen.addAll(eventsOfWatchedFiles());
        });

        try (var server = started()) {
            ChangeSet changes = server.moveToPackage(List.of(invoice), ARCHIVE);

            assertThat(seen).first().isEqualTo(true);
            assertThat(seen).contains("Created " + archive);
            assertThat(seen).doesNotContain("Deleted " + archive);
            assertThat(archive).doesNotExist();
            assertThat(eventsOfWatchedFiles()).containsSubsequence("Created " + archive, "Deleted " + archive);
            assertThat(fake.moveRequests().getFirst().destination()).isEqualTo(archive.toUri() + "/");
            assertThat(changes.changes()).contains(new FileChange.RenameFile(invoice, archive.resolve("Invoice.java")));
        }
    }

    @Test
    void makesAndRemovesOnlyTheFoldersThatAreMissingAndTellsJdtlsOfTheHighestOne() throws Exception {
        Path billing = main.resolve("demo/billing");
        Path deeper = billing.resolve("deep/er");
        List<Boolean> seen = new ArrayList<>();
        fake.moveAnswers(moves(deeper, invoice)).onMove(request -> seen.add(Files.isDirectory(deeper)));

        try (var server = started()) {
            server.moveToPackage(List.of(invoice), PackageName.of("demo.billing.deep.er"));

            assertThat(seen).containsExactly(true);
            assertThat(deeper).doesNotExist();
            assertThat(billing.resolve("deep")).doesNotExist();
            assertThat(billing.resolve("Ledger.java")).exists();
            assertThat(eventsOfWatchedFiles())
                    .contains("Created " + billing.resolve("deep"), "Deleted " + billing.resolve("deep"))
                    .doesNotContain("Created " + deeper, "Created " + billing);
        }
    }

    @Test
    void putsThePackageUnderTheSourceFolderOfTheFiles() throws Exception {
        Path billing = root.resolve("src/test/java/demo/billing");
        fake.moveAnswers(moves(billing, invoiceTest));

        try (var server = started()) {
            server.moveToPackage(List.of(invoiceTest), BILLING);

            assertThat(fake.moveRequests().getFirst().destination()).isEqualTo(billing.toUri() + "/");
            assertThat(billing).doesNotExist();
            assertThat(main.resolve("demo/billing/InvoiceTest.java")).doesNotExist();
        }
    }

    @Test
    void doesNotTakeASourceFolderThatOnlySharesTheStartOfTheNameForTheOneOfTheFile() throws Exception {
        fake.listsSourcePaths(root.resolve("src/main/jav"));

        try (var server = started()) {
            assertThatThrownBy(() -> server.moveToPackage(List.of(invoice), BILLING))
                    .satisfies(thrown -> assertThat(refusalOf(thrown).parameters())
                            .containsEntry("reason", "outside-source-folders"));
        }
    }

    @Test
    void takesTheInnermostSourceFolderWhenOneContainsAnother() throws Exception {
        fake.listsSourcePaths(main, main.resolve("demo"));
        fake.moveAnswers(moves(main.resolve("demo/demo/billing"), invoice));

        try (var server = started()) {
            server.moveToPackage(List.of(invoice), BILLING);

            assertThat(fake.moveRequests().getFirst().destination())
                    .isEqualTo(main.resolve("demo/demo/billing").toUri() + "/");
        }
    }

    @FakeOnly(reason = JDTLS_FAILS)
    @Test
    void removesTheFolderItMadeWhenJdtlsDeclinesOrFails() throws Exception {
        Path archive = main.resolve("demo/archive");
        fake.moveAnswers(CompletableFuture.failedFuture(new IllegalStateException("the model is broken")));

        try (var server = started()) {
            assertThatThrownBy(() -> server.moveToPackage(List.of(invoice), ARCHIVE))
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("waiting for the move of 1 file to the package demo.archive");

            assertThat(archive).doesNotExist();
            assertThat(eventsOfWatchedFiles()).contains("Deleted " + archive);
        }
    }

    @Test
    void saysSoAndLeavesTheFolderWhenSomethingWasWrittenInItMeanwhile() throws Exception {
        Path archive = main.resolve("demo/archive");
        fake.moveAnswers(moves(archive, invoice)).onMove(request -> {
            try {
                Files.writeString(archive.resolve("Appeared.java"), "class Appeared {}\n");
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        });

        try (var server = started()) {
            assertThatThrownBy(() -> server.moveToPackage(List.of(invoice), ARCHIVE))
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("could not remove the folder " + archive)
                    .hasMessageContaining("made for the move");

            assertThat(archive.resolve("Appeared.java")).exists();
        }
    }

    @Test
    void refusesAPackageWhoseFolderCannotBeMadeBecauseAFileIsInTheWayAndDoesNotAskJdtls() throws Exception {
        Files.writeString(main.resolve("demo/archive"), "in the way");

        try (var server = started()) {
            assertThatThrownBy(() -> server.moveToPackage(List.of(invoice), ARCHIVE))
                    .satisfies(thrown -> {
                        var refusal = refusalOf(thrown);
                        assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_MOVABLE_TO_PACKAGE);
                        assertThat(refusal.parameters())
                                .containsEntry("reason", "folder-not-made")
                                .containsEntry("files", invoice.toString())
                                .containsEntry("package", "demo.archive");
                    });
            assertThat(fake.moveRequests()).isEmpty();
            assertThat(main.resolve("demo/archive")).hasContent("in the way");
        }
    }

    private void assertRefusedWithoutAsking(List<Path> files, PackageName target, String reason, List<Path> named)
            throws Exception {
        try (var server = started()) {
            assertThatThrownBy(() -> server.moveToPackage(files, target)).satisfies(thrown -> {
                var refusal = refusalOf(thrown);
                assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_MOVABLE_TO_PACKAGE);
                assertThat(refusal.parameters())
                        .containsEntry("reason", reason)
                        .containsEntry("package", target.value())
                        .containsEntry(
                                "files",
                                String.join(
                                        ", ", named.stream().map(Path::toString).toList()));
            });
            assertThat(fake.moveRequests()).isEmpty();
        }
    }

    @Test
    void refusesAFileThatIsNotAJavaSourceInsteadOfLettingJdtlsSkipIt() throws Exception {
        Path notes = main.resolve("demo/notes.txt");

        assertRefusedWithoutAsking(List.of(invoice, notes), BILLING, "not-a-java-source", List.of(notes));
    }

    @Test
    void refusesAJavaFileOfNoSourceFolder() throws Exception {
        Path unsourced = root.resolve("Unsourced.java");

        assertRefusedWithoutAsking(List.of(unsourced), BILLING, "outside-source-folders", List.of(unsourced));
    }

    @Test
    void refusesFilesOfTwoSourceFoldersAndNamesThemAll() throws Exception {
        assertRefusedWithoutAsking(
                List.of(invoice, invoiceTest), BILLING, "several-source-folders", List.of(invoice, invoiceTest));
    }

    @Test
    void refusesAFileAlreadyInThePackage() throws Exception {
        Path ledger = main.resolve("demo/billing/Ledger.java");

        assertRefusedWithoutAsking(List.of(invoice, ledger), BILLING, "already-in-package", List.of(ledger));
    }

    @Test
    void refusesAFileWhoseNameAFileOfThePackageHasAndOverwritesNothing() throws Exception {
        Path ledger = main.resolve("demo/Ledger.java");

        assertRefusedWithoutAsking(List.of(invoice, ledger), BILLING, "name-taken", List.of(ledger));
        assertThat(main.resolve("demo/billing/Ledger.java"))
                .hasContent("package demo.billing;\n\npublic class Ledger {}\n");
    }

    @Test
    void refusesTwoFilesOfTheSameNameThatWouldMeetInThePackage() throws Exception {
        Path ledger = main.resolve("demo/Ledger.java");
        Path other = main.resolve("demo/other/Ledger.java");

        assertRefusedWithoutAsking(List.of(ledger, other), ARCHIVE, "name-taken", List.of(other));
        assertThat(main.resolve("demo/archive")).doesNotExist();
    }

    @Test
    void refusesAFileOutsideTheProjectAMissingFileAndAFolderWithoutAskingJdtls() throws Exception {
        try (var server = started()) {
            assertThatThrownBy(() -> server.moveToPackage(List.of(invoice, root.resolve("../Elsewhere.java")), BILLING))
                    .satisfies(thrown ->
                            assertThat(refusalOf(thrown).key()).isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));
            assertThatThrownBy(() -> server.moveToPackage(List.of(main.resolve("demo/Nope.java")), BILLING))
                    .satisfies(thrown ->
                            assertThat(refusalOf(thrown).key()).isEqualTo(LanguageServerErrorKey.FILE_MISSING));
            assertThatThrownBy(() -> server.moveToPackage(List.of(main.resolve("demo")), BILLING))
                    .satisfies(
                            thrown -> assertThat(refusalOf(thrown).key()).isEqualTo(LanguageServerErrorKey.NOT_A_FILE));
            assertThat(fake.moveRequests()).isEmpty();
        }
    }

    @Test
    void refusesAsNotReadyWhileJdtlsIsStillImporting() throws Exception {
        fake.neverAnswersInitialize();

        try (var server = start()) {
            assertThatThrownBy(() -> server.moveToPackage(List.of(invoice), BILLING))
                    .satisfies(
                            thrown -> assertThat(refusalOf(thrown).key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
            assertThat(fake.moveRequests()).isEmpty();
        }
    }

    @Test
    void refusesWhenJdtlsAnswersNoChangeWhateverTheWay() throws Exception {
        try (var server = started()) {
            fake.moveAnswers(CompletableFuture.completedFuture(null));
            assertNothingToMove(server);
            fake.moveAnswers(CompletableFuture.completedFuture(new JdtlsServer.RefactorEdit(null, null, null)));
            assertNothingToMove(server);
            fake.moveAnswers(CompletableFuture.completedFuture(
                    new JdtlsServer.RefactorEdit(new WorkspaceEdit(List.of()), null, null)));
            assertNothingToMove(server);
            fake.moveAnswers(CompletableFuture.failedFuture(new ResponseErrorException(
                    new ResponseError(ResponseErrorCode.InvalidRequest, "cannot move", null))));
            assertNothingToMove(server);
        }
    }

    private void assertNothingToMove(JdtlsLanguageServer server) {
        assertThatThrownBy(() -> server.moveToPackage(List.of(invoice, payment), BILLING))
                .satisfies(thrown -> {
                    var refusal = refusalOf(thrown);
                    assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_MOVABLE_TO_PACKAGE);
                    assertThat(refusal.parameters())
                            .containsEntry("reason", "nothing-to-move")
                            .containsEntry("files", invoice + ", " + payment);
                });
    }

    @Test
    void failsWithTheMessageOfJdtlsWhenItCannotMoveWhatThePreChecksLetThrough() throws Exception {
        fake.moveAnswers(CompletableFuture.completedFuture(
                new JdtlsServer.RefactorEdit(null, null, "Failed to move the files because cannot find the package")));

        try (var server = started()) {
            assertThatThrownBy(() -> server.moveToPackage(List.of(invoice), BILLING))
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("jdtls cannot move the files to the package demo.billing")
                    .hasMessageContaining("Failed to move the files because cannot find the package");
        }
    }

    @Test
    void keepsTheInterruptionOfTheThreadThatWaits() throws Exception {
        fake.moveAnswers(moves(main.resolve("demo/billing"), invoice));

        try (var server = started()) {
            Thread.currentThread().interrupt();

            assertThatThrownBy(() -> server.moveToPackage(List.of(invoice), BILLING))
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining(
                            "Interrupted while waiting for the move of 1 file to the package demo.billing");
            assertThat(Thread.interrupted()).isTrue();
        }
    }
}
