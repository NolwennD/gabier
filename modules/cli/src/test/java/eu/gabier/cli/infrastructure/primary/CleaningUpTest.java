package eu.gabier.cli.infrastructure.primary;

import static eu.gabier.cli.infrastructure.primary.CliFixtures.MODIFIED;
import static eu.gabier.cli.infrastructure.primary.CliFixtures.NOTHING;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CleaningUpTest extends CliTest {

    private static final String REQUEST = """
        {"jsonrpc":"2.0","id":1,"method":"cleanup","params":{"cleanUps":"%s","dryRun":"%s","file":"%s"}}\
        """;

    @Test
    void asksTheDaemonToApplyTheCleanUpToTheFile() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("cleanup", "/project/Banner.java", "stringConcatToTextBlock"))
                    .isZero();

            assertThat(daemon.requests())
                    .containsExactly(
                            REQUEST.formatted("stringConcatToTextBlock", false, Path.of("/project/Banner.java")));
        }
    }

    @Test
    void sendsSeveralCleanUpsOnePerLineInTheOrderGiven() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("cleanup", "/project/Banner.java", "addFinalModifier", "stringConcatToTextBlock"))
                    .isZero();

            assertThat(daemon.requests())
                    .containsExactly(REQUEST.formatted(
                            "addFinalModifier\\nstringConcatToTextBlock", false, Path.of("/project/Banner.java")));
        }
    }

    @Test
    void takesTheDryRunOptionBetweenTheOperandsToo() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("cleanup", "/project/Banner.java", "--dry-run", "addOverride"))
                    .isZero();

            assertThat(daemon.requests())
                    .containsExactly(REQUEST.formatted("addOverride", true, Path.of("/project/Banner.java")));
        }
    }

    @Test
    void saysThatThereIsNothingToChangeForAFileWithNothingToCleanUp() throws IOException {
        try (var daemon = daemonAnswering(NOTHING.toJson())) {
            assertThat(runWithSocket("cleanup", "/project/Plain.java", "stringConcatToTextBlock"))
                    .isZero();

            assertThat(out().lines()).containsExactly("nothing to change");
            assertThat(err()).isEmpty();
        }
    }

    @Test
    void saysBothThatItIsADryRunAndThatThereIsNothingToChange() throws IOException {
        try (var daemon = daemonAnswering(NOTHING.toJson())) {
            assertThat(runWithSocket("cleanup", "--dry-run", "/project/Plain.java", "stringConcatToTextBlock"))
                    .isZero();

            assertThat(out().lines()).containsExactly("dry run, nothing written", "nothing to change");
        }
    }

    @Test
    void refusesACleanUpWrittenWithALineBreakWithoutAskingTheDaemon() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("cleanup", "/project/Banner.java", "addOverride\nqualifyMembers"))
                    .isEqualTo(2);

            assertThat(daemon.requests()).isEmpty();
        }
    }

    @Test
    void refusesAnEmptyCleanUpAsAUsageErrorBeforeTheRunnerSeesIt() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("cleanup", "/project/Banner.java", "addOverride", ""))
                    .isEqualTo(2);

            assertThat(err())
                    .startsWith("Usage error: Unsupported arguments;")
                    .doesNotContain("operation-invalid-values");
            assertThat(daemon.requests()).isEmpty();
        }
    }

    @Nested
    class ReturningChangedFiles implements ChangesCommandContract {

        @Override
        public CliTest cli() {
            return CleaningUpTest.this;
        }

        @Override
        public String commandName() {
            return "cleanup";
        }

        @Override
        public List<String> afterTheFile() {
            return List.of("stringConcatToTextBlock");
        }

        @Override
        public String request(boolean dryRun, Path file) {
            return REQUEST.formatted("stringConcatToTextBlock", dryRun, file);
        }
    }
}
