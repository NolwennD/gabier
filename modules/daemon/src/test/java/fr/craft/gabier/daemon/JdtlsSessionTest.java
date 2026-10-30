package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The start and the end of a jdtls session, against {@link FakeJdtls}: a language server in this process, over pipes,
 * and a child process standing in for the jdtls process. No jdtls is started.
 */
class JdtlsSessionTest {

    private static final Path PROJECT_ROOT = Path.of("/work/project");
    private static final Path MODULE_A = PROJECT_ROOT.resolve("modules/a");
    private static final Path MODULE_B = PROJECT_ROOT.resolve("modules/b");
    private static final String URI_A = "file:///work/project/modules/a";
    private static final String URI_B = "file:///work/project/modules/b/";
    private static final Duration LONG = Duration.ofSeconds(30);
    private static final Duration SHORT = Duration.ofMillis(500);
    /** Long enough for the whole start of the fake, short enough to let it pass in a test. */
    private static final Duration MEDIUM = Duration.ofSeconds(2);

    private final FakeJdtls fake = new FakeJdtls();
    private final List<String> log = new CopyOnWriteArrayList<>();
    private final List<PublishDiagnosticsParams> published = new CopyOnWriteArrayList<>();

    @AfterEach
    void leaveNothingBehind() throws Exception {
        // A test that interrupts its own thread must not interrupt the next one.
        Thread.interrupted();
        fake.close();
        if (fake.data() != null && Files.exists(fake.data())) {
            delete(fake.data());
        }
    }

    private JdtlsSettings settings(Duration timeout, List<Path> expectedProjects) {
        return new JdtlsSettings(PROJECT_ROOT, expectedProjects, timeout, Duration.ofMillis(20), Duration.ofSeconds(5));
    }

    private JdtlsSession start(JdtlsStarter starter, Duration timeout, Path... expectedProjects) throws Exception {
        return JdtlsSession.start(starter, settings(timeout, List.of(expectedProjects)), log::add, published::add);
    }

    private JdtlsSession start(Path... expectedProjects) throws Exception {
        return start(fake, LONG, expectedProjects);
    }

    private static void delete(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
    }

    @Nested
    class Start {

        @Test
        void givesBackASessionOnceJdtlsAnsweredInitializeAndSentServiceReady() throws Exception {
            fake.listsProjects(URI_A);

            try (JdtlsSession session = start()) {
                assertThat(fake.initializedNotifications()).isEqualTo(1);
                assertThat(session.pid()).isEqualTo(fake.process().pid());
            }
        }

        @Test
        void initializesJdtlsOnTheRootOfTheProjectWithTheCapabilitiesOfARefactoringClient() throws Exception {
            fake.listsProjects(URI_A);

            try (JdtlsSession session = start()) {
                assertThat(fake.initializeParams().getRootUri()).isEqualTo("file:///work/project");
                assertThat(fake.initializeParams().getCapabilities()).isEqualTo(JdtlsCapabilities.forRefactoring());
            }
        }

        @Test
        void startsJdtlsWithADataDirectoryOfItsOwn() throws Exception {
            fake.listsProjects(URI_A);

            try (JdtlsSession session = start()) {
                assertThat(session.dataDirectory()).isEqualTo(fake.data()).isDirectory();
                assertThat(session.dataDirectory().getFileName().toString()).startsWith("gabier-jdtls-data-");
            }
        }

        @Test
        void knowsTheProjectsJdtlsListed() throws Exception {
            fake.listsProjects(URI_A, URI_B, "file:///work/project/build-logic");

            try (JdtlsSession session = start(MODULE_A, MODULE_B)) {
                assertThat(session.projects()).containsExactly(MODULE_A, MODULE_B, PROJECT_ROOT.resolve("build-logic"));
                assertThat(fake.commands()).containsExactly("java.project.getAll");
            }
        }

        @Test
        void asksOnceWhenNoProjectIsExpected() throws Exception {
            fake.listsProjects();

            try (JdtlsSession session = start()) {
                assertThat(session.projects()).isEmpty();
                assertThat(fake.projectRequests()).isEqualTo(1);
            }
        }

        @Test
        void asksAgainUntilEveryExpectedProjectIsListed() throws Exception {
            fake.listsProjects(URI_A).listsProjects(URI_A).listsProjects(URI_A, URI_B);

            try (JdtlsSession session = start(MODULE_A, MODULE_B)) {
                assertThat(fake.projectRequests()).isEqualTo(3);
                assertThat(session.projects()).containsExactly(MODULE_A, MODULE_B);
            }
        }

