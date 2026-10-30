package fr.craft.gabier.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

            assertEquals("gabier-daemon 0.1" + System.lineSeparator(), out());
        }

        @Test
        void printsNothingOnTheStandardError() {
            run(PASSING);

            assertEquals("", err());
        }

        @Test
        void exitsWithStatusZero() {
            assertEquals(0, run(PASSING));
        }

        @Test
        void doesNotRunTheSelfCheck() {
            assertEquals(0, run(FAILING));
        }

        @Test
        void anotherArgumentIsNotTheSelfCheck() {
            assertEquals(0, run(FAILING, "--other"));
            assertEquals("gabier-daemon 0.1" + System.lineSeparator(), out());
        }

        @Test
        void theSelfCheckFollowedByAnotherArgumentIsNotTheSelfCheck() {
            assertEquals(0, run(FAILING, "--self-check", "--other"));
            assertEquals("gabier-daemon 0.1" + System.lineSeparator(), out());
        }
    }

    @Nested
    class SelfCheckOption {

        @Test
        void exitsWithStatusZeroWhenTheCheckPasses() {
            assertEquals(0, run(PASSING, "--self-check"));
        }

        @Test
        void printsTheSummaryOnTheStandardOutputWhenTheCheckPasses() {
            run(PASSING, "--self-check");

            assertEquals("self-check ok: " + PASSING.summary() + System.lineSeparator(), out());
            assertEquals("", err());
        }

        @Test
        void exitsWithStatusOneWhenTheCheckFails() {
            assertEquals(1, run(FAILING, "--self-check"));
        }

        @Test
        void printsEveryProblemOnTheStandardErrorWhenTheCheckFails() {
            run(FAILING, "--self-check");

            assertEquals("", out());
            assertTrue(err().startsWith("self-check failed: "));
            FAILING.problems().forEach(problem -> assertTrue(err().contains(problem)));
        }
    }
}
