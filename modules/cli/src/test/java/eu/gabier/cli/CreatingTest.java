package eu.gabier.cli;

import static eu.gabier.cli.CliFixtures.MODIFIED;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CreatingTest extends CliTest {

    private static final String REQUEST = """
        {"jsonrpc":"2.0","id":1,"method":"create","params":{"dryRun":"%s","file":"%s","kind":"%s"}}\
        """;

    @Test
    void asksTheDaemonToCreateTheTypeOfTheKindInTheFile() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("create", "/project/Widget.java", "record"))
                    .isZero();

            assertThat(daemon.requests())
                    .containsExactly(REQUEST.formatted(false, Path.of("/project/Widget.java"), "record"));
        }
    }

    @Test
    void sendsTheKindAsItIsTypedAndLeavesTheDaemonToRefuseAWordThatIsNotOneOfTheFour() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("create", "/project/Widget.java", "annotation"))
                    .isZero();

            assertThat(daemon.requests())
                    .containsExactly(REQUEST.formatted(false, Path.of("/project/Widget.java"), "annotation"));
        }
    }

    @Nested
    class ReturningChangedFiles implements ChangesCommandContract {

        @Override
        public CliTest cli() {
            return CreatingTest.this;
        }

        @Override
        public String commandName() {
            return "create";
        }

        @Override
        public List<String> afterTheFile() {
            return List.of("class");
        }

        @Override
        public String request(boolean dryRun, Path file) {
            return REQUEST.formatted(dryRun, file, "class");
        }
    }
}
