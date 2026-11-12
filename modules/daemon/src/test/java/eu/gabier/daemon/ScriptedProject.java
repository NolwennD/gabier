package eu.gabier.daemon;

import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.CleanUp;
import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.editing.domain.JavaLanguageServer;
import eu.gabier.editing.domain.OfferedAction;
import eu.gabier.editing.domain.OfferedActions;
import eu.gabier.editing.domain.PackageName;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.Range;
import eu.gabier.editing.domain.ReferenceLocations;
import eu.gabier.editing.domain.TypeKind;
import eu.gabier.protocol.JdtlsProgress;
import eu.gabier.protocol.JdtlsState;
import eu.gabier.testing.domain.BuiltProject;
import eu.gabier.testing.domain.ChangedBuild;
import eu.gabier.testing.domain.ProjectBuild;
import eu.gabier.testing.fixtures.FakeProjectBuild;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * The project of a daemon under test: a language server given by the test, a state and a reason the test sets, a count
 * of closes, and requests that can be made to wait.
 */
final class ScriptedProject implements ServedProject {

    /** What a {@code diagnostics} request does. */
    private enum Requests {
        ARE_ANSWERED,
        WAIT_FOR_THE_CLOSE,
        NEVER_END,
        BREAK
    }

    private final Path root;
    private final JavaLanguageServer server;
    private final ScriptedDisk disk = new ScriptedDisk();
    private volatile Supplier<List<DiagnosedFile>> problems;
    private final AtomicInteger closes = new AtomicInteger();
    private final AtomicInteger warmUps = new AtomicInteger();
    private final CountDownLatch waiting = new CountDownLatch(1);
    private final CountDownLatch closed = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);
    private volatile JdtlsState state = JdtlsState.READY;
    private volatile Optional<String> failure = Optional.empty();
    private volatile Optional<JdtlsProgress> progress = Optional.empty();
    private volatile Requests requests = Requests.ARE_ANSWERED;
    private volatile boolean interrupted;
    private volatile ProjectBuild build;

    ScriptedProject(Path root, JavaLanguageServer server) {
        this.root = root;
        this.build = new FakeProjectBuild(root);
        this.server = server;
        this.problems = () -> this.server.projectDiagnostics();
    }

    /**
     * The problems of the whole project are what the test gives, read each time the daemon asks: the state of the
     * project as an edit on disk changes it.
     */
    ScriptedProject whoseProblemsAre(Supplier<List<DiagnosedFile>> problems) {
        this.problems = problems;

        return this;
    }

    /** The build the project reports: what the test hands it, as the build of the language server would. */
    ScriptedProject building(ProjectBuild build) {
        this.build = build;

        return this;
    }

    /** The disk the project follows: what the test says of it is what a client that watches is told. */
    ScriptedDisk disk() {
        return disk;
    }

    ScriptedProject in(JdtlsState state) {
        this.state = state;

        return this;
    }

    /** What jdtls is doing, as the project says it. */
    ScriptedProject doing(JdtlsProgress progress) {
        this.progress = Optional.of(progress);

        return this;
    }

    /** The reason jdtls is in its state, as the project says it. */
    ScriptedProject because(String failure) {
        this.failure = Optional.of(failure);

        return this;
    }

    /**
     * A {@code diagnostics} request waits until the project is closed, then fails as the request that waits for jdtls
     * does when the daemon stops.
     */
    ScriptedProject whoseRequestsWaitForTheClose() {
        requests = Requests.WAIT_FOR_THE_CLOSE;

        return this;
    }

    /** A {@code diagnostics} request waits until {@link #release}, whatever the close. */
    ScriptedProject whoseRequestsNeverEnd() {
        requests = Requests.NEVER_END;

        return this;
    }

    /** A {@code diagnostics} request fails with an exception that no refusal of gabier is. */
    ScriptedProject whoseRequestsBreak() {
        requests = Requests.BREAK;

        return this;
    }

    /** True once a {@code diagnostics} request is waiting, within ten seconds. */
    boolean awaitRequest() throws InterruptedException {
        return waiting.await(10, TimeUnit.SECONDS);
    }

    /** Lets the requests that never end go. */
    void release() {
        released.countDown();
    }

    /** Closing it is interrupted, as a thread that is told to stop while it waits for the end of jdtls is. */
    void interruptedWhenClosed() {
        interrupted = true;
    }

    int closes() {
        return closes.get();
    }

    @Override
    public Path root() {
        return root;
    }

    @Override
    public JdtlsState state() {
        return state;
    }

    @Override
    public Optional<JdtlsProgress> jdtlsProgress() {
        return progress;
    }

    @Override
    public Optional<String> jdtlsFailure() {
        return failure;
    }

    @Override
    public DiskChanges watchDisk() {
        return disk;
    }

    @Override
    public void warmUp() {
        warmUps.incrementAndGet();
        if (state == JdtlsState.NOT_STARTED) {
            state = JdtlsState.STARTING;
        }
    }

    /** How many times the daemon asked the project to warm up; a project that was not started is then starting. */
    int warmUps() {
        return warmUps.get();
    }

    @Override
    public List<List<Diagnostic>> diagnostics(List<Path> files) {
        try {
            switch (requests) {
                case WAIT_FOR_THE_CLOSE -> {
                    waiting.countDown();
                    closed.await();
                    throw JdtlsStartException.of(
                            root, new JdtlsException("jdtls did not start: the daemon is stopping"));
                }
                case NEVER_END -> {
                    waiting.countDown();
                    released.await();
                }
                case BREAK -> throw new JdtlsException("jdtls broke while it was asked for the diagnostics");
                default -> {}
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            throw new JdtlsException("interrupted", stopped);
        }
        return server.diagnostics(files);
    }

    @Override
    public List<DiagnosedFile> projectDiagnostics() {
        return problems.get();
    }

    @Override
    public BuiltProject built() {
        return build.built();
    }

    @Override
    public ChangedBuild builtWithChanges() {
        return build.builtWithChanges();
    }

    @Override
    public ChangeSet rename(Path file, Position position, String newName) {
        return server.rename(file, position, newName);
    }

    @Override
    public ChangeSet extractVariable(Path file, Range selection, String name) {
        return server.extractVariable(file, selection, name);
    }

    @Override
    public ChangeSet extractConstant(Path file, Range selection, String name) {
        return server.extractConstant(file, selection, name);
    }

    @Override
    public ChangeSet extractField(Path file, Range selection, String name) {
        return server.extractField(file, selection, name);
    }

    @Override
    public ChangeSet extractMethod(Path file, Range selection, String name) {
        return server.extractMethod(file, selection, name);
    }

    @Override
    public ChangeSet moveTypeToFile(Path file, Position position) {
        return server.moveTypeToFile(file, position);
    }

    @Override
    public ChangeSet moveToPackage(List<Path> files, PackageName target) {
        return server.moveToPackage(files, target);
    }

    @Override
    public ChangeSet inline(Path file, Position position) {
        return server.inline(file, position);
    }

    @Override
    public ChangeSet createType(Path file, TypeKind kind) {
        return server.createType(file, kind);
    }

    @Override
    public ChangeSet createPackageInfo(Path file) {
        return server.createPackageInfo(file);
    }

    @Override
    public ReferenceLocations references(Path file, Position position, boolean includeDeclaration) {
        return server.references(file, position, includeDeclaration);
    }

    @Override
    public OfferedActions codeActions(Path file, Range range) {
        return server.codeActions(file, range);
    }

    @Override
    public ChangeSet codeActionEdit(Path file, Range range, OfferedAction action) {
        return server.codeActionEdit(file, range, action);
    }

    @Override
    public Optional<ChangeSet> organizeImports(Path file) {
        return server.organizeImports(file);
    }

    @Override
    public Optional<ChangeSet> cleanUp(Path file, List<CleanUp> cleanUps) {
        return server.cleanUp(file, cleanUps);
    }

    @Override
    public void close() throws InterruptedException {
        closes.incrementAndGet();
        closed.countDown();
        if (interrupted) {
            throw new InterruptedException("told to stop while jdtls ends");
        }
    }
}
