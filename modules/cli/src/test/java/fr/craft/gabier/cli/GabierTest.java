package fr.craft.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;

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

            assertThat(out()).isEqualTo("gabier 0.1" + System.lineSeparator());
        }

        @Test
        void printsNothingOnTheStandardError() {
            run("--version");

            assertThat(err()).isEmpty();
        }

        @Test
        void exitsWithStatusZero() {
            assertThat(run("--version")).isZero();
        }
    }

    @Nested
    class UnknownUsage {

        @Test
        void unknownArgumentPrintsTheUsageOnTheStandardError() {
            run("--help");

            assertThat(err()).isEqualTo(USAGE);
        }

        @Test
        void unknownArgumentPrintsNothingOnTheStandardOutput() {
            run("--help");

            assertThat(out()).isEmpty();
        }

        @Test
        void unknownArgumentExitsWithStatusTwo() {
            assertThat(run("--help")).isEqualTo(2);
        }

        @Test
        void noArgumentIsAnUnknownUsage() {
            assertThat(run()).isEqualTo(2);
            assertThat(out()).isEmpty();
            assertThat(err()).isEqualTo(USAGE);
        }

        @Test
        void versionFollowedByAnotherArgumentIsAnUnknownUsage() {
            assertThat(run("--version", "--help")).isEqualTo(2);
            assertThat(out()).isEmpty();
            assertThat(err()).isEqualTo(USAGE);
        }

        @Test
        void unknownArgumentFollowedByVersionIsAnUnknownUsage() {
            assertThat(run("--help", "--version")).isEqualTo(2);
            assertThat(out()).isEmpty();
            assertThat(err()).isEqualTo(USAGE);
        }
    }
}
