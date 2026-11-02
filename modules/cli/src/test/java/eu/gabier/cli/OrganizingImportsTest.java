package eu.gabier.cli;

import static eu.gabier.cli.CliFixtures.MODIFIED;
import static eu.gabier.cli.CliFixtures.NOTHING;
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
