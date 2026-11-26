package eu.gabier.daemon;

import java.io.PrintStream;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Writes each line to a stream, begun with the time it is written: the local time of the clock, to the second, with
 * its offset from UTC ({@code 2026-11-26T09:43:12+01:00 jdtls started}). The log of the daemon is made of these lines,
 * so that the time between two of them can be read from the log alone. The format and the clock are a choice of the
 * daemon, not of what writes the lines.
 */
final class DatedLines implements Consumer<String> {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");

    private final Clock clock;
    private final PrintStream stream;

    DatedLines(Clock clock, PrintStream stream) {
        this.clock = clock;
        this.stream = stream;
    }

    @Override
    public void accept(String line) {
        stream.println(TIME.format(ZonedDateTime.now(clock)) + " " + line);
    }
}
