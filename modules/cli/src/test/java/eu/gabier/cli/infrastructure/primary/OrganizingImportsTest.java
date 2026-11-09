package eu.gabier.cli.infrastructure.primary;

import static eu.gabier.cli.infrastructure.primary.CliFixtures.MODIFIED;
import static eu.gabier.cli.infrastructure.primary.CliFixtures.NOTHING;
import static eu.gabier.cli.infrastructure.primary.CliFixtures.WARNINGS;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrganizingImportsTest extends CliTest {

    private static final String REQUEST = """
        {"jsonrpc":"2.0","id":1,"method":"organize-imports","params":{"dryRun":"%s","file":"%s"}}\
        """;

    @Test
    void asksTheDaemonToOrganizeTheImportsOfTheFile() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("organize-imports", "/project/Untidy.java"))
                    .isZero();

            assertThat(daemon.requests()).containsExactly(REQUEST.formatted(false, Path.of("/project/Untidy.java")));
        }
    }

    @Test
    void saysThatThereIsNothingToChangeForAFileWhoseImportsAreInOrder() throws IOException {
        try (var daemon = daemonAnswering(NOTHING.toJson())) {
            assertThat(runWithSocket("organize-imports", "/project/Tidy.java")).isZero();

            assertThat(out().lines()).containsExactly("nothing to change");
            assertThat(err()).isEmpty();
        }
    }

    @Test
    void saysBothThatItIsADryRunAndThatThereIsNothingToChange() throws IOException {
        try (var daemon = daemonAnswering(NOTHING.toJson())) {
            assertThat(runWithSocket("organize-imports", "--dry-run", "/project/Tidy.java"))
                    .isZero();

            assertThat(out().lines()).containsExactly("dry run, nothing written", "nothing to change");
        }
    }

    @Test
    void saysTheWarningsOfTheDaemonBeforeTheFilesItChangedWithTheirControlCharactersEscaped() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.withWarnings(WARNINGS).toJson())) {
            assertThat(runWithSocket("organize-imports", "/project/Untidy.java"))
                    .isZero();

            assertThat(out().lines())
                    .startsWith(
                            "warning: java.completion.importOrder: .settings/org.eclipse.jdt.ui.prefs says"
                                    + " [\"#\", \"\"] and .vscode/settings.json says [\"java\"]: following .settings",
                            "warning: ignored .vscode/settings.json: \\u001b[2Jforged",
                            "modified /project/Greeter.java");
            assertThat(err()).isEmpty();
        }
    }

    @Test
    void saysTheWarningsOfTheDryRunAfterSayingThatNothingWasWritten() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.withWarnings(WARNINGS).toJson())) {
            assertThat(runWithSocket("organize-imports", "--dry-run", "/project/Untidy.java"))
                    .isZero();

            var lines = out().lines().toList();
            assertThat(lines.getFirst()).isEqualTo("dry run, nothing written");
            assertThat(lines.get(1)).startsWith("warning: java.completion.importOrder");
        }
    }

    @Test
    void saysTheWarningsAlongWithThereBeingNothingToChange() throws IOException {
        try (var daemon = daemonAnswering(NOTHING.withWarnings(WARNINGS).toJson())) {
            assertThat(runWithSocket("organize-imports", "/project/Tidy.java")).isZero();

            assertThat(out().lines())
                    .hasSize(3)
                    .endsWith("nothing to change")
                    .first()
                    .asString()
                    .startsWith("warning: ");
        }
    }

    @Test
    void givesTheWarningsExactlyAsJsonWithTheJsonOption() throws IOException {
        var answer = MODIFIED.withWarnings(WARNINGS).toJson();
        try (var daemon = daemonAnswering(answer)) {
            assertThat(runWithSocket("--json", "organize-imports", "/project/Untidy.java"))
                    .isZero();

            assertThat(out()).isEqualTo(answer + System.lineSeparator());
        }
    }

    @Nested
    class ReturningChangedFiles implements ChangesCommandContract {

        @Override
        public CliTest cli() {
            return OrganizingImportsTest.this;
        }

        @Override
        public String commandName() {
            return "organize-imports";
        }

        @Override
        public List<String> afterTheFile() {
            return List.of();
        }

        @Override
        public String request(boolean dryRun, Path file) {
            return REQUEST.formatted(dryRun, file);
        }
    }
}
