package fr.craft.gabier.cli;

import static fr.craft.gabier.cli.CliFixtures.MODIFIED;
import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.protocol.JsonRpc;
import fr.craft.gabier.protocol.Problem;
import fr.craft.gabier.shared.error.domain.ErrorStatus;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MovingToAPackageTest extends CliTest {

    private static final String REQUEST = """
        {"jsonrpc":"2.0","id":1,"method":"move","params":{"dryRun":"%s","files":"%s","package":"%s"}}\
        """;

    @Test
    void asksTheDaemonToMoveTheFilesToThePackageNamedLastSendingTheAbsolutePathsOnePerLine() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("move", "/project/Invoice.java", "src/Payment.java", "demo.billing"))
                    .isZero();

            assertThat(daemon.requests())
                    .containsExactly(REQUEST.formatted(
                            false,
                            Path.of("/project/Invoice.java") + "\\n"
                                    + Path.of("src/Payment.java").toAbsolutePath(),
                            "demo.billing"));
        }
    }

    @Test
    void takesTheDryRunOptionBetweenTheFilesToo() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("move", "/project/A.java", "--dry-run", "/project/B.java", "demo"))
                    .isZero();

            assertThat(daemon.requests())
                    .containsExactly(REQUEST.formatted(true, "/project/A.java\\n/project/B.java", "demo"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"A\nB.java", "A\rB.java", "A.java\n", "\nA.java", "A.java\r"})
    void refusesAFileNameWithALineBreakAsAUsageErrorThatShowsItAsAnEscapeAndAsksNothing(String file)
            throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("move", file, "demo")).isEqualTo(2);

            assertThat(out()).isEmpty();
            assertThat(err())
                    .startsWith("Usage error: Unsupported arguments;")
                    .contains("arguments=move " + file.replace("\n", "\\u000a").replace("\r", "\\u000d") + " demo)");
            assertThat(daemon.requests()).isEmpty();
        }
    }

    @Test
    void showsWhatATerminalWouldObeyInTheRefusedPackageNameAsAnEscape() throws IOException {
        var refusal = new Problem(
                "urn:gabier:error:editing-not-a-package-name",
                "Not a package name",
                "Not a package name",
                "editing-not-a-package-name",
                Map.of("package", "demo.\u001b[2Jx"),
                ErrorStatus.BAD_REQUEST);
        try (var daemon = new ScriptedDaemon(socket(), JsonRpc.failure("1", refusal))) {
            assertThat(runWithSocket("move", "/project/A.java", "demo.\u001b[2Jx"))
                    .isEqualTo(2);

            assertThat(err()).contains("package=demo.\\u001b[2Jx").doesNotContain("\u001b");
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Nested
    class ReturningChangedFiles implements ChangesCommandContract {

        @Override
        public CliTest cli() {
            return MovingToAPackageTest.this;
        }

        @Override
        public String commandName() {
            return "move";
        }

        @Override
        public List<String> afterTheFile() {
            return List.of("demo.billing");
        }

        @Override
        public String request(boolean dryRun, Path file) {
            return REQUEST.formatted(dryRun, file, "demo.billing");
        }
    }
}
