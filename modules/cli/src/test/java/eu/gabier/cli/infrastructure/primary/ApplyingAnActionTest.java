package eu.gabier.cli.infrastructure.primary;

import static eu.gabier.cli.infrastructure.primary.CliFixtures.MODIFIED;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ApplyingAnActionTest extends CliTest {

    private static final String TITLE = "Import 'List' (java.util)";

    private static final String REQUEST = """
        {"jsonrpc":"2.0","id":1,"method":"apply-action","params":{"dryRun":"%s","endColumn":"8","endLine":"3",\
        "file":"%s","startColumn":"4","startLine":"3","title":"%s"}}\
        """;

    @Test
    void asksTheDaemonToApplyTheActionOfTheTitleOnTheRangeCountedFromOneAndSendsItFromZero() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("apply-action", "/project/Offers.java", "4:5", "4:9", TITLE))
                    .isZero();

            assertThat(daemon.requests())
                    .containsExactly(REQUEST.formatted(false, Path.of("/project/Offers.java"), TITLE));
        }
    }

    @Test
    void sendsTheTitleAsOneOperandWhateverItHolds() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("apply-action", "/project/Offers.java", "4:5", "4:9", "Create class 'List<T>'"))
                    .isZero();

            assertThat(daemon.requests())
                    .containsExactly(
                            REQUEST.formatted(false, Path.of("/project/Offers.java"), "Create class 'List<T>'"));
        }
    }

    @Nested
    class ReturningChangedFiles implements ChangesCommandContract {

        @Override
        public CliTest cli() {
            return ApplyingAnActionTest.this;
        }

        @Override
        public String commandName() {
            return "apply-action";
        }

        @Override
        public List<String> afterTheFile() {
            return List.of("4:5", "4:9", TITLE);
        }

        @Override
        public String request(boolean dryRun, Path file) {
            return REQUEST.formatted(dryRun, file, TITLE);
        }
    }
}
