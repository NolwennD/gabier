package eu.gabier.daemon;

import static eu.gabier.daemon.FakeOnlyReasons.INTERRUPTED_WHILE_WAITING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.editing.domain.Diagnostic.Severity;
import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.fixtures.FakeOnly;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code projectDiagnostics} of the jdtls adapter against {@link FakeJdtls}: how it chooses the files (the source folders
 * jdtls lists for the project, walked on disk, nothing guessed from their names), what it leaves out, and that it asks
 * for their diagnostics together, as it does for files named by the caller.
 */
@ExtendWith(FakeOnlyGuard.class)
class JdtlsProjectDiagnosticsTest {

    private static final Duration LONG = Duration.ofSeconds(30);
    private static final Duration QUIET = Duration.ofMillis(300);

    @TempDir
    Path root;

    private final FakeJdtls fake = new FakeJdtls();
    private final List<String> log = new CopyOnWriteArrayList<>();

    @BeforeEach
    void writeTheProject() throws IOException {
        root = root.toRealPath();
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

    private Path write(String relative) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "class X {}\n");
        // jdtls publishes for every file it opens, an empty list for a file that is clean.
        fake.publishesOnOpen(file, List.of());

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

    private static org.eclipse.lsp4j.Diagnostic lsp(int line, int column, DiagnosticSeverity severity, String text) {
        return new org.eclipse.lsp4j.Diagnostic(
                new Range(
                        new org.eclipse.lsp4j.Position(line, column), new org.eclipse.lsp4j.Position(line, column + 1)),
                text,
                severity,
                "Java");
    }

    private List<Path> openedFiles() {
        return fake.opened().stream()
                .map(opened -> Path.of(java.net.URI.create(opened.uri())))
                .toList();
    }

    @Test
    void diagnosesEveryJavaFileUnderTheSourceFoldersJdtlsListsWhateverTheyAreCalledInTheOrderOfThePaths()
            throws Exception {
        Path main = write("src/main/java/demo/Main.java");
        Path shop = write("src/main/java/demo/shop/Shop.java");
        Path test = write("src/test/java/demo/MainTest.java");
        Path other = write("src/integration/java/demo/FlowTest.java");
        Path generated = write("build/generated/sources/demo/Generated.java");
        fake.listsSourcePaths(
                root.resolve("src/test/java"),
                root.resolve("src/main/java"),
                root.resolve("src/integration/java"),
                root.resolve("build/generated/sources"));
        fake.publishesOnOpen(main, List.of(lsp(2, 4, DiagnosticSeverity.Error, "broken")));
        fake.publishesOnOpen(other, List.of(lsp(0, 0, DiagnosticSeverity.Warning, "careful")));

        try (var server = started()) {
            assertThat(server.projectDiagnostics())
                    .containsExactly(
                            new DiagnosedFile(generated, List.of()),
                            new DiagnosedFile(
                                    other, List.of(new Diagnostic(Severity.WARNING, new Position(0, 0), "careful"))),
                            new DiagnosedFile(
                                    main, List.of(new Diagnostic(Severity.ERROR, new Position(2, 4), "broken"))),
                            new DiagnosedFile(shop, List.of()),
                            new DiagnosedFile(test, List.of()));
        }
    }

    @Test
    void asksTheDiagnosticsOfAllTheFilesTogetherAndClosesThemOnceAnswered() throws Exception {
        Path main = write("src/main/java/demo/Main.java");
        Path test = write("src/test/java/demo/MainTest.java");
        fake.listsSourcePaths(root.resolve("src/main/java"), root.resolve("src/test/java"));

        try (var server = started()) {
            server.projectDiagnostics();

            assertThat(openedFiles()).containsExactly(main, test);
            assertThat(fake.closed(2))
                    .containsExactly(main.toUri().toString(), test.toUri().toString());
            assertThat(fake.sourcePathRequests()).isEqualTo(1);
        }
    }

    @Test
    void leavesOutWhatIsNotAJavaFileOrIsNotUnderASourceFolder() throws Exception {
        Path main = write("src/main/java/demo/Main.java");
        write("src/main/java/demo/notes.txt");
        write("src/main/java/demo/Main.java.orig");
        write("Unsourced.java");
        write("src/main/resources/Config.java");
        Files.createDirectories(root.resolve("src/main/java/demo/Folder.java"));
        fake.listsSourcePaths(root.resolve("src/main/java"));

        try (var server = started()) {
            assertThat(server.projectDiagnostics())
                    .extracting(DiagnosedFile::file)
                    .containsExactly(main);
        }
    }

    @Test
    void answersEachFileOnceWhenSourceFoldersNest() throws Exception {
        Path main = write("src/main/java/demo/Main.java");
        fake.listsSourcePaths(root.resolve("src"), root.resolve("src/main/java"), root.resolve("src/main/java"));

        try (var server = started()) {
            assertThat(server.projectDiagnostics())
                    .extracting(DiagnosedFile::file)
                    .containsExactly(main);
            assertThat(openedFiles()).containsExactly(main);
        }
    }

