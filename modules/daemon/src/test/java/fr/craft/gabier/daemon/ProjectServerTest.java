package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.Diagnostic;
import fr.craft.gabier.editing.domain.FileChange;
import fr.craft.gabier.editing.domain.LanguageServerErrorKey;
import fr.craft.gabier.editing.domain.LanguageServerException;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.protocol.JdtlsState;
import fr.craft.gabier.shared.error.domain.ErrorStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The project of a daemon: jdtls is started by the first request that needs it, once, whatever the number of requests.
 * No jdtls here: the starter is a hand-written one that counts how many times it is asked, and the language server it
 * hands out is scripted by the test.
 */
class ProjectServerTest {

    private static final Path ROOT = Path.of("/work/demo");
    private static final Path FILE = ROOT.resolve("src/Main.java");
    private static final Duration LONG = Duration.ofSeconds(30);

    /** One thread for each request, however many there are: they all wait for the same start. */
    private static final Executor REQUESTS = Executors.newVirtualThreadPerTaskExecutor();

    private final CountingStarter starter = new CountingStarter();
    private final List<String> log = new CopyOnWriteArrayList<>();
    private final ProjectServer project = new ProjectServer(ROOT, starter, log::add);

    /** Hands out the language servers it is given, in turn, and counts the starts. */
    private static final class CountingStarter implements ProjectServer.Starter {

        private final AtomicInteger starts = new AtomicInteger();
        private final List<ScriptedServer> servers = new CopyOnWriteArrayList<>();
        private final List<ScriptedServer> toHandOut = new CopyOnWriteArrayList<>();

        ScriptedServer willStart(ScriptedServer server) {
            toHandOut.add(server);

            return server;
        }

        @Override
        public StartedLanguageServer start() {
            starts.incrementAndGet();
            var server = toHandOut.isEmpty() ? new ScriptedServer() : toHandOut.removeFirst();
            servers.add(server);

            return server;
        }
    }

    /** A language server that is ready at once, unless the test holds its start or makes it fail. */
    private static final class ScriptedServer implements StartedLanguageServer {

        static final ChangeSet RENAMED =
                new ChangeSet(List.of(new FileChange.CreateFile(Path.of("/work/demo/Other.java"))));
        static final Diagnostic WARNING = new Diagnostic(Diagnostic.Severity.WARNING, new Position(1, 2), "unused");

        private final CountDownLatch released = new CountDownLatch(1);
        private final AtomicInteger waiting = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();
        private volatile JdtlsState state = JdtlsState.STARTING;
        private volatile String failure;
        private volatile Exception closeFailure;
        private final boolean held;

        ScriptedServer() {
            this(false);
        }

        private ScriptedServer(boolean held) {
            this.held = held;
            if (!held) {
                finish(null);
            }
        }

        static ScriptedServer held() {
            return new ScriptedServer(true);
        }

        static ScriptedServer failing(String reason) {
            var server = new ScriptedServer(true);
            server.finish(reason);

            return server;
        }

        /** The start is over: ready, or failed with this reason. */
        void finish(String reason) {
            failure = reason;
            state = reason == null ? JdtlsState.READY : JdtlsState.FAILED;
            released.countDown();
        }

        @Override
        public JdtlsState state() {
            return state;
        }

        @Override
        public Optional<String> failure() {
            return state == JdtlsState.FAILED ? Optional.ofNullable(failure) : Optional.empty();
        }

        @Override
        public void awaitReady() throws InterruptedException {
            waiting.incrementAndGet();
            released.await();
            if (failure != null) {
                throw new JdtlsException("jdtls did not start: " + failure);
            }
        }

        @Override
        public List<Diagnostic> diagnostics(Path file) {
            return List.of(WARNING);
        }

        @Override
        public ChangeSet rename(Path file, Position position, String newName) {
            return RENAMED;
        }

        /** jdtls was ready and is dead now: nobody asked it to end. */
        void dies() {
            failure = "it was killed";
            state = JdtlsState.FAILED;
        }

