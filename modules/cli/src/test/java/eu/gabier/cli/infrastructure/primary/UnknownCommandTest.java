package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.cli.Gabier;
import eu.gabier.protocol.Json;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class UnknownCommandTest {

    private static final String FOR_A_TASK = CliTest.FOR_A_TASK;
    private static final String HOW_TO_HELP =
            "Run gabier help for the commands, and gabier help <command> for the usage of one";

    @TempDir
    Path directory;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(Map<String, String> environment, String... args) {
        return Gabier.run(
                args,
                environment,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private String err() {
        return err.toString(StandardCharsets.UTF_8);
    }

    @ParameterizedTest(name = "{2}: {0} means {1}")
    @CsvSource({
        "organise-imports, organize-imports, one letter changed",
        "renane, rename, one letter changed",
        "diagnosstics, diagnostics, one letter added",
        "extract-variablee, extract-variable, one letter added at the end",
        "extract-varible, extract-variable, one letter removed",
        "diagnostic, diagnostics, one letter removed at the end",
        "reanme, rename, two letters swapped",
        "extract-metohd, extract-method, two letters swapped",
        "stpo, stop, two letters swapped"
    })
    void aCommandWithOneTypoIsRefusedNamingTheRightOne(String typed, String meant, String kind) {
        assertThat(run(Map.of(), typed, "x.java")).as(kind).isEqualTo(2);

        assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(err())
                .isEqualTo("Usage error: Unsupported arguments; did you mean " + meant + "? " + HOW_TO_HELP
                        + " (usage-error; arguments=" + typed + " x.java, suggestion=" + meant + ")"
                        + System.lineSeparator() + FOR_A_TASK + System.lineSeparator());
    }

    @ParameterizedTest
    @CsvSource({"organise-imports, organize-imports", "reanme, rename", "extract-varible, extract-variable"})
    void withJsonTheSuggestionIsAFieldOfTheProblem(String typed, String meant) {
        assertThat(run(Map.of(), "--json", typed, "x.java")).isEqualTo(2);

        var problem = (Map<?, ?>) Json.parse(err());
        assertThat(problem.get("key")).isEqualTo("usage-error");
        assertThat(problem.get("detail"))
                .isEqualTo("Unsupported arguments; did you mean " + meant + "? " + HOW_TO_HELP);
        assertThat((Map<?, ?>) problem.get("parameters"))
                .isEqualTo(Map.of("arguments", typed + " x.java", "suggestion", meant));
        assertThat(problem.get("help")).isEqualTo(FOR_A_TASK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"frobnicate", "remove", "organise-the-imports", "x", "--bogus", "A.java", "renamed-all"})
    void aWordFarFromEveryCommandGivesTheUsageErrorWithoutASuggestion(String typed) {
        assertThat(run(Map.of(), typed)).isEqualTo(2);

        assertThat(err())
                .isEqualTo("Usage error: Unsupported arguments; run gabier help for the commands, "
                        + "and gabier help <command> for the usage of one (usage-error; arguments=" + typed + ")"
                        + System.lineSeparator() + FOR_A_TASK + System.lineSeparator());
    }

    @Test
    void aWordFarFromEveryCommandAsJsonHasNoSuggestionField() {
        assertThat(run(Map.of(), "--json", "frobnicate")).isEqualTo(2);

        var problem = (Map<?, ?>) Json.parse(err());
        assertThat((Map<?, ?>) problem.get("parameters")).isEqualTo(Map.of("arguments", "frobnicate"));
        assertThat(problem.get("help")).isEqualTo(FOR_A_TASK);
    }

    @Test
    void onlyTheFirstWordIsTakenForTheCommand() {
        assertThat(run(Map.of(), "x.java", "rename")).isEqualTo(2);

        assertThat(err()).doesNotContain("did you mean");
    }

    @Test
    void aTypoNeverStartsADaemonNorOpensTheSocket() throws IOException {
        var marker = directory.resolve("started");
        var bin = directory.resolve("bin");
        FakeDaemonScripts.install(bin, "echo started > '" + marker + "'");
        var socket = directory.resolve("gabier.sock");
        var environment = Map.of("GABIER_SOCKET", socket.toString(), "PATH", bin.toString());

        assertThat(run(environment, "diagnostcs", "x.java")).isEqualTo(2);
        assertThat(run(environment, "organise-imports", "x.java")).isEqualTo(2);

        assertThat(marker).doesNotExist();
        assertThat(socket).doesNotExist();
    }
}
