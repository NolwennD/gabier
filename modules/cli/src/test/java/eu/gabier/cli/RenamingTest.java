package eu.gabier.cli;

import static eu.gabier.cli.CliFixtures.MODIFIED;
import static eu.gabier.cli.CliFixtures.NOTHING;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RenamingTest extends CliTest {

    private static final String BY_POSITION = """
        {"jsonrpc":"2.0","id":1,"method":"rename","params":{"column":"17","dryRun":"%s","file":"%s",\
        "line":"5","newName":"welcome"}}\
        """;
    private static final String BY_TOKEN = """
        {"jsonrpc":"2.0","id":1,"method":"rename-token","params":{"dryRun":"%s","file":"%s",\
        "newName":"welcome","token":"greet"}}\
        """;

    @Test
    void asksTheDaemonToRenameTheSymbolAtAPositionGivenFromOneAndSendsItFromZero() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("rename", "/project/Greeter.java", "6:18", "welcome"))
                    .isZero();

            assertThat(daemon.requests())
                    .containsExactly(BY_POSITION.formatted(false, Path.of("/project/Greeter.java")));
        }
    }

    @Test
    void asksTheDaemonToRenameTheSymbolAnIdentifierNamesWithTheTokenOption() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("rename", "--token", "/project/Greeter.java", "greet", "welcome"))
                    .isZero();

            assertThat(daemon.requests()).containsExactly(BY_TOKEN.formatted(false, Path.of("/project/Greeter.java")));
        }
    }

    @Test
    void asksForADryRunByTokenWithTheTwoOptionsInAnyOrder() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("rename", "--token", "--dry-run", "/project/Greeter.java", "greet", "welcome"))
                    .isZero();

            assertThat(daemon.requests()).containsExactly(BY_TOKEN.formatted(true, Path.of("/project/Greeter.java")));
        }
    }

    @Test
    void printsNothingWhenNothingChanged() throws IOException {
        try (var daemon = daemonAnswering(NOTHING.toJson())) {
            assertThat(runWithSocket("rename", "/project/Greeter.java", "6:18", "welcome"))
                    .isZero();

            assertThat(out()).isEmpty();
        }
    }

    @Test
    void givesTheResultAsJsonWithTheJsonOptionForARenameByToken() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("--json", "rename", "--token", "/project/Greeter.java", "greet", "welcome"))
                    .isZero();

            assertThat(out()).isEqualTo(MODIFIED.toJson() + System.lineSeparator());
            assertThat(err()).isEmpty();
        }
    }

    @Nested
    class ReturningChangedFiles implements ChangesCommandContract {

        @Override
        public CliTest cli() {
            return RenamingTest.this;
        }

        @Override
        public String commandName() {
            return "rename";
        }

        @Override
        public List<String> afterTheFile() {
            return List.of("6:18", "welcome");
        }

        @Override
        public String request(boolean dryRun, Path file) {
            return BY_POSITION.formatted(dryRun, file);
        }
    }
}
