package fr.craft.gabier.cli;

import static fr.craft.gabier.cli.CliFixtures.MODIFIED;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExtractingAVariableTest extends CliTest {

    private static final String REQUEST = """
        {"jsonrpc":"2.0","id":1,"method":"extract-variable","params":{"dryRun":"%s","endColumn":"%s","endLine":"%s",\
        "file":"%s","name":"%s","startColumn":"%s","startLine":"%s"}}\
        """;

    @Test
    void asksTheDaemonToExtractTheSelectionCountedFromOneAsItIsShownAndSendsItFromZero() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("extract-variable", "/project/Greeter.java", "7:26", "7:48", "joined"))
                    .isZero();

            assertThat(daemon.requests())
                    .containsExactly(
                            REQUEST.formatted(false, 47, 6, Path.of("/project/Greeter.java"), "joined", 25, 6));
        }
    }

    @Test
    void takesASelectionOverSeveralLines() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("extract-variable", "/project/Greeter.java", "7:26", "9:3", "joined"))
                    .isZero();

            assertThat(daemon.requests())
                    .containsExactly(REQUEST.formatted(false, 2, 8, Path.of("/project/Greeter.java"), "joined", 25, 6));
        }
    }

    @Nested
    class ReturningChangedFiles implements ChangesCommandContract {

        @Override
        public CliTest cli() {
            return ExtractingAVariableTest.this;
        }

        @Override
        public String commandName() {
            return "extract-variable";
        }

        @Override
        public List<String> afterTheFile() {
            return List.of("7:26", "7:48", "joined");
        }

        @Override
        public String request(boolean dryRun, Path file) {
            return REQUEST.formatted(dryRun, 47, 6, file, "joined", 25, 6);
        }
    }
}
