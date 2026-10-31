package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.Diagnostic;
import fr.craft.gabier.editing.domain.JavaLanguageServer;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.editing.domain.Range;
import fr.craft.gabier.protocol.JdtlsState;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The project of a daemon under test: a language server given by the test, a state and a reason the test sets, a count
 * of closes, and requests that can be made to wait.
 */
final class ScriptedProject implements ServedProject {

    /** What a {@code diagnostics} request does. */
    private enum Requests {
        ARE_ANSWERED,
        WAIT_FOR_THE_CLOSE,
        NEVER_END
    }

    private final Path root;
    private final JavaLanguageServer server;
    private final AtomicInteger closes = new AtomicInteger();
    private final CountDownLatch waiting = new CountDownLatch(1);
    private final CountDownLatch closed = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);
    private volatile JdtlsState state = JdtlsState.READY;
    private volatile Optional<String> failure = Optional.empty();
    private volatile Requests requests = Requests.ARE_ANSWERED;
    private volatile boolean interrupted;

    ScriptedProject(Path root, JavaLanguageServer server) {
        this.root = root;
        this.server = server;
    }

    ScriptedProject in(JdtlsState state) {
        this.state = state;

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
    public Optional<String> jdtlsFailure() {
        return failure;
    }

    @Override
    public List<Diagnostic> diagnostics(Path file) {
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
                default -> {}
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            throw new JdtlsException("interrupted", stopped);
        }
        return server.diagnostics(file);
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
    public ChangeSet extractMethod(Path file, Range selection, String name) {
        return server.extractMethod(file, selection, name);
    }

    @Override
    public Optional<ChangeSet> organizeImports(Path file) {
        return server.organizeImports(file);
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
