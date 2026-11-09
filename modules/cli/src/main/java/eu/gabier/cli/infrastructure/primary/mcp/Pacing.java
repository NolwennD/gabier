package eu.gabier.cli.infrastructure.primary.mcp;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * The time of the calls that wait, which a test replaces by hand: when a call that has waited too long is given up, and
 * how often the agent is told how a call goes. A call is given up after {@link #BOUND}, and told about every {@link
 * #INTERVAL}, by a ticker that runs in the background.
 *
 * @param ticker what runs a task at an interval, until it is told to stop
 * @param nanoClock a monotonic clock in nanoseconds, never the wall clock, which a user can set back
 * @param interval how often the calls that wait are looked at
 * @param bound how long a call waits for the daemon at most
 * @param statusLimit how long a notice waits for the daemon to say where jdtls is, so that a daemon that does not answer
 *     never holds the ticker, which the bound depends on
 */
record Pacing(Ticker ticker, LongSupplier nanoClock, Duration interval, Duration bound, Duration statusLimit) {

    /**
     * How long a call waits for the daemon at most. The daemon gives up on its own: jdtls has five minutes to start
     * (all its waits together), then each request to it five minutes, and the cli twenty seconds to find the daemon
     * listening. The bound is above the sum, so that the refusal of the daemon, which says what went wrong, arrives
     * first; this one only catches a daemon that does not answer at all.
     */
    static final Duration BOUND = Duration.ofMinutes(11);

    /** How often the agent is told how a call that waits goes: a notice a few seconds apart is not a flood. */
    static final Duration INTERVAL = Duration.ofSeconds(5);

    /**
     * How long a notice waits for the status of the daemon, which answers at once while jdtls starts: a daemon that does
     * not is told as "waiting for the daemon".
     */
    static final Duration STATUS_LIMIT = Duration.ofSeconds(2);

    /** The time as it is: a thread in the background that does not keep the program alive. */
    static Pacing real() {
        return new Pacing(Pacing::inBackground, System::nanoTime, INTERVAL, BOUND, STATUS_LIMIT);
    }

    private static Runnable inBackground(Duration interval, Runnable task) {
        var scheduler = Executors.newSingleThreadScheduledExecutor(runnable ->
                Thread.ofPlatform().daemon().name("gabier-mcp-ticker").unstarted(runnable));
        var _ = scheduler.scheduleWithFixedDelay(task, interval.toNanos(), interval.toNanos(), TimeUnit.NANOSECONDS);

        return scheduler::shutdownNow;
    }

    /** What runs a task again and again. */
    @FunctionalInterface
    interface Ticker {

        /**
         * Runs the task every interval, the first time one interval from now.
         *
         * @return what stops it
         */
        Runnable every(Duration interval, Runnable task);
    }
}
