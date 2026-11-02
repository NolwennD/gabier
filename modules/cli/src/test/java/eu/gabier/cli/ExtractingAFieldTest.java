package eu.gabier.cli;

import static eu.gabier.cli.CliFixtures.MODIFIED;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExtractingAFieldTest extends CliTest {

    private static final String REQUEST = """
        {"jsonrpc":"2.0","id":1,"method":"extract-field","params":{"dryRun":"%s","endColumn":"%s","endLine":"%s",\
        "file":"%s","name":"%s","startColumn":"%s","startLine":"%s"}}\
        """;

    @Test
    void asksTheDaemonToExtractTheSelectionCountedFromOneAsItIsShownAndSendsItFromZero() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("extract-field", "/project/Totals.java", "6:28", "6:37", "label"))
                    .isZero();

            assertThat(daemon.requests())
                    .containsExactly(REQUEST.formatted(false, 36, 5, Path.of("/project/Totals.java"), "label", 27, 5));
        }
    }

    @Nested
    class ReturningChangedFiles implements ChangesCommandContract {

        @Override
        public CliTest cli() {
            return ExtractingAFieldTest.this;
        }

        @Override
        public String commandName() {
            return "extract-field";
        }

        @Override
        public List<String> afterTheFile() {
            return List.of("6:28", "6:37", "label");
        }

        @Override
        public String request(boolean dryRun, Path file) {
            return REQUEST.formatted(dryRun, 36, 5, file, "label", 27, 5);
        }
    }
}
