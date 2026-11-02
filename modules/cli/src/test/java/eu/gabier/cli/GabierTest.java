package eu.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GabierTest extends CliTest {

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
    class JsonOption {

        @Test
        void aUsageErrorIsTheProblemObjectOnTheStandardError() {
            assertThat(run("--json", "--bogus")).isEqualTo(2);

            assertThat(err()).isEqualTo(usageJson("--bogus"));
        }

        @Test
        void aUsageErrorPrintsNothingOnTheStandardOutput() {
            run("--json", "--bogus");

            assertThat(out()).isEmpty();
        }

        @Test
        void canComeAfterTheOtherArguments() {
            assertThat(run("--bogus", "--json")).isEqualTo(2);

            assertThat(err()).isEqualTo(usageJson("--bogus"));
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
        void runReportsAnExceptionThatNobodyExpectedAsAnInternalErrorInsteadOfLettingItEscape() {
            Map<String, String> environment = new HashMap<>() {
                @Override
                public String get(Object key) {
                    throw bug;
                }
            };

            var exit = Gabier.run(new String[] {"status"}, environment, printStream(out), printStream(err));

            assertThat(exit).isEqualTo(1);
            assertThat(out()).isEmpty();
            assertThat(err())
                    .isEqualTo("Internal server error: An internal error occurred, and it is not described here "
                            + "(internal-server-error)" + System.lineSeparator());
        }

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
