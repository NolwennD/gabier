package eu.gabier.cli.infrastructure.primary.mcp;

import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;

/**
 * Starts the daemon while the host loads the tools, so that jdtls imports the project before the first call: the first
 * {@link #begin()} runs the start in the background, the following ones do nothing. A start that fails does not fail
 * what the host is doing (its handshake): it is told on the standard error, and the first call that needs the daemon
 * meets the same failure and reports it. A call {@link #awaitEnd() waits} for the start that is running, so that it does
 * not start a second daemon beside it.
 */
public final class WarmUp {

    private final Runnable start;
    private final PrintStream err;
    private final Executor executor;
    /** Begun by the thread that reads the client, awaited by the threads of the calls. */
    private volatile @Nullable CompletableFuture<Void> running;

    /**
     * A warm-up that runs the start with this executor.
     *
     * @param start what starts the daemon
     * @param err where a start that fails is told
     * @param executor where the start runs
     */
    WarmUp(Runnable start, PrintStream err, Executor executor) {
        this.start = start;
        this.err = err;
        this.executor = executor;
    }

    /**
     * A warm-up that runs the start in a thread of its own, which does not keep the program alive: the host that closes
     * the input does not wait for a daemon that is still starting.
     *
     * @param start what starts the daemon
     * @param err where a start that fails is told
     */
    public static WarmUp inBackground(Runnable start, PrintStream err) {
        return new WarmUp(
                start,
                err,
                task -> Thread.ofPlatform().daemon().name("gabier-warm-up").start(task));
    }

    /** Starts the daemon in the background, once: the next calls do nothing. */
    void begin() {
        if (running == null) {
            running = CompletableFuture.runAsync(this::startQuietly, executor);
        }
    }

    /** Waits for the start that is running to end; returns at once when none was begun, or when it ended. */
    void awaitEnd() {
        var begun = running;
        if (begun != null) {
            begun.join();
        }
    }

    private void startQuietly() {
        try {
            start.run();
        } catch (RuntimeException | Error failure) {
            // An Error as well (a class or a library that is missing, as when the native image spawns a process): the
            // thread of the warm-up must end quietly, or the call that waits for it would be thrown the Error.
            err.println("gabier mcp: the daemon could not be started: " + failure.getMessage());
        }
    }
}
