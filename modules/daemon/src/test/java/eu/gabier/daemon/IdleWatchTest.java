package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

/** The idle rule of the daemon, on a clock the test moves: nothing waits for real. */
class IdleWatchTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(60);

    private final Clock clock = new Clock();
    private final IdleWatch watch = new IdleWatch(clock, TIMEOUT);
    private final List<Duration> waits = new ArrayList<>();
    private final AtomicInteger stops = new AtomicInteger();

    @Test
    void sleepsForTheWholeDelayWhenNothingHasHappenedAndStopsOnceItIsOver() {
        var stopped = watch.watch(pausing(), stops::incrementAndGet);

        assertThat(stopped).isTrue();
        assertThat(waits).containsExactly(TIMEOUT);
        assertThat(stops).hasValue(1);
    }

    @Test
    void sleepsForWhatIsLeftOfTheDelayAfterAConnectionThatEnded() {
        clock.advance(Duration.ofMinutes(20));
        watch.connected();
        clock.advance(Duration.ofMinutes(5));
        watch.disconnected();
        clock.advance(Duration.ofMinutes(10));

        var stopped = watch.watch(pausing(), stops::incrementAndGet);

        assertThat(stopped).isTrue();
        assertThat(waits).containsExactly(Duration.ofMinutes(50));
    }

    @Test
    void doesNotStopWhileAConnectionIsOpenHoweverLongItLastsAndCountsTheDelayFromItsEnd() {
        watch.connected();
        clock.advance(Duration.ofHours(3));
        var pause = new ScriptedPause(clock, waits).atTheFirstWait(Duration.ofMinutes(10), watch::disconnected);

        var stopped = watch.watch(pause, stops::incrementAndGet);

        assertThat(stopped).isTrue();
        assertThat(waits).containsExactly(TIMEOUT, Duration.ofMinutes(10));
        assertThat(stops).hasValue(1);
    }

    @Test
    void doesNotStopWhenThePauseSaysThatTheDaemonIsStoppingAnyway() {
        var stopped = watch.watch(duration -> false, stops::incrementAndGet);

        assertThat(stopped).isFalse();
        assertThat(stops).hasValue(0);
    }

    @Test
    void refusesAConnectionOnceItHasDecidedToStopAndAcceptsOneBefore() {
        assertThat(watch.connected()).isTrue();
        watch.disconnected();
        clock.advance(TIMEOUT);
        watch.watch(duration -> true, () -> {});

        assertThat(watch.connected()).isFalse();
    }

    @Test
    void saysTheDelayAndWhatIsLeftOfItAsAConnectionSeesIt() {
        watch.connected();
        clock.advance(Duration.ofMinutes(35));
        watch.disconnected();
        clock.advance(Duration.ofMinutes(10));
        watch.connected();

        assertThat(watch.timeout()).isEqualTo(TIMEOUT);
        assertThat(watch.left()).isEqualTo(Duration.ofMinutes(50));
    }

    @Test
    void saysTheWholeDelayWhileAnotherConnectionIsOpen() {
        watch.connected();
        watch.connected();
        clock.advance(Duration.ofMinutes(45));

        assertThat(watch.left()).isEqualTo(TIMEOUT);
    }

    @Test
    void neverSaysLessThanNothingLeft() {
        watch.connected();
        clock.advance(Duration.ofHours(2));

        assertThat(watch.left()).isEqualTo(Duration.ZERO);
    }

    private IdleWatch.Pause pausing() {
        return new ScriptedPause(clock, waits);
    }

    /** A pause that moves the clock by the time asked, and lets the test act during the first wait. */
    private static final class ScriptedPause implements IdleWatch.Pause {

        private final Clock clock;
        private final List<Duration> waits;
        private Duration after = Duration.ZERO;
        private Runnable action = () -> {};

        ScriptedPause(Clock clock, List<Duration> waits) {
            this.clock = clock;
            this.waits = waits;
        }

        ScriptedPause atTheFirstWait(Duration after, Runnable action) {
            this.after = after;
            this.action = action;

            return this;
        }

        @Override
        public boolean pause(Duration duration) {
            waits.add(duration);
            if (waits.size() == 1) {
                clock.advance(after);
                action.run();
                clock.advance(duration.minus(after));
            } else {
                clock.advance(duration);
            }

            return true;
        }
    }

    private static final class Clock implements LongSupplier {

        private long now = Long.MAX_VALUE - Duration.ofDays(30).toNanos();

        void advance(Duration duration) {
            now += duration.toNanos();
        }

        @Override
        public long getAsLong() {
            return now;
        }
    }
}