        @Test
        void handsTheDiagnosticsPublishedFromTheFirstMessageToTheListener() throws Exception {
            fake.listsProjects(URI_A).publishesDiagnosticsFor("file:///work/project/modules/a/A.java");

            try (JdtlsSession session = start()) {
                assertThat(session.projects()).containsExactly(MODULE_A);
            }
            assertThat(published)
                    .extracting(PublishDiagnosticsParams::getUri)
                    .containsExactly("file:///work/project/modules/a/A.java");
        }

        @Test
        void tellsWhatItStartedAndWhatJdtlsSays() throws Exception {
            fake.listsProjects(URI_A);

            try (JdtlsSession session = start()) {
                assertThat(log)
                        .contains(
                                "jdtls started, pid " + session.pid() + ", data " + session.dataDirectory(),
                                "jdtls status ServiceReady: ServiceReady");
            }
        }

        @Test
        void givesTheServerAndAWatchToWhoKeepsTalkingToJdtls() throws Exception {
            fake.listsProjects(URI_A);

            try (JdtlsSession session = start()) {
                var answer =
                        session.server().getWorkspaceService().executeCommand(new ExecuteCommandParams("x", List.of()));

                assertThat(session.newWatch().await("the project list", () -> answer))
                        .isEqualTo(List.of(URI_A));
                session.newWatch().pause("a short pause", Duration.ofMillis(1));
            }
        }

        @Test
        void everyWatchHasADeadlineOfItsOwnStartedWhenItIsCreated() throws Exception {
            fake.listsProjects(URI_A);

            try (JdtlsSession session = start(fake, MEDIUM)) {
                Thread.sleep(MEDIUM.plusMillis(200));
                var answer =
                        session.server().getWorkspaceService().executeCommand(new ExecuteCommandParams("x", List.of()));

                assertThat(session.newWatch().await("the project list", () -> answer))
                        .isEqualTo(List.of(URI_A));
            }
        }
    }

    @Nested
    class StartFailing {

        @Test
        void failsAtOnceWhenJdtlsSendsAnErrorStatus() throws Exception {
            fake.sendsStatus("Error");
            long begin = System.nanoTime();

            assertThatThrownBy(() -> start())
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("jdtls sent the status Error: Error")
                    .hasMessageContaining("the language/status notification of type ServiceReady");

            assertThat(Duration.ofNanos(System.nanoTime() - begin)).isLessThan(Duration.ofSeconds(10));
            assertNothingLeftBehind();
        }

        @Test
        void failsAtOnceWithTheExitCodeWhenJdtlsExits() throws Exception {
            fake.child("sh", "-c", "exit 3").neverAnswersInitialize();
            long begin = System.nanoTime();

            assertThatThrownBy(() -> start())
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("jdtls exited with code 3")
                    .hasMessageContaining("the answer to initialize");

            assertThat(Duration.ofNanos(System.nanoTime() - begin)).isLessThan(Duration.ofSeconds(10));
            assertNothingLeftBehind();
        }

        @Test
        void failsAtOnceWhenTheStreamToJdtlsIsClosedAndJdtlsIsGone() throws Exception {
            fake.child("sh", "-c", "exit 4");
            JdtlsStarter closedStream = data -> {
                JdtlsConnection connection = fake.start(data);
                try {
                    fake.closePipes();
                } catch (Exception e) {
                    throw new IOException(e);
                }
                return connection;
            };

            assertThatThrownBy(() -> start(closedStream, LONG))
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("jdtls exited with code 4")
                    .hasMessageContaining("the answer to initialize");

            assertNothingLeftBehind();
        }

        @Test
        void aTimeoutNamesTheAnswerToInitialize() throws Exception {
            fake.neverAnswersInitialize();

            assertThatThrownBy(() -> start(fake, SHORT))
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("Timed out")
                    .hasMessageContaining("waiting for the answer to initialize");

            assertNothingLeftBehind();
        }

        @Test
        void aTimeoutNamesTheServiceReadyStatus() throws Exception {
            fake.sendsStatus("");

            assertThatThrownBy(() -> start(fake, SHORT))
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("Timed out")
                    .hasMessageContaining("the language/status notification of type ServiceReady");

            assertNothingLeftBehind();
        }

