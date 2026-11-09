package eu.gabier.cli.infrastructure.primary;

import static eu.gabier.cli.infrastructure.primary.CliFixtures.MODIFIED;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExtractingAMethodTest extends CliTest {

    private static final String REQUEST = """
        {"jsonrpc":"2.0","id":1,"method":"extract-method","params":{"dryRun":"%s","endColumn":"%s","endLine":"%s",\
        "file":"%s","name":"%s","startColumn":"%s","startLine":"%s"}}\
        """;

    @Test
    void asksTheDaemonToExtractTheSelectionCountedFromOneAsItIsShownAndSendsItFromZero() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("extract-method", "/project/Totals.java", "6:9", "6:47", "printCount"))
                    .isZero();

            assertThat(daemon.requests())
                    .containsExactly(
                            REQUEST.formatted(false, 46, 5, Path.of("/project/Totals.java"), "printCount", 8, 5));
        }
    }

    @Nested
    class ReturningChangedFiles implements ChangesCommandContract {

        @Override
        public CliTest cli() {
            return ExtractingAMethodTest.this;
        }

        @Override
        public String commandName() {
            return "extract-method";
        }

        @Override
        public List<String> afterTheFile() {
            return List.of("6:9", "6:47", "printCount");
        }

        @Override
        public String request(boolean dryRun, Path file) {
            return REQUEST.formatted(dryRun, 46, 5, file, "printCount", 8, 5);
        }
    }
}
