package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MainTest {

    private static final SelfCheck PASSING = new SelfCheck("fr.craft.gabier.daemon", "org.eclipse.lsp4j");
    private static final SelfCheck FAILING = new SelfCheck(null, null);

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(SelfCheck selfCheck, String... args) {
        return Main.run(args, selfCheck, printStream(out), printStream(err));
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
    class WithoutArguments {

        @Test
        void printsTheNameAndTheProtocolVersion() {
            run(PASSING);

            assertThat(out()).isEqualTo("gabier-daemon 0.1" + System.lineSeparator());
        }

        @Test
        void printsNothingOnTheStandardError() {
            run(PASSING);

            assertThat(err()).isEmpty();
        }

        @Test
        void exitsWithStatusZero() {
            assertThat(run(PASSING)).isZero();
        }

        @Test
        void doesNotRunTheSelfCheck() {
            assertThat(run(FAILING)).isZero();
        }

        @Test
        void anotherArgumentIsNotTheSelfCheck() {
            assertThat(run(FAILING, "--other")).isZero();
            assertThat(out()).isEqualTo("gabier-daemon 0.1" + System.lineSeparator());
        }

        @Test
        void theSelfCheckFollowedByAnotherArgumentIsNotTheSelfCheck() {
            assertThat(run(FAILING, "--self-check", "--other")).isZero();
            assertThat(out()).isEqualTo("gabier-daemon 0.1" + System.lineSeparator());
        }
    }

    @Nested
    class SelfCheckOption {

        @Test
        void exitsWithStatusZeroWhenTheCheckPasses() {
            assertThat(run(PASSING, "--self-check")).isZero();
        }

        @Test
        void printsTheSummaryOnTheStandardOutputWhenTheCheckPasses() {
            run(PASSING, "--self-check");

            assertThat(out()).isEqualTo("self-check ok: " + PASSING.summary() + System.lineSeparator());
            assertThat(err()).isEmpty();
        }

        @Test
        void exitsWithStatusOneWhenTheCheckFails() {
            assertThat(run(FAILING, "--self-check")).isEqualTo(1);
        }

        @Test
        void printsEveryProblemOnTheStandardErrorWhenTheCheckFails() {
            run(FAILING, "--self-check");

            assertThat(out()).isEmpty();
            assertThat(err()).startsWith("self-check failed: ").contains(FAILING.problems());
        }
    }
}