        @Test
        void aTimeoutNamesTheProjectsStillMissing() throws Exception {
            fake.listsProjects(URI_A);

            assertThatThrownBy(() -> start(fake, SHORT, MODULE_A, MODULE_B))
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("Timed out")
                    .hasMessageContaining("jdtls to list the modules [" + MODULE_A + ", " + MODULE_B + "]")
                    .hasMessageContaining("still missing [" + MODULE_B + "]")
                    .hasMessageContaining("it listed [" + MODULE_A + "]");

            assertNothingLeftBehind();
        }

        @Test
        void aListOfProjectsThatIsNotAListIsAFailure() throws Exception {
            fake.answersNothingToProjects();

            assertThatThrownBy(() -> start())
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("java.project.getAll answered null, which is not a list of projects");

            assertNothingLeftBehind();
        }

        @Test
        void removesTheDataDirectoryWhenJdtlsCannotBeStarted() {
            AtomicReference<Path> data = new AtomicReference<>();
            JdtlsStarter failing = directory -> {
                data.set(directory);
                throw new IOException("no such launcher");
            };

            assertThatThrownBy(() -> start(failing, LONG))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("no such launcher");

            assertThat(data.get()).doesNotExist();
        }

        @Test
        void aFailureToCloseAfterAFailureToStartIsAddedToItNotInstead() throws Exception {
            Thread test = Thread.currentThread();
            // The test thread is interrupted while it awaits initialize, and again while the failed start closes.
            fake.neverAnswersInitialize()
                    .shutdown(FakeJdtls.Shutdown.NEVER_ANSWERS)
                    .onInitialize(test::interrupt)
                    .onShutdown(test::interrupt);

            assertThatThrownBy(() -> start())
                    .isInstanceOf(InterruptedException.class)
                    .satisfies(failure -> assertThat(failure.getSuppressed())
                            .singleElement()
                            .isInstanceOf(InterruptedException.class));

            assertNothingLeftBehind();
        }

        private void assertNothingLeftBehind() {
            assertThat(fake.process().isAlive()).as("the jdtls process").isFalse();
            assertThat(fake.data()).as("the data directory").doesNotExist();
        }
    }

    @Nested
    class Close {

        @Test
        void asksJdtlsToShutDownAndToExit() throws Exception {
            fake.listsProjects(URI_A);
            JdtlsSession session = start();

            session.close();

            assertThat(fake.shutdownRequests()).isEqualTo(1);
            assertThat(fake.awaitExitNotification()).isTrue();
            assertThat(log).doesNotContain("jdtls did not shut down cleanly");
        }

        @Test
        void endsTheWholeProcessTree() throws Exception {
            fake.listsProjects(URI_A).child("sh", "-c", "sleep 300 & wait");
            JdtlsSession session = start();
            List<ProcessHandle> tree = fake.process().descendants().toList();
            assertThat(tree).as("the process the launcher started in turn").isNotEmpty();

            session.close();

            assertThat(fake.process().isAlive()).isFalse();
            assertThat(tree).noneMatch(ProcessHandle::isAlive);
        }

        @Test
        void endsTheChildrenOfALauncherThatDiedBeforeTheClose() throws Exception {
            fake.listsProjects(URI_A).child("sh", "-c", "sleep 300 & wait");
            JdtlsSession session = start();
            List<ProcessHandle> orphans = fake.process().descendants().toList();
            assertThat(orphans).as("the process the launcher started in turn").isNotEmpty();
            fake.process().destroyForcibly().onExit().get();
            assertThat(orphans).as("orphaned, not dead").allMatch(ProcessHandle::isAlive);
            assertThat(fake.process().descendants())
                    .as("the tree is now unreadable")
                    .isEmpty();

            session.close();

            for (ProcessHandle orphan : orphans) {
                orphan.onExit().get(10, TimeUnit.SECONDS);
            }
            assertThat(orphans).noneMatch(ProcessHandle::isAlive);
            assertThat(session.dataDirectory()).doesNotExist();
        }

        @Test
        void deletesTheDataDirectoryWithWhatJdtlsWroteInIt() throws Exception {
            fake.listsProjects(URI_A);
            JdtlsSession session = start();
            Files.createDirectories(session.dataDirectory().resolve("workspace/.metadata"));
            Files.writeString(session.dataDirectory().resolve("workspace/.metadata/.log"), "jdtls was here");

            session.close();

            assertThat(session.dataDirectory()).doesNotExist();
        }

