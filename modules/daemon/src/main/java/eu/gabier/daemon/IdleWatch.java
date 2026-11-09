package eu.gabier.daemon;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * The idle rule of the daemon: it is idle when no connection is open and the last one ended at least the delay ago (a
 * daemon that nobody has connected to counts from its start). A connection is a call in progress, the cli opening one
 * per call, so an open connection holds the daemon up, and the delay is counted from the end of the last one. Once the
 * watch has decided that the daemon stops, {@link #connected} says so to every connection that follows, and the server
 * answers those with the refusal {@code daemon-stopping} and runs nothing: the cli that gets it asks again.
 *
 * <p>The time is a monotonic clock in nanoseconds and the wait is a {@link Pause}, both given, so that a test moves
 * the clock and waits for nothing.
 */
final class IdleWatch {

    /** How the watch waits. */
    @FunctionalInterface
    interface Pause {

        /**
         * Waits for at most this long.
         *
         * @return false when the watch is no longer needed (the daemon is stopping for another reason)
         */
        boolean pause(Duration duration);
    }

    private final LongSupplier nanoClock;
    private final Duration timeout;
    private int open;
    private long lastEnd;
    private boolean stopping;

    IdleWatch(LongSupplier nanoClock, Duration timeout) {
        this.nanoClock = nanoClock;
        this.timeout = timeout;
        lastEnd = nanoClock.getAsLong();
    }

    /** The delay with no connection after which the daemon stops. */
    Duration timeout() {
        return timeout;
    }

    /** A connection is accepted; false, and the connection is to be closed unread, when the daemon decided to stop. */
    synchronized boolean connected() {
        if (stopping) {
            return false;
        }
        open++;

        return true;
    }

    /** A connection that {@link #connected} accepted has ended: the delay is counted from now when it was the last. */
    synchronized void disconnected() {
        open--;
        lastEnd = nanoClock.getAsLong();
    }

    /**
     * What is left of the delay as a connection sees it, the one that asks being open itself: the whole delay while
     * another connection is open, else what the last end of a connection leaves, never less than nothing. The call that
     * asks is a connection, so it restarts the delay when it ends, but this is the time left before it.
     */
    synchronized Duration left() {
        if (open > 1) {
            return timeout;
        }
        var left = timeout.minus(Duration.ofNanos(nanoClock.getAsLong() - lastEnd));

        return left.isNegative() ? Duration.ZERO : left;
    }

    /**
     * Waits, then decides, until the daemon is idle for the delay.
     *
     * @param pause how to wait
     * @param stop what to do once the daemon is idle, after which no connection is accepted
     * @return whether the daemon was idle and has been stopped, false when the pause ended the watch
     */
    boolean watch(Pause pause, Runnable stop) {
        while (true) {
            var wait = decide();
            if (wait.isZero()) {
                stop.run();

                return true;
            }
            if (!pause.pause(wait)) {
                return false;
            }
        }
    }

    /** Zero, after deciding to stop, when the daemon is idle; else how long to wait before looking again. */
    private synchronized Duration decide() {
        if (open > 0) {
            return timeout;
        }
        var left = left();
        if (left.isZero()) {
            stopping = true;
        }

        return left;
    }
}