    @Test
    void leavesOutTheFilesOfASourceFolderThatIsNotUnderAFolderJdtlsImportedAsItRefusesThemWhenAskedByHand()
            throws Exception {
        Path main = write("src/main/java/demo/Main.java");
        Path shared = Files.createTempDirectory("gabier-shared-").toRealPath();
        try {
            Files.writeString(shared.resolve("Shared.java"), "class Shared {}\n");
            fake.publishesOnOpen(shared.resolve("Shared.java"), List.of());
            fake.listsSourcePaths(root.resolve("src/main/java"), shared);

            try (var server = started()) {
                assertThat(server.projectDiagnostics())
                        .extracting(DiagnosedFile::file)
                        .containsExactly(main);
                assertThatThrownBy(() -> server.diagnostics(shared.resolve("Shared.java")))
                        .isInstanceOfSatisfying(
                                LanguageServerException.class,
                                refusal -> assertThat(refusal.key())
                                        .isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));
            }
        } finally {
            Files.delete(shared.resolve("Shared.java"));
            Files.delete(shared);
        }
    }

    @Test
    void skipsASourceFolderThatIsNotOnDisk() throws Exception {
        Path main = write("src/main/java/demo/Main.java");
        fake.listsSourcePaths(root.resolve("src/gone/java"), root.resolve("src/main/java"));

        try (var server = started()) {
            assertThat(server.projectDiagnostics())
                    .extracting(DiagnosedFile::file)
                    .containsExactly(main);
        }
    }

    @Test
    void answersNothingAndOpensNothingForAProjectWithNoJavaFile() throws Exception {
        Files.createDirectories(root.resolve("src/main/java"));
        fake.listsSourcePaths(root.resolve("src/main/java"));

        try (var server = started()) {
            assertThat(server.projectDiagnostics()).isEmpty();
            assertThat(fake.opened()).isEmpty();
        }
    }

    @Test
    void takesInAFileCreatedSinceTheImportBecauseItWalksTheFoldersAndDoesNotRememberThem() throws Exception {
        write("src/main/java/demo/Main.java");
        fake.listsSourcePaths(root.resolve("src/main/java"));

        try (var server = started()) {
            assertThat(server.projectDiagnostics()).hasSize(1);
            Path created = write("src/main/java/demo/Created.java");

            assertThat(server.projectDiagnostics())
                    .extracting(DiagnosedFile::file)
                    .contains(created);
        }
    }

    @Test
    void refusesAsNotReadyWhileJdtlsIsStillImporting() throws Exception {
        fake.neverAnswersInitialize();

        try (var server = start()) {
            assertThatThrownBy(server::projectDiagnostics)
                    .isInstanceOfSatisfying(
                            LanguageServerException.class,
                            refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
            assertThat(fake.sourcePathRequests()).isZero();
        }
    }

    @Test
    void failsWhenAFolderOfTheSourcesCannotBeListed() throws Exception {
        write("src/main/java/demo/Main.java");
        Path demo = root.resolve("src/main/java/demo");
        fake.listsSourcePaths(root.resolve("src/main/java"));
        Files.setPosixFilePermissions(demo, PosixFilePermissions.fromString("--x------"));

        try (var server = started()) {
            assertThatThrownBy(server::projectDiagnostics).isInstanceOf(UncheckedIOException.class);
        } finally {
            Files.setPosixFilePermissions(demo, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void failsWhenASourceFolderCannotBeOpened() throws Exception {
        write("src/main/java/demo/Main.java");
        Path sources = root.resolve("src/main/java");
        fake.listsSourcePaths(sources);
        Files.setPosixFilePermissions(sources, PosixFilePermissions.fromString("--x------"));

        try (var server = started()) {
            assertThatThrownBy(server::projectDiagnostics).isInstanceOf(UncheckedIOException.class);
        } finally {
            Files.setPosixFilePermissions(sources, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @FakeOnly(reason = INTERRUPTED_WHILE_WAITING)
    @Test
    void keepsTheInterruptionOfTheThreadThatWaitsForTheSourceFolders() throws Exception {
        write("src/main/java/demo/Main.java");
        fake.listsSourcePaths(root.resolve("src/main/java"));

        try (var server = started()) {
            Thread.currentThread().interrupt();

            assertThatThrownBy(server::projectDiagnostics)
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("Interrupted while waiting for the source folders of the project");
            assertThat(Thread.interrupted()).isTrue();
        }
    }

    @Test
    void saysWhatJdtlsAnsweredWhenItDoesNotListTheSourceFoldersAsExpected() throws Exception {
        fake.sourcePathsAnswers(CompletableFuture.completedFuture("nothing"));

        try (var server = started()) {
            assertThatThrownBy(server::projectDiagnostics)
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("java.project.listSourcePaths answered nothing");
        }
    }
}