        @Test
        void sendsNoShutdownRequestToAJdtlsThatIsAlreadyDead() throws Exception {
            fake.listsProjects(URI_A);
            JdtlsSession session = start();
            fake.process().destroyForcibly().onExit().get();

            session.close();

            assertThat(fake.shutdownRequests()).isZero();
            assertThat(session.dataDirectory()).doesNotExist();
        }

        @Test
        void stillEndsJdtlsWhenItRefusesToShutDown() throws Exception {
            fake.listsProjects(URI_A).shutdown(FakeJdtls.Shutdown.FAILS);
            JdtlsSession session = start();

            session.close();

            assertThat(log)
                    .anyMatch(line -> line.startsWith("jdtls did not shut down cleanly: ")
                            && line.contains("ExecutionException"));
            assertThat(fake.process().isAlive()).isFalse();
            assertThat(session.dataDirectory()).doesNotExist();
        }

        @Test
        void tellsWhenAProcessOfTheTreeIsStillRunningAfterTheKill() throws Exception {
            fake.listsProjects(URI_A).whoseHandleExitsAs(new CompletableFuture<>());
            JdtlsSession session = JdtlsSession.start(fake, shortShutdown(), log::add, published::add);

            session.close();

            assertThat(log)
                    .anyMatch(line -> line.equals("process " + session.pid() + " is still running: "
                            + "java.util.concurrent.TimeoutException"));
            assertThat(session.dataDirectory()).doesNotExist();
        }

        @Test
        void tellsWhenTheEndOfAProcessOfTheTreeCannotBeAwaited() throws Exception {
            fake.listsProjects(URI_A)
                    .whoseHandleExitsAs(CompletableFuture.failedFuture(new IllegalStateException("lost")));
            JdtlsSession session = JdtlsSession.start(fake, shortShutdown(), log::add, published::add);

            session.close();

            assertThat(log)
                    .anyMatch(line -> line.startsWith("process " + session.pid() + " is still running: ")
                            && line.contains("ExecutionException")
                            && line.contains("lost"));
            assertThat(session.dataDirectory()).doesNotExist();
        }

        private JdtlsSettings shortShutdown() {
            return new JdtlsSettings(PROJECT_ROOT, List.of(), LONG, Duration.ofMillis(20), Duration.ofMillis(300));
        }

        @Test
        void stillEndsJdtlsWhenItNeverAnswersTheShutdown() throws Exception {
            fake.listsProjects(URI_A).shutdown(FakeJdtls.Shutdown.NEVER_ANSWERS);
            JdtlsSession session = JdtlsSession.start(
                    fake,
                    new JdtlsSettings(PROJECT_ROOT, List.of(), LONG, Duration.ofMillis(20), Duration.ofMillis(300)),
                    log::add,
                    published::add);

            session.close();

            assertThat(log)
                    .anyMatch(line ->
                            line.startsWith("jdtls did not shut down cleanly: ") && line.contains("TimeoutException"));
            assertThat(fake.process().isAlive()).isFalse();
            assertThat(session.dataDirectory()).doesNotExist();
        }

        @Test
        void stillEndsJdtlsWhenTheStreamToItIsBroken() throws Exception {
            fake.listsProjects(URI_A);
            JdtlsSession session = start();
            fake.closePipes();

            session.close();

            assertThat(log).anyMatch(line -> line.startsWith("jdtls did not shut down cleanly: "));
            assertThat(fake.process().isAlive()).isFalse();
            assertThat(session.dataDirectory()).doesNotExist();
        }

        @Test
        void closingTwiceDoesNothingTheSecondTime() throws Exception {
            fake.listsProjects(URI_A);
            JdtlsSession session = start();
            session.close();

            session.close();

            assertThat(fake.shutdownRequests()).isEqualTo(1);
        }

        @Test
        void anInterruptionDoesNotStopTheCleaningUp() throws Exception {
            fake.listsProjects(URI_A).shutdown(FakeJdtls.Shutdown.NEVER_ANSWERS);
            JdtlsSession session = start();
            Thread.currentThread().interrupt();

            assertThatThrownBy(session::close).isInstanceOf(InterruptedException.class);

            // The kill was sent before the interruption was noticed; the process is reaped a moment later.
            assertThat(fake.process().waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(session.dataDirectory()).doesNotExist();
        }
    }
}