        /** Closing this server fails with the exception. */
        void failsToClose(Exception failure) {
            closeFailure = failure;
        }

        @Override
        public void close() throws IOException, InterruptedException {
            closes.incrementAndGet();
            if (state == JdtlsState.STARTING) {
                finish("closed while it started");
            }
            switch (closeFailure) {
                case IOException failure -> throw failure;
                case InterruptedException failure -> throw failure;
                case null, default -> {}
            }
        }
    }

    private static void awaitUntil(java.util.function.BooleanSupplier condition, String what)
            throws InterruptedException {
        for (var attempt = 0; attempt < 1500 && !condition.getAsBoolean(); attempt++) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }

    @Nested
    class NotStarted {

        @Test
        void startsNothingBeforeARequestAndSaysWhere() {
            assertThat(project.root()).isEqualTo(ROOT);
            assertThat(project.state()).isEqualTo(JdtlsState.NOT_STARTED);
            assertThat(project.jdtlsFailure()).isEmpty();
            assertThat(starter.starts).hasValue(0);
        }

        @Test
        void closesWithoutStartingAnything() throws Exception {
            project.close();

            assertThat(starter.starts).hasValue(0);
        }
    }

    @Nested
    class Starting {

        @Test
        void theFirstRequestStartsJdtlsAndGetsItsAnswer() {
            assertThat(project.diagnostics(FILE)).containsExactly(ScriptedServer.WARNING);

            assertThat(starter.starts).hasValue(1);
            assertThat(project.state()).isEqualTo(JdtlsState.READY);
        }

        @Test
        void aRenameStartsItToo() {
            assertThat(project.rename(FILE, new Position(0, 0), "Other")).isEqualTo(ScriptedServer.RENAMED);

            assertThat(starter.starts).hasValue(1);
        }

        @Test
        void keepsItForTheNextRequests() {
            project.diagnostics(FILE);
            project.diagnostics(FILE);
            project.rename(FILE, new Position(0, 0), "Other");

            assertThat(starter.starts).hasValue(1);
        }

        @Test
        void startsItOnceWhateverTheNumberOfRequestsAtTheSameTime() throws Exception {
            var held = starter.willStart(ScriptedServer.held());
            var requests = new ArrayList<CompletableFuture<List<Diagnostic>>>();
            for (var request = 0; request < 20; request++) {
                requests.add(CompletableFuture.supplyAsync(() -> project.diagnostics(FILE), REQUESTS));
            }

            awaitUntil(() -> held.waiting.get() == 20, "the twenty requests to wait for the start");
            assertThat(project.state()).isEqualTo(JdtlsState.STARTING);
            assertThat(starter.starts).hasValue(1);
            held.finish(null);

            for (var request : requests) {
                assertThat(request.orTimeout(30, TimeUnit.SECONDS).join()).containsExactly(ScriptedServer.WARNING);
            }
            assertThat(starter.starts).hasValue(1);
            assertThat(project.state()).isEqualTo(JdtlsState.READY);
        }

        @Test
        void saysItIsStartingToWhoeverAsksWhileTheFirstRequestWaits() throws Exception {
            var held = starter.willStart(ScriptedServer.held());
            var request = CompletableFuture.supplyAsync(() -> project.diagnostics(FILE), REQUESTS);

            awaitUntil(() -> project.state() == JdtlsState.STARTING, "the state to be starting");
            assertThat(request).isNotDone();
            held.finish(null);

            assertThat(request.orTimeout(30, TimeUnit.SECONDS).join()).hasSize(1);
        }

