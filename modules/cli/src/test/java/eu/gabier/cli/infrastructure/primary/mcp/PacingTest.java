package eu.gabier.cli.infrastructure.primary.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** The time of the calls that wait, as it runs for real. */
class PacingTest {

    @Test
    void waitsLongerThanTheDaemonDoesSoThatItsOwnRefusalComesFirst() {
        // jdtls has five minutes to start and then five minutes for a request, and the cli twenty seconds to launch it.
        assertThat(Pacing.BOUND).isGreaterThan(Duration.ofMinutes(5 + 5).plusSeconds(20));
    }

    @Test
    void tellsTheAgentEveryFewSecondsAndNotOftenerThanAFloodOfNotices() {
        assertThat(Pacing.INTERVAL).isBetween(Duration.ofSeconds(1), Duration.ofSeconds(30));
    }

    @Test
    void runsTheTaskEveryIntervalInAThreadThatDoesNotKeepTheProgramAliveUntilItIsStopped() throws Exception {
        var ticks = new CountDownLatch(2);
        var inDaemonThread = new AtomicInteger();
        var stop = Pacing.real().ticker().every(Duration.ofMillis(10), () -> {
            if (Thread.currentThread().isDaemon()) {
                inDaemonThread.incrementAndGet();
            }
            ticks.countDown();
        });

        assertThat(ticks.await(10, TimeUnit.SECONDS)).isTrue();
        stop.run();

        assertThat(inDaemonThread.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void readsAMonotonicClock() {
        var clock = Pacing.real().nanoClock();

        var first = clock.getAsLong();

        assertThat(clock.getAsLong()).isGreaterThanOrEqualTo(first);
    }
}
