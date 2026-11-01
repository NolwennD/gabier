package fr.craft.gabier.cli;

import static fr.craft.gabier.cli.CliFixtures.MODIFIED;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InliningTest extends CliTest {

    private static final String REQUEST = """
        {"jsonrpc":"2.0","id":1,"method":"inline","params":{"column":"%s","dryRun":"%s","file":"%s",\
        "line":"%s"}}\
        """;

    @Test
    void asksTheDaemonToInlineTheSymbolAtThePositionCountedFromOneAsItIsShownAndSendsItFromZero() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("inline", "/project/Prices.java", "4:22")).isZero();

            assertThat(daemon.requests())
                    .containsExactly(REQUEST.formatted(21, false, Path.of("/project/Prices.java"), 3));
        }
    }

    @Nested
    class ReturningChangedFiles implements ChangesCommandContract {

        @Override
        public CliTest cli() {
            return InliningTest.this;
        }

        @Override
        public String commandName() {
            return "inline";
        }

        @Override
        public List<String> afterTheFile() {
            return List.of("4:22");
        }

        @Override
        public String request(boolean dryRun, Path file) {
            return REQUEST.formatted(21, dryRun, file, 3);
        }
    }
}
