package eu.gabier.daemon;

import static eu.gabier.daemon.FakeOnlyReasons.A_BUILD_THAT_LASTS;
import static eu.gabier.daemon.FakeOnlyReasons.A_START_THAT_LASTS;
import static eu.gabier.daemon.FakeOnlyReasons.INTERRUPTED_WHILE_WAITING;
import static eu.gabier.daemon.FakeOnlyReasons.JDTLS_FAILS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.editing.domain.Diagnostic.Severity;
import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.fixtures.FakeOnly;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code projectDiagnostics} of the jdtls adapter against {@link FakeJdtls}: it answers from what jdtls published for
 * files nobody opened, once the changes on disk are told and the build is finished, and it opens and reads no file.
 */
@ExtendWith(FakeOnlyGuard.class)
class JdtlsProjectDiagnosticsTest {

    private static final Duration LONG = Duration.ofSeconds(30);
    private static final Duration QUIET = Duration.ofMillis(300);
    private static final Duration SETTLE = Duration.ofMillis(300);

    @TempDir
    Path root;

    private final FakeJdtls fake = new FakeJdtls();
    private final List<String> log = new CopyOnWriteArrayList<>();

    @BeforeEach
    void listTheProject() throws Exception {
        root = root.toRealPath();
        fake.listsProjects(root.toUri().toString());
        fake.registersWatchersFor("**/*.java");
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

    private JdtlsLanguageServer start() {
        var settings = new JdtlsSettings(
                root, List.of(root), LONG, Duration.ofMillis(20), Duration.ofSeconds(5), JdtlsWorkspace.temporary());
        var server = JdtlsLanguageServer.start(fake, settings, QUIET, SETTLE, log::add);
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

    private static List<org.eclipse.lsp4j.Diagnostic> error(String text) {
        return List.of(lsp(2, 4, DiagnosticSeverity.Error, text));
    }

    @Test
    void answersTheFilesJdtlsPublishedProblemsForWithoutOpeningOrReadingAnyFileInTheOrderOfThePaths() throws Exception {
        // None of these files is on disk: the answer is what jdtls published, not what a walk finds.
        Path main = root.resolve("src/main/java/demo/Main.java");
        Path test = root.resolve("src/test/java/demo/MainTest.java");
        Path other = root.resolve("src/integration/java/demo/FlowTest.java");
        fake.onBuild(() -> {
            fake.publishes(test, error("in the tests"));
            fake.publishes(main, error("broken"));
            fake.publishes(other, List.of(lsp(0, 0, DiagnosticSeverity.Warning, "careful")));
        });

        try (var server = started()) {
            assertThat(server.projectDiagnostics())
                    .containsExactly(
                            new DiagnosedFile(
                                    other, List.of(new Diagnostic(Severity.WARNING, new Position(0, 0), "careful"))),
                            new DiagnosedFile(
                                    main, List.of(new Diagnostic(Severity.ERROR, new Position(2, 4), "broken"))),
                            new DiagnosedFile(
                                    test, List.of(new Diagnostic(Severity.ERROR, new Position(2, 4), "in the tests"))));
            assertThat(fake.opened()).isEmpty();
            assertThat(fake.sourcePathRequests()).isZero();
        }
    }

    @Test
    void leavesOutWhatHasNoProblemWhatIsNotAJavaFileAndWhatIsOutsideTheProject() throws Exception {
        Path main = root.resolve("src/main/java/demo/Main.java");
        Path fixed = root.resolve("src/main/java/demo/Fixed.java");
        Path outside = Path.of("/elsewhere/src/Shared.java");
        fake.onBuild(() -> {
            fake.publishes(main, error("broken"));
            fake.publishes(fixed, error("was broken"));
            fake.publishes(fixed, List.of());
            fake.publishes(root.resolve("src/main/java/demo/Clean.java"), List.of());
            fake.publishes(root.resolve("pom.xml"), error("not a Java file"));
            fake.publishes(outside, error("not in the project"));
        });

        try (var server = started()) {
            assertThat(server.projectDiagnostics())
                    .extracting(DiagnosedFile::file)
                    .containsExactly(main);
        }
    }

    @Test
    void answersNothingForAProjectJdtlsPublishedNoProblemFor() throws Exception {
        try (var server = started()) {
            assertThat(server.projectDiagnostics()).isEmpty();
        }
    }

    @Test
    void asksJdtlsToBuildAnIncrementalBuildEachTimeAndTakesWhatIsPublishedAfterIt() throws Exception {
        Path main = root.resolve("src/main/java/demo/Main.java");

        try (var server = started()) {
            fake.onBuild(() -> fake.publishes(main, error("first")));
            assertThat(server.projectDiagnostics())
                    .extracting(DiagnosedFile::diagnostics)
                    .extracting(problems -> problems.getFirst().message())
                    .containsExactly("first");

            fake.onBuild(() -> fake.publishes(main, List.of()));
            assertThat(server.projectDiagnostics()).isEmpty();
            assertThat(fake.buildRequests()).containsExactly(false, false);
        }
    }

    @FakeOnly(reason = A_BUILD_THAT_LASTS)
    @Test
    void doesNotAnswerWhileTheBuildIsRunningAndAnswersWhatItPublished() throws Exception {
        Path main = root.resolve("src/main/java/demo/Main.java");
        var building = new CompletableFuture<Object>();
        fake.buildAnswers(building);

        try (var server = started()) {
            var answer = CompletableFuture.supplyAsync(server::projectDiagnostics);

            // A running build is not a clean project: the call waits, however long it takes.
            LockSupport.parkNanos(Duration.ofMillis(700).toNanos());
            assertThat(answer).isNotDone();

            fake.publishes(main, error("found by the build"));
            building.complete(null);

            assertThat(answer.get(10, TimeUnit.SECONDS))
                    .extracting(DiagnosedFile::file)
                    .containsExactly(main);
        }
    }

    @Test
    void waitsAfterTheBuildForWhatJdtlsPublishesAShortTimeLaterAsItDoesRightAfterItsStart() throws Exception {
        Path main = root.resolve("src/main/java/demo/Main.java");
        fake.onBuild(() -> Thread.ofVirtual().start(() -> {
            LockSupport.parkNanos(Duration.ofMillis(150).toNanos());
            fake.publishes(main, error("published after the answer"));
        }));

        try (var server = started()) {
            long begun = System.nanoTime();

            var files = server.projectDiagnostics();

            assertThat(files).extracting(DiagnosedFile::file).containsExactly(main);
            assertThat(Duration.ofNanos(System.nanoTime() - begun)).isGreaterThanOrEqualTo(SETTLE);
        }
    }

    @FakeOnly(reason = A_START_THAT_LASTS)
    @Test
    void doesNotAskForTheBuildBeforeJdtlsHasRegisteredTheFilesItWatchesAndAnswersOnceItHas() throws Exception {
        Path main = root.resolve("src/main/java/demo/Main.java");
        fake.holdsBackTheWatchers();
        fake.onBuild(() -> fake.publishes(main, error("published once initialized")));

        try (var server = started()) {
            var answer = CompletableFuture.supplyAsync(server::projectDiagnostics);

            LockSupport.parkNanos(Duration.ofMillis(500).toNanos());
            assertThat(answer).isNotDone();
            assertThat(fake.received()).doesNotContain("buildWorkspace");

            fake.registersTheHeldBackWatchers();

            assertThat(answer.get(10, TimeUnit.SECONDS))
                    .extracting(DiagnosedFile::file)
                    .containsExactly(main);
        }
    }

    @Test
    void takesInAFileEditedOnDiskBeforeTheBuildSoThatTheBuildSeesIt() throws Exception {
        Path main = root.resolve("src/main/java/demo/Main.java");
        Files.createDirectories(main.getParent());
        Files.writeString(main, "class Main {}\n");

        try (var server = started()) {
            Files.writeString(main, "class Main { int edited; }\n");
            fake.onBuild(() -> fake.publishes(main, error("the edit is wrong")));
            int before = fake.received().size();

            assertThat(server.projectDiagnostics())
                    .extracting(DiagnosedFile::file)
                    .containsExactly(main);

            assertThat(fake.received().subList(before, fake.received().size()))
                    .containsExactly("didChangeWatchedFiles", "acknowledged .gabier-changes-taken-1", "buildWorkspace");
        }
    }

    @Test
    void refusesAsNotReadyWhileJdtlsIsStillImportingAndAsksItNothing() throws Exception {
        fake.neverAnswersInitialize();

        try (var server = start()) {
            assertThatThrownBy(server::projectDiagnostics)
                    .isInstanceOfSatisfying(
                            LanguageServerException.class,
                            refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
            assertThat(fake.buildRequests()).isEmpty();
        }
    }

    @FakeOnly(reason = JDTLS_FAILS)
    @Test
    void refusesWithTheErrorOfJdtlsWhenItFailsTheBuild() throws Exception {
        fake.buildAnswers(CompletableFuture.failedFuture(new org.eclipse.lsp4j.jsonrpc.ResponseErrorException(
                new org.eclipse.lsp4j.jsonrpc.messages.ResponseError(
                        ResponseErrorCode.InternalError, "the build failed", null))));

        try (var server = started()) {
            assertThatThrownBy(server::projectDiagnostics)
                    .isInstanceOfSatisfying(
                            LanguageServerException.class,
                            refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.REQUEST_FAILED))
                    .hasMessageContaining("the build failed");
        }
    }

    @FakeOnly(reason = INTERRUPTED_WHILE_WAITING)
    @Test
    void keepsTheInterruptionOfTheThreadThatWaitsForTheBuild() throws Exception {
        try (var server = started()) {
            Thread.currentThread().interrupt();

            assertThatThrownBy(server::projectDiagnostics)
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("Interrupted while waiting for the diagnostics of the project");
            assertThat(Thread.interrupted()).isTrue();
        }
    }

    @Test
    void followsTheDiskForTheFilesJdtlsAskedToWatch() throws Exception {
        try (var server = started();
                var disk = server.watchDisk()) {
            Files.writeString(root.resolve("Greeter.java"), "class Greeter {}");

            assertThat(disk.await(Duration.ofSeconds(10))).isEqualTo(DiskChanges.Signal.CHANGED);
        }
    }

    @FakeOnly(reason = INTERRUPTED_WHILE_WAITING)
    @Test
    void keepsTheInterruptionOfTheThreadThatWaitsForJdtlsToRegisterWhatItWatches() throws Exception {
        fake.holdsBackTheWatchers();
        try (var server = started()) {
            Thread.currentThread().interrupt();

            assertThatThrownBy(server::watchDisk)
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("Interrupted while waiting for jdtls to register the files it watches");
            assertThat(Thread.interrupted()).isTrue();
        }
    }
}