        @Test
        void stopsWaitingWhenTheRequestIsInterrupted() throws Exception {
            var held = starter.willStart(ScriptedServer.held());
            var failure = new CompletableFuture<Throwable>();
            var interrupted = new CompletableFuture<Boolean>();
            var waiter = Thread.ofPlatform().start(() -> {
                try {
                    project.diagnostics(FILE);
                } catch (RuntimeException thrown) {
                    failure.complete(thrown);
                }
                interrupted.complete(Thread.currentThread().isInterrupted());
            });
            awaitUntil(() -> held.waiting.get() == 1, "the request to wait");

            waiter.interrupt();

            assertThat(failure.orTimeout(30, TimeUnit.SECONDS).join())
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("Interrupted while waiting for jdtls to start");
            assertThat(interrupted.join()).isTrue();
        }
    }

    @Nested
    class Failing {

        @Test
        void aStartThatFailsIsAProblemObjectThatSaysWhy() {
            starter.willStart(ScriptedServer.failing("Timed out after 300 s (jdtls is given 300 s)"));

            var failure = catchThrowableOfType(JdtlsStartException.class, () -> project.diagnostics(FILE));

            assertThat(failure.key().get()).isEqualTo("jdtls-start-failed");
            assertThat(failure.status()).isEqualTo(ErrorStatus.INTERNAL_SERVER_ERROR);
            assertThat(failure.getMessage())
                    .isEqualTo("jdtls did not start: Timed out after 300 s (jdtls is given 300 s)");
            assertThat(failure.parameters()).containsExactly(Map.entry("project", ROOT.toString()));
            assertThat(project.state()).isEqualTo(JdtlsState.FAILED);
            assertThat(project.jdtlsFailure()).contains("Timed out after 300 s (jdtls is given 300 s)");
        }

        @Test
        void theNextRequestStartsItAgain() {
            starter.willStart(ScriptedServer.failing("no launcher"));
            assertThatThrownBy(() -> project.diagnostics(FILE)).isInstanceOf(JdtlsStartException.class);

            assertThat(project.diagnostics(FILE)).containsExactly(ScriptedServer.WARNING);

            assertThat(starter.starts).hasValue(2);
            assertThat(project.state()).isEqualTo(JdtlsState.READY);
        }

        @Test
        void aJdtlsThatDiedWhileReadyIsFailedAndTheNextRequestEndsItAndStartsAnother() {
            var first = starter.willStart(new ScriptedServer());
            project.diagnostics(FILE);

            first.dies();

            assertThat(project.state()).isEqualTo(JdtlsState.FAILED);
            assertThat(project.jdtlsFailure()).contains("it was killed");
            assertThat(project.diagnostics(FILE)).containsExactly(ScriptedServer.WARNING);
            assertThat(starter.starts).hasValue(2);
            assertThat(first.closes).hasValue(1);
            assertThat(project.state()).isEqualTo(JdtlsState.READY);
            assertThat(log).containsExactly("jdtls failed: ending it and starting another");
        }

        @Test
        void startsAnotherJdtlsEvenWhenEndingTheDeadOneFails() {
            var first = starter.willStart(new ScriptedServer());
            project.diagnostics(FILE);
            first.dies();
            first.failsToClose(new IOException("cannot delete the data folder"));

            assertThat(project.diagnostics(FILE)).containsExactly(ScriptedServer.WARNING);

            assertThat(starter.starts).hasValue(2);
            assertThat(log)
                    .contains("could not end the jdtls that failed: java.io.IOException: "
                            + "cannot delete the data folder");
        }

        @Test
        void startsAnotherJdtlsEvenWhenEndingTheDeadOneIsInterruptedAndKeepsTheInterruption() {
            var first = starter.willStart(new ScriptedServer());
            project.diagnostics(FILE);
            first.dies();
            first.failsToClose(new InterruptedException("stop"));

            try {
                // The other jdtls is started, then the request that was interrupted stops waiting for it.
                assertThatThrownBy(() -> project.diagnostics(FILE))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("Interrupted while waiting for jdtls to start");
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                Thread.interrupted();
            }
            assertThat(starter.starts).hasValue(2);
        }

