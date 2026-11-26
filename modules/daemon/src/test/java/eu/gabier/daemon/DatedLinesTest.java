package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DatedLinesTest {

    private static final String NEWLINE = System.lineSeparator();

    private final ByteArrayOutputStream written = new ByteArrayOutputStream();
    private final PrintStream stream = new PrintStream(written, true, StandardCharsets.UTF_8);

    private String written() {
        return written.toString(StandardCharsets.UTF_8);
    }

    @Test
    void beginsALineWithTheTimeInTheLocalTimeOfTheClockWithItsOffsetToTheSecond() {
        var clock = Clock.fixed(Instant.parse("2026-11-26T08:43:12.987Z"), ZoneOffset.ofHours(1));

        new DatedLines(clock, stream).accept("jdtls started");

        assertThat(written()).isEqualTo("2026-11-26T09:43:12+01:00 jdtls started" + NEWLINE);
    }

    @Test
    void writesTheOffsetOfAZoneBehindUtcAndOfUtcItself() {
        var instant = Instant.parse("2026-01-02T03:04:05Z");

        new DatedLines(Clock.fixed(instant, ZoneOffset.ofHoursMinutes(-3, -30)), stream).accept("west");
        new DatedLines(Clock.fixed(instant, ZoneOffset.UTC), stream).accept("greenwich");

        assertThat(written())
                .isEqualTo(
                        "2026-01-01T23:34:05-03:30 west" + NEWLINE + "2026-01-02T03:04:05+00:00 greenwich" + NEWLINE);
    }

    @Test
    void datesEachLineWithTheTimeOfTheClockWhenItIsWritten() {
        var lines = new DatedLines(new TickingClock(Instant.parse("2026-11-26T08:00:00Z"), 90), stream);

        lines.accept("jdtls started, pid 4242");
        lines.accept("jdtls status ServiceReady: ready");

        assertThat(written())
                .isEqualTo("2026-11-26T08:00:00+00:00 jdtls started, pid 4242" + NEWLINE
                        + "2026-11-26T08:01:30+00:00 jdtls status ServiceReady: ready" + NEWLINE);
    }

    /** A clock that moves on by a fixed number of seconds each time it is read. */
    private static final class TickingClock extends Clock {

        private Instant now;
        private final long seconds;

        TickingClock(Instant start, long seconds) {
            this.now = start;
            this.seconds = seconds;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            var current = now;
            now = now.plusSeconds(seconds);

            return current;
        }
    }
}
