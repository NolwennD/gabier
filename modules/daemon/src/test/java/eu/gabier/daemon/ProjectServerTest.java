package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.CleanUp;
import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.editing.domain.FileChange;
import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.OfferedAction;
import eu.gabier.editing.domain.OfferedActions;
import eu.gabier.editing.domain.PackageName;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.Range;
import eu.gabier.editing.domain.ReferenceLocations;
import eu.gabier.editing.domain.TypeKind;
import eu.gabier.protocol.JdtlsProgress;
import eu.gabier.protocol.JdtlsState;
import eu.gabier.shared.error.domain.ErrorStatus;
import eu.gabier.testing.domain.BuiltProject;
import eu.gabier.testing.domain.ClassFolder;
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
        static final BuiltProject BUILT = new BuiltProject(
                List.of(new ClassFolder(Path.of("/work/demo/src"), Path.of("/work/demo/bin"))), List.of(), List.of());
        static final ReferenceLocations NO_REFERENCES = new ReferenceLocations(List.of(), List.of());
        static final OfferedActions OFFERED = new OfferedActions(List.of(new OfferedAction("quickfix", "Fix it")));
        static final Diagnostic WARNING = new Diagnostic(Diagnostic.Severity.WARNING, new Position(1, 2), "unused");

        private final CountDownLatch released = new CountDownLatch(1);
        private final AtomicInteger waiting = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();
        private volatile JdtlsState state = JdtlsState.STARTING;
        private volatile String failure;
        private volatile JdtlsProgress progress;
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

        /** What the server says it is doing. */
        void doing(JdtlsProgress progress) {
            this.progress = progress;
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
        public Optional<JdtlsProgress> progress() {
            return Optional.ofNullable(progress);
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
        public List<List<Diagnostic>> diagnostics(List<Path> files) {
            return files.stream().map(file -> List.of(WARNING)).toList();
        }

        @Override
        public List<DiagnosedFile> projectDiagnostics() {
            return List.of(new DiagnosedFile(FILE, List.of(WARNING)));
        }

        @Override
        public BuiltProject built() {
            return BUILT;
        }

        @Override
        public ChangeSet rename(Path file, Position position, String newName) {
            return RENAMED;
        }

        @Override
        public ChangeSet extractVariable(Path file, Range selection, String name) {
            return RENAMED;
        }

        @Override
        public ChangeSet extractConstant(Path file, Range selection, String name) {
            return RENAMED;
        }

        @Override
        public ChangeSet extractField(Path file, Range selection, String name) {
            return RENAMED;
        }

        @Override
        public ChangeSet extractMethod(Path file, Range selection, String name) {
            return RENAMED;
        }

        @Override
        public ChangeSet moveTypeToFile(Path file, Position position) {
            return RENAMED;
        }

        @Override
        public ChangeSet moveToPackage(List<Path> files, PackageName target) {
            return RENAMED;
        }

        @Override
        public ChangeSet inline(Path file, Position position) {
            return RENAMED;
        }

        @Override
        public ChangeSet createType(Path file, TypeKind kind) {
            return RENAMED;
        }

        @Override
        public ChangeSet createPackageInfo(Path file) {
            return RENAMED;
        }

        @Override
        public ReferenceLocations references(Path file, Position position, boolean includeDeclaration) {
            return NO_REFERENCES;
        }

        @Override
        public OfferedActions codeActions(Path file, Range range) {
            return OFFERED;
        }

        @Override
        public ChangeSet codeActionEdit(Path file, Range range, OfferedAction action) {
            return RENAMED;
        }

        @Override
        public Optional<ChangeSet> organizeImports(Path file) {
            return Optional.of(RENAMED);
        }

        @Override
        public Optional<ChangeSet> cleanUp(Path file, List<CleanUp> cleanUps) {
            return Optional.of(RENAMED);
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
            assertThat(project.jdtlsProgress()).isEmpty();
            assertThat(starter.starts).hasValue(0);
        }

        @Test
        void closesWithoutStartingAnything() throws Exception {
            project.close();

            assertThat(starter.starts).hasValue(0);
        }
    }

    @Nested
    class WarmingUp {

        @Test
        void startsJdtlsWithoutWaitingForItAndWithoutARequest() {
            var held = starter.willStart(ScriptedServer.held());

            project.warmUp();

            assertThat(starter.starts).hasValue(1);
            assertThat(project.state()).isEqualTo(JdtlsState.STARTING);
            assertThat(held.waiting).hasValue(0);
        }

        @Test
        void startsNothingWhileJdtlsIsStartingNorOnceItIsReady() {
            var held = starter.willStart(ScriptedServer.held());
            project.warmUp();
            project.warmUp();

            held.finish(null);
            project.warmUp();
            project.diagnostics(List.of(FILE));

            assertThat(starter.starts).hasValue(1);
            assertThat(project.state()).isEqualTo(JdtlsState.READY);
        }

        @Test
        void startsJdtlsAgainWhenTheLastStartFailed() {
            var first = starter.willStart(ScriptedServer.failing("no launcher"));

            project.warmUp();
            assertThat(project.state()).isEqualTo(JdtlsState.FAILED);
            project.warmUp();

            assertThat(starter.starts).hasValue(2);
            assertThat(first.closes).hasValue(1);
            assertThat(project.state()).isEqualTo(JdtlsState.READY);
        }

        @Test
        void isRefusedOnceTheProjectIsClosed() throws Exception {
            project.close();

            assertThatThrownBy(project::warmUp)
                    .isInstanceOfSatisfying(
                            LanguageServerException.class,
                            refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
            assertThat(starter.starts).hasValue(0);
        }
    }

    @Nested
    class Starting {

        @Test
        void theFirstRequestStartsJdtlsAndGetsItsAnswer() {
            assertThat(project.diagnostics(List.of(FILE))).containsExactly(List.of(ScriptedServer.WARNING));

            assertThat(starter.starts).hasValue(1);
            assertThat(project.state()).isEqualTo(JdtlsState.READY);
        }

        @Test
        void theDiagnosticsOfTheWholeProjectStartItToo() {
            assertThat(project.projectDiagnostics())
                    .containsExactly(new DiagnosedFile(FILE, List.of(ScriptedServer.WARNING)));

            assertThat(starter.starts).hasValue(1);
        }

        @Test
        void theBuildStartsItToo() {
            assertThat(project.built()).isEqualTo(ScriptedServer.BUILT);

            assertThat(starter.starts).hasValue(1);
        }

        @Test
        void aRenameStartsItToo() {
            assertThat(project.rename(FILE, new Position(0, 0), "Other")).isEqualTo(ScriptedServer.RENAMED);

            assertThat(starter.starts).hasValue(1);
        }

        @Test
        void anOrganizationOfTheImportsStartsItToo() {
            assertThat(project.organizeImports(FILE)).contains(ScriptedServer.RENAMED);

            assertThat(starter.starts).hasValue(1);
        }

        @Test
        void aCleanUpStartsItToo() {
            assertThat(project.cleanUp(FILE, List.of(CleanUp.ADD_OVERRIDE))).contains(ScriptedServer.RENAMED);

            assertThat(starter.starts).hasValue(1);
        }

        @Test
        void anExtractionOfAVariableStartsItToo() {
            assertThat(project.extractVariable(FILE, new Range(new Position(0, 0), new Position(0, 1)), "x"))
                    .isEqualTo(ScriptedServer.RENAMED);

            assertThat(starter.starts).hasValue(1);
        }

        @Test
        void anExtractionOfAConstantStartsItToo() {
            assertThat(project.extractConstant(FILE, new Range(new Position(0, 0), new Position(0, 1)), "X"))
                    .isEqualTo(ScriptedServer.RENAMED);

            assertThat(starter.starts).hasValue(1);
        }

        @Test
        void anExtractionOfAFieldStartsItToo() {
            assertThat(project.extractField(FILE, new Range(new Position(0, 0), new Position(0, 1)), "x"))
                    .isEqualTo(ScriptedServer.RENAMED);

            assertThat(starter.starts).hasValue(1);
        }

        @Test
        void anExtractionOfAMethodStartsItToo() {
            assertThat(project.extractMethod(FILE, new Range(new Position(0, 0), new Position(0, 1)), "x"))
                    .isEqualTo(ScriptedServer.RENAMED);

            assertThat(starter.starts).hasValue(1);
        }

        @Test
        void aMoveOfATypeStartsItToo() {
            assertThat(project.moveTypeToFile(FILE, new Position(0, 0))).isEqualTo(ScriptedServer.RENAMED);

            assertThat(starter.starts).hasValue(1);
        }

        @Test
        void aMoveToAPackageStartsItToo() {
            assertThat(project.moveToPackage(List.of(FILE), PackageName.of("demo")))
                    .isEqualTo(ScriptedServer.RENAMED);

            assertThat(starter.starts).hasValue(1);
        }

        @Test
        void aCreationOfATypeStartsItToo() {
            assertThat(project.createType(FILE, TypeKind.CLASS)).isEqualTo(ScriptedServer.RENAMED);

            assertThat(starter.starts).hasValue(1);
        }

        @Test
        void aCreationOfAPackageInfoStartsItToo() {
            assertThat(project.createPackageInfo(FILE)).isEqualTo(ScriptedServer.RENAMED);

            assertThat(starter.starts).hasValue(1);
        }

        @Test
        void aSearchForReferencesStartsItToo() {
            assertThat(project.references(FILE, new Position(0, 0), true)).isEqualTo(ScriptedServer.NO_REFERENCES);

            assertThat(starter.starts).hasValue(1);
        }

        @Test
        void aListOfCodeActionsStartsItToo() {
            var range = new Range(new Position(0, 0), new Position(0, 1));

            assertThat(project.codeActions(FILE, range)).isEqualTo(ScriptedServer.OFFERED);

            assertThat(starter.starts).hasValue(1);
        }

        @Test
        void theEditOfACodeActionStartsItToo() {
            var range = new Range(new Position(0, 0), new Position(0, 1));

            assertThat(project.codeActionEdit(FILE, range, new OfferedAction("quickfix", "Fix it")))
                    .isEqualTo(ScriptedServer.RENAMED);

            assertThat(starter.starts).hasValue(1);
        }

        @Test
        void anInlineStartsItToo() {
            assertThat(project.inline(FILE, new Position(0, 0))).isEqualTo(ScriptedServer.RENAMED);

            assertThat(starter.starts).hasValue(1);
        }

        @Test
        void keepsItForTheNextRequests() {
            project.diagnostics(List.of(FILE));
            project.diagnostics(List.of(FILE));
            project.projectDiagnostics();
            project.built();
            project.rename(FILE, new Position(0, 0), "Other");
            project.organizeImports(FILE);
            project.cleanUp(FILE, List.of(CleanUp.ADD_OVERRIDE));
            project.extractVariable(FILE, new Range(new Position(0, 0), new Position(0, 1)), "x");
            project.extractConstant(FILE, new Range(new Position(0, 0), new Position(0, 1)), "X");
            project.extractField(FILE, new Range(new Position(0, 0), new Position(0, 1)), "x");
            project.extractMethod(FILE, new Range(new Position(0, 0), new Position(0, 1)), "x");
            project.moveTypeToFile(FILE, new Position(0, 0));
            project.moveToPackage(List.of(FILE), PackageName.of("demo"));
            project.inline(FILE, new Position(0, 0));
            project.createType(FILE, TypeKind.RECORD);
            project.createPackageInfo(FILE);
            project.references(FILE, new Position(0, 0), false);

            assertThat(starter.starts).hasValue(1);
        }

        @Test
        void saysWhatTheServerThatIsStartingIsDoing() throws Exception {
            var held = starter.willStart(ScriptedServer.held());
            var doing = new JdtlsProgress(JdtlsProgress.Phase.IMPORTING, "Build", Duration.ofSeconds(4));
            held.doing(doing);
            var request = CompletableFuture.supplyAsync(() -> project.diagnostics(List.of(FILE)), REQUESTS);
            awaitUntil(() -> held.waiting.get() == 1, "the request to wait for the start");

            assertThat(project.jdtlsProgress()).contains(doing);
            held.finish(null);
            request.orTimeout(30, TimeUnit.SECONDS).join();
        }

        @Test
        void startsItOnceWhateverTheNumberOfRequestsAtTheSameTime() throws Exception {
            var held = starter.willStart(ScriptedServer.held());
            var requests = new ArrayList<CompletableFuture<List<List<Diagnostic>>>>();
            for (var request = 0; request < 20; request++) {
                requests.add(CompletableFuture.supplyAsync(() -> project.diagnostics(List.of(FILE)), REQUESTS));
            }

            awaitUntil(() -> held.waiting.get() == 20, "the twenty requests to wait for the start");
            assertThat(project.state()).isEqualTo(JdtlsState.STARTING);
            assertThat(starter.starts).hasValue(1);
            held.finish(null);

            for (var request : requests) {
                assertThat(request.orTimeout(30, TimeUnit.SECONDS).join())
                        .containsExactly(List.of(ScriptedServer.WARNING));
            }
            assertThat(starter.starts).hasValue(1);
            assertThat(project.state()).isEqualTo(JdtlsState.READY);
        }

        @Test
        void saysItIsStartingToWhoeverAsksWhileTheFirstRequestWaits() throws Exception {
            var held = starter.willStart(ScriptedServer.held());
            var request = CompletableFuture.supplyAsync(() -> project.diagnostics(List.of(FILE)), REQUESTS);

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
                    project.diagnostics(List.of(FILE));
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

            var failure = catchThrowableOfType(JdtlsStartException.class, () -> project.diagnostics(List.of(FILE)));

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
            assertThatThrownBy(() -> project.diagnostics(List.of(FILE))).isInstanceOf(JdtlsStartException.class);

            assertThat(project.diagnostics(List.of(FILE))).containsExactly(List.of(ScriptedServer.WARNING));

            assertThat(starter.starts).hasValue(2);
            assertThat(project.state()).isEqualTo(JdtlsState.READY);
        }

        @Test
        void aJdtlsThatDiedWhileReadyIsFailedAndTheNextRequestEndsItAndStartsAnother() {
            var first = starter.willStart(new ScriptedServer());
            project.diagnostics(List.of(FILE));

            first.dies();

            assertThat(project.state()).isEqualTo(JdtlsState.FAILED);
            assertThat(project.jdtlsFailure()).contains("it was killed");
            assertThat(project.diagnostics(List.of(FILE))).containsExactly(List.of(ScriptedServer.WARNING));
            assertThat(starter.starts).hasValue(2);
            assertThat(first.closes).hasValue(1);
            assertThat(project.state()).isEqualTo(JdtlsState.READY);
            assertThat(log).containsExactly("jdtls failed: ending it and starting another");
        }

        @Test
        void startsAnotherJdtlsEvenWhenEndingTheDeadOneFails() {
            var first = starter.willStart(new ScriptedServer());
            project.diagnostics(List.of(FILE));
            first.dies();
            first.failsToClose(new IOException("cannot delete the data folder"));

            assertThat(project.diagnostics(List.of(FILE))).containsExactly(List.of(ScriptedServer.WARNING));

            assertThat(starter.starts).hasValue(2);
            assertThat(log)
                    .contains("could not end the jdtls that failed: java.io.IOException: "
                            + "cannot delete the data folder");
        }

        @Test
        void startsAnotherJdtlsEvenWhenEndingTheDeadOneIsInterruptedAndKeepsTheInterruption() {
            var first = starter.willStart(new ScriptedServer());
            project.diagnostics(List.of(FILE));
            first.dies();
            first.failsToClose(new InterruptedException("stop"));

            try {
                // The other jdtls is started, then the request that was interrupted stops waiting for it.
                assertThatThrownBy(() -> project.diagnostics(List.of(FILE)))
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
            var requests = new ArrayList<CompletableFuture<List<List<Diagnostic>>>>();
            for (var request = 0; request < 5; request++) {
                requests.add(CompletableFuture.supplyAsync(() -> project.diagnostics(List.of(FILE)), REQUESTS));
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
            project.diagnostics(List.of(FILE));

            project.close();
            project.close();

            assertThat(starter.servers.getFirst().closes).hasValue(1);
        }

        @Test
        void endsTheStartInProgressAndTheRequestsThatWaitForIt() throws Exception {
            var held = starter.willStart(ScriptedServer.held());
            var request = CompletableFuture.supplyAsync(() -> project.diagnostics(List.of(FILE)), REQUESTS);
            awaitUntil(() -> held.waiting.get() == 1, "the request to wait for the start");

            project.close();

            assertThat(held.closes).hasValue(1);
            assertThat(request).failsWithin(LONG).withThrowableThat().withCauseInstanceOf(JdtlsStartException.class);
        }

        @Test
        void startsNothingOnceClosed() throws Exception {
            project.close();

            assertThatThrownBy(() -> project.diagnostics(List.of(FILE)))
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

        private final List<String> logged = new CopyOnWriteArrayList<>();

        private ProjectServer served(Map<String, String> environment) {
            return ProjectServer.jdtls(directory, environment, directory.resolve("home"), logged::add);
        }

        @Test
        void saysTheLauncherIsMissingWhenTheEnvironmentDoesNotGiveIt() {
            var served = served(Map.of());

            assertThat(served.state()).isEqualTo(JdtlsState.NOT_STARTED);
            assertThatThrownBy(() -> served.diagnostics(List.of(directory.resolve("A.java"))))
                    .isInstanceOf(JdtlsStartException.class)
                    .hasMessageContaining("jdtls did not start")
                    .hasMessageContaining("no jdtls found");
            assertThat(served.state()).isEqualTo(JdtlsState.FAILED);
        }

        @Test
        void saysTheLauncherIsMissingWhenTheVariableIsBlank() {
            var served = served(Map.of("GABIER_JDTLS", " "));

            assertThatThrownBy(() -> served.diagnostics(List.of(directory.resolve("A.java"))))
                    .hasMessageContaining("no jdtls found");
        }

        @Test
        void saysTheLauncherCannotBeRunWhenItIsNotThere() {
            var served = served(
                    Map.of("GABIER_JDTLS", directory.resolve("no-such-jdtls").toString()));

            assertThatThrownBy(() -> served.diagnostics(List.of(directory.resolve("A.java"))))
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

            assertThatThrownBy(() -> served.diagnostics(List.of(directory.resolve("A.java"))))
                    .isInstanceOf(JdtlsStartException.class)
                    .hasMessageContaining("jdtls did not start")
                    .hasMessageContaining("jdtls exited with code 3");
        }

        /** A launcher in a distribution of jdtls (it has a version) that writes its arguments to a file and exits. */
        private Path recordingLauncher(Path arguments) throws IOException {
            Files.createDirectories(directory.resolve("distribution/bin"));
            Files.createDirectories(directory.resolve("distribution/plugins"));
            Files.createFile(directory.resolve("distribution/plugins/org.eclipse.jdt.ls.core_1.63.0.202610191322.jar"));
            var launcher = Files.writeString(directory.resolve("distribution/bin/jdtls"), """
                #!/bin/sh
                echo "$@" > %s
                exit 3
                """.formatted(arguments));
            Files.setPosixFilePermissions(launcher, PosixFilePermissions.fromString("rwx------"));
            return launcher;
        }

        @Test
        void startsJdtlsOnATemporaryDataFolderByDefault() throws IOException {
            var arguments = directory.resolve("arguments");
            var served =
                    served(Map.of("GABIER_JDTLS", recordingLauncher(arguments).toString()));

            assertThatThrownBy(() -> served.diagnostics(List.of(directory.resolve("A.java"))))
                    .isInstanceOf(JdtlsStartException.class);

            assertThat(Files.readString(arguments))
                    .contains("-data " + Path.of(System.getProperty("java.io.tmpdir")) + "/gabier-jdtls-data-");
        }

        @Test
        void startsJdtlsWithoutSharedIndexesWhenItsDistributionTellsNoVersion() throws IOException {
            var arguments = directory.resolve("arguments");
            var launcher = Files.writeString(directory.resolve("jdtls"), """
                #!/bin/sh
                echo "$@" > %s
                exit 3
                """.formatted(arguments));
            Files.setPosixFilePermissions(launcher, PosixFilePermissions.fromString("rwx------"));
            var served = served(Map.of("GABIER_JDTLS", launcher.toString()));

            assertThatThrownBy(() -> served.diagnostics(List.of(directory.resolve("A.java"))))
                    .isInstanceOf(JdtlsStartException.class);

            assertThat(Files.readString(arguments)).doesNotContain("sharedIndexLocation");
            assertThat(logged)
                    .contains("the indexes of the libraries are not shared: " + launcher
                            + " is not in a distribution of jdtls that tells its version");
        }

        @Test
        void startsJdtlsSharingTheIndexesOfItsLibrariesInTheCacheFolder() throws IOException {
            var arguments = directory.resolve("arguments");
            var cache = directory.resolve("cache");
            var served = served(
                    Map.of("GABIER_JDTLS", recordingLauncher(arguments).toString(), "GABIER_CACHE", cache.toString()));

            assertThatThrownBy(() -> served.diagnostics(List.of(directory.resolve("A.java"))))
                    .isInstanceOf(JdtlsStartException.class);

            assertThat(Files.readString(arguments))
                    .contains(" --jvm-arg=-Djdt.core.sharedIndexLocation=" + cache
                            + "/jdtls-1.63.0.202610191322/indexes");
        }

        /** The pinned jdtls as {@code mise run jdtls:install} leaves it under the home folder, writing its arguments. */
        private Path installedLauncher(Path arguments) throws IOException {
            var distribution = directory.resolve("home/.local/share/jdtls").resolve(JdtlsLocation.PINNED_VERSION);
            Files.createDirectories(distribution.resolve("bin"));
            var launcher = Files.writeString(distribution.resolve("bin/jdtls"), """
                #!/bin/sh
                echo "$@" > %s
                exit 3
                """.formatted(arguments));
            Files.setPosixFilePermissions(launcher, PosixFilePermissions.fromString("rwx------"));
            return launcher;
        }

        @Test
        void startsThePinnedJdtlsInstalledUnderTheHomeFolderWhenTheVariableIsNotSet() throws IOException {
            var arguments = directory.resolve("arguments");
            installedLauncher(arguments);
            var served = served(Map.of());

            assertThatThrownBy(() -> served.diagnostics(List.of(directory.resolve("A.java"))))
                    .isInstanceOf(JdtlsStartException.class)
                    .hasMessageContaining("jdtls exited with code 3");

            assertThat(Files.readString(arguments)).contains("-data ");
        }

        @Test
        void startsTheLauncherOfTheVariableWhateverIsInstalled() throws IOException {
            var installedArguments = directory.resolve("installed-arguments");
            installedLauncher(installedArguments);
            var chosenArguments = directory.resolve("chosen-arguments");
            var served = served(
                    Map.of("GABIER_JDTLS", recordingLauncher(chosenArguments).toString()));

            assertThatThrownBy(() -> served.diagnostics(List.of(directory.resolve("A.java"))))
                    .isInstanceOf(JdtlsStartException.class);

            assertThat(chosenArguments).exists();
            assertThat(installedArguments).doesNotExist();
        }

        @Test
        void findsAJdtlsInstalledAfterTheServerWasMadeAtTheNextRequest() throws IOException {
            var arguments = directory.resolve("arguments");
            var served = served(Map.of());
            assertThatThrownBy(() -> served.diagnostics(List.of(directory.resolve("A.java"))))
                    .hasMessageContaining("no jdtls found");

            installedLauncher(arguments);
            assertThatThrownBy(() -> served.diagnostics(List.of(directory.resolve("A.java"))))
                    .hasMessageContaining("jdtls exited with code 3");

            assertThat(arguments).exists();
        }
    }
}