        @Test
        void everyRequestThatWaitedForTheStartGetsItsFailure() throws Exception {
            var held = starter.willStart(ScriptedServer.held());
            var requests = new ArrayList<CompletableFuture<List<Diagnostic>>>();
            for (var request = 0; request < 5; request++) {
                requests.add(CompletableFuture.supplyAsync(() -> project.diagnostics(FILE), REQUESTS));
            }
            awaitUntil(() -> held.waiting.get() == 5, "the five requests to wait for the start");

            held.finish("jdtls exited with code 3");

            for (var request : requests) {
                assertThat(request.orTimeout(30, TimeUnit.SECONDS))
                        .failsWithin(LONG)
                        .withThrowableThat()
                        .withCauseInstanceOf(JdtlsStartException.class);
            }
            assertThat(starter.starts).hasValue(1);
        }
    }

    @Nested
    class Closing {

        @Test
        void endsTheServerThatIsReadyOnce() throws Exception {
            project.diagnostics(FILE);

            project.close();
            project.close();

            assertThat(starter.servers.getFirst().closes).hasValue(1);
        }

        @Test
        void endsTheStartInProgressAndTheRequestsThatWaitForIt() throws Exception {
            var held = starter.willStart(ScriptedServer.held());
            var request = CompletableFuture.supplyAsync(() -> project.diagnostics(FILE), REQUESTS);
            awaitUntil(() -> held.waiting.get() == 1, "the request to wait for the start");

            project.close();

            assertThat(held.closes).hasValue(1);
            assertThat(request).failsWithin(LONG).withThrowableThat().withCauseInstanceOf(JdtlsStartException.class);
        }

        @Test
        void startsNothingOnceClosed() throws Exception {
            project.close();

            assertThatThrownBy(() -> project.diagnostics(FILE))
                    .isInstanceOfSatisfying(
                            LanguageServerException.class,
                            refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
            assertThat(starter.starts).hasValue(0);
        }
    }

    @Nested
    class OfJdtls {

        @TempDir
        Path directory;

        private ProjectServer served(Map<String, String> environment) {
            return ProjectServer.jdtls(directory, environment, ignored -> {});
        }

        @Test
        void saysTheLauncherIsMissingWhenTheEnvironmentDoesNotGiveIt() {
            var served = served(Map.of());

            assertThat(served.state()).isEqualTo(JdtlsState.NOT_STARTED);
            assertThatThrownBy(() -> served.diagnostics(directory.resolve("A.java")))
                    .isInstanceOf(JdtlsStartException.class)
                    .hasMessageContaining("jdtls did not start")
                    .hasMessageContaining("GABIER_JDTLS is not set");
            assertThat(served.state()).isEqualTo(JdtlsState.FAILED);
        }

        @Test
        void saysTheLauncherIsMissingWhenTheVariableIsBlank() {
            var served = served(Map.of("GABIER_JDTLS", " "));

            assertThatThrownBy(() -> served.diagnostics(directory.resolve("A.java")))
                    .hasMessageContaining("GABIER_JDTLS is not set");
        }

        @Test
        void saysTheLauncherCannotBeRunWhenItIsNotThere() {
            var served = served(
                    Map.of("GABIER_JDTLS", directory.resolve("no-such-jdtls").toString()));

            assertThatThrownBy(() -> served.diagnostics(directory.resolve("A.java")))
                    .isInstanceOf(JdtlsStartException.class)
                    .hasMessageContaining("no-such-jdtls");
        }

        @Test
        void saysWhyALauncherThatDiesDoesNotStart() throws IOException {
            var launcher = Files.writeString(directory.resolve("jdtls"), """
                #!/bin/sh
                exit 3
                """);
            Files.setPosixFilePermissions(launcher, PosixFilePermissions.fromString("rwx------"));
            var served = served(Map.of("GABIER_JDTLS", launcher.toString()));

            assertThatThrownBy(() -> served.diagnostics(directory.resolve("A.java")))
                    .isInstanceOf(JdtlsStartException.class)
                    .hasMessageContaining("jdtls did not start")
                    .hasMessageContaining("jdtls exited with code 3");
        }
    }
}
