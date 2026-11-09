package eu.gabier.cli.infrastructure.primary.mcp;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

/**
 * The time of the calls that wait, moved by the test: a clock that stands still until {@link #advance} and a ticker that
 * runs its task only when the test says {@link #tick()}, so that nothing waits for a given time.
 */
final class ManualTime implements Pacing.Ticker {

    /** Real time, as no test waits for a daemon that answers at once: a daemon that does not is given up after this. */
    static final Duration STATUS_LIMIT = Duration.ofSeconds(1);

    private final AtomicLong nanos = new AtomicLong();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private volatile @Nullable Runnable task;

    /** The pacing of a server whose time is this one, with the usual interval and bound. */
    Pacing pacing() {
        return new Pacing(this, nanos::get, Pacing.INTERVAL, Pacing.BOUND, STATUS_LIMIT);
    }

    /** Lets this much time pass. */
    void advance(Duration time) {
        nanos.addAndGet(time.toNanos());
    }

    /** Runs what the ticker would run at the end of an interval; the test fails when no call has started it. */
    void tick() {
        var running = task;
        if (running == null) {
            throw new AssertionError("The ticker was never started: no call is waiting");
        }
        running.run();
    }

    /** Whether the server stopped the ticker, as it does when it has nothing more to wait for. */
    boolean stopped() {
        return stopped.get();
    }

    @Override
    public Runnable every(Duration interval, Runnable task) {
        this.task = task;

        return () -> stopped.set(true);
    }
}
