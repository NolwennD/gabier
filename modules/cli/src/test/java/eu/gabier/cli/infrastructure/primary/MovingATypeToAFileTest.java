package eu.gabier.cli.infrastructure.primary;

import static eu.gabier.cli.infrastructure.primary.CliFixtures.MODIFIED;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MovingATypeToAFileTest extends CliTest {

    private static final String REQUEST = """
        {"jsonrpc":"2.0","id":1,"method":"move-type-to-file","params":{"column":"%s","dryRun":"%s","file":"%s",\
        "line":"%s"}}\
        """;

    @Test
    void asksTheDaemonToMoveTheTypeAtThePositionCountedFromOneAsItIsShownAndSendsItFromZero() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("move-type-to-file", "/project/Shapes.java", "6:25"))
                    .isZero();

            assertThat(daemon.requests())
                    .containsExactly(REQUEST.formatted(24, false, Path.of("/project/Shapes.java"), 5));
        }
    }

    @Nested
    class ReturningChangedFiles implements ChangesCommandContract {

        @Override
        public CliTest cli() {
            return MovingATypeToAFileTest.this;
        }

        @Override
        public String commandName() {
            return "move-type-to-file";
        }

        @Override
        public List<String> afterTheFile() {
            return List.of("6:25");
        }

        @Override
        public String request(boolean dryRun, Path file) {
            return REQUEST.formatted(24, dryRun, file, 5);
        }
    }
}
