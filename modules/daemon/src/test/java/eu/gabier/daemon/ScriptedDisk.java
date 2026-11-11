package eu.gabier.daemon;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The disk of a project under test: what it tells is given by the test ({@link #says}), and a wait that has nothing to
 * tell ends as quiet after a few milliseconds, as a wait of the real one does after its timeout, so that the thread
 * that follows it looks at whether it must stop often.
 */
final class ScriptedDisk implements DiskChanges {

    private static final long LONGEST_WAIT_MILLIS = 20;

    private final BlockingQueue<Signal> signals = new LinkedBlockingQueue<>();
    private final AtomicInteger waits = new AtomicInteger();
    private volatile boolean closed;

    /** What the next wait ends with. */
    void says(Signal signal) {
        signals.add(signal);
    }

    /** How many times the disk was waited on. */
    int waits() {
        return waits.get();
    }

    boolean closed() {
        return closed;
    }

    @Override
    public Signal await(Duration timeout) throws InterruptedException {
        waits.incrementAndGet();
        var signal = signals.poll(Math.min(timeout.toMillis(), LONGEST_WAIT_MILLIS), TimeUnit.MILLISECONDS);

        return signal == null ? Signal.QUIET : signal;
    }

    @Override
    public void close() {
        closed = true;
    }
}
