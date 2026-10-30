package fr.craft.gabier.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GabierTest {

    private static final String USAGE = "usage: gabier --version" + System.lineSeparator();

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(String... args) {
        return Gabier.run(args, printStream(out), printStream(err));
    }

    private static PrintStream printStream(ByteArrayOutputStream stream) {
        return new PrintStream(stream, true, StandardCharsets.UTF_8);
    }

    private String out() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return err.toString(StandardCharsets.UTF_8);
    }

    @Nested
    class Version {

        @Test
        void printsTheNameAndTheProtocolVersionOnTheStandardOutput() {
            run("--version");

            assertEquals("gabier 0.1" + System.lineSeparator(), out());
        }

        @Test
        void printsNothingOnTheStandardError() {
            run("--version");

            assertEquals("", err());
        }

        @Test
        void exitsWithStatusZero() {
            assertEquals(0, run("--version"));
        }
    }

    @Nested
    class UnknownUsage {

        @Test
        void unknownArgumentPrintsTheUsageOnTheStandardError() {
            run("--help");

            assertEquals(USAGE, err());
        }

        @Test
        void unknownArgumentPrintsNothingOnTheStandardOutput() {
            run("--help");

            assertEquals("", out());
        }

        @Test
        void unknownArgumentExitsWithStatusTwo() {
            assertEquals(2, run("--help"));
        }

        @Test
        void noArgumentIsAnUnknownUsage() {
            assertEquals(2, run());
            assertEquals("", out());
            assertEquals(USAGE, err());
        }

        @Test
        void versionFollowedByAnotherArgumentIsAnUnknownUsage() {
            assertEquals(2, run("--version", "--help"));
            assertEquals("", out());
            assertEquals(USAGE, err());
        }

        @Test
        void unknownArgumentFollowedByVersionIsAnUnknownUsage() {
            assertEquals(2, run("--help", "--version"));
            assertEquals("", out());
            assertEquals(USAGE, err());
        }
    }
}
