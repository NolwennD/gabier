package fr.craft.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GabierTest {

    private static final String USAGE_DETAIL = "Unsupported arguments; usage: gabier [--json] --version";

    private static String usageText(String arguments) {
        return "Usage error: " + USAGE_DETAIL + " (usage-error; arguments=" + arguments + ")" + System.lineSeparator();
    }

    private static String usageJson(String arguments) {
        return "{\"type\":\"urn:gabier:error:usage-error\",\"title\":\"Usage error\",\"detail\":\"" + USAGE_DETAIL
                + "\",\"key\":\"usage-error\",\"parameters\":{\"arguments\":\"" + arguments + "\"}}"
                + System.lineSeparator();
    }

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

            assertThat(err()).isEqualTo(usageText("--help"));
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
            assertThat(err()).isEqualTo(usageText(""));
        }

        @Test
        void versionFollowedByAnotherArgumentIsAnUnknownUsage() {
            assertThat(run("--version", "--help")).isEqualTo(2);
            assertThat(out()).isEmpty();
            assertThat(err()).isEqualTo(usageText("--version --help"));
        }

        @Test
        void unknownArgumentFollowedByVersionIsAnUnknownUsage() {
            assertThat(run("--help", "--version")).isEqualTo(2);
            assertThat(out()).isEmpty();
            assertThat(err()).isEqualTo(usageText("--help --version"));
        }
    }

    @Nested
    class JsonOption {

        @Test
        void aUsageErrorIsTheProblemObjectOnTheStandardError() {
            assertThat(run("--json", "--help")).isEqualTo(2);

            assertThat(err()).isEqualTo(usageJson("--help"));
        }

        @Test
        void aUsageErrorPrintsNothingOnTheStandardOutput() {
            run("--json", "--help");

            assertThat(out()).isEmpty();
        }

        @Test
        void canComeAfterTheOtherArguments() {
            assertThat(run("--help", "--json")).isEqualTo(2);

            assertThat(err()).isEqualTo(usageJson("--help"));
        }

        @Test
        void aloneIsAUsageErrorWithNoArgument() {
            assertThat(run("--json")).isEqualTo(2);

            assertThat(err()).isEqualTo(usageJson(""));
        }

        @Test
        void doesNotChangeTheVersion() {
            assertThat(run("--json", "--version")).isZero();

            assertThat(out()).isEqualTo("gabier 0.1" + System.lineSeparator());
            assertThat(err()).isEmpty();
        }

        @Test
        void afterTheVersionDoesNotChangeItEither() {
            assertThat(run("--version", "--json")).isZero();

            assertThat(out()).isEqualTo("gabier 0.1" + System.lineSeparator());
        }
    }

    @Nested
    class Failure {

        private final RuntimeException bug = new IllegalStateException("secret /home/someone/file");

        @Test
        void anUnexpectedExceptionIsAnInternalErrorWithItsOwnExitCode() {
            assertThat(Gabier.fail(bug, false, printStream(err))).isEqualTo(1);
        }

        @Test
        void anUnexpectedExceptionIsReportedAsText() {
            Gabier.fail(bug, false, printStream(err));

            assertThat(err())
                    .isEqualTo("Internal server error: An internal error occurred, and it is not described here "
                            + "(internal-server-error)" + System.lineSeparator());
        }

        @Test
        void anUnexpectedExceptionIsReportedAsJson() {
            Gabier.fail(bug, true, printStream(err));

            assertThat(err())
                    .isEqualTo("{\"type\":\"urn:gabier:error:internal-server-error\","
                            + "\"title\":\"Internal server error\","
                            + "\"detail\":\"An internal error occurred, and it is not described here\","
                            + "\"key\":\"internal-server-error\",\"parameters\":{}}" + System.lineSeparator());
        }

        @Test
        void doesNotLeakTheMessageNorTheStackTrace() {
            Gabier.fail(bug, false, printStream(err));
            Gabier.fail(bug, true, printStream(err));

            assertThat(err())
                    .doesNotContain("secret")
                    .doesNotContain("IllegalStateException")
                    .doesNotContain("\tat ");
        }
    }
}
