package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.FileDiagnostics;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiagnosticsTest extends CliTest {

    private static final String ERRORS = """
        {"diagnostics":[\
        {"severity":"error","line":4,"column":15,"message":"Type mismatch: cannot convert from String to int"},\
        {"severity":"warning","line":0,"column":0,"message":"A warning"}]}\
        """;
    private static final String CLEAN = "{\"diagnostics\":[]}";

    private static String request(Path file) {
        return """
            {"jsonrpc":"2.0","id":1,"method":"diagnostics","params":{"file":"%s"}}\
            """.formatted(file);
    }

    @Test
    void printsOneLineForEachProblemWithPositionsFromOne() throws IOException {
        try (var daemon = daemonAnswering(ERRORS)) {
            assertThat(runWithSocket("diagnostics", "/project/Broken.java")).isZero();

            assertThat(out().lines())
                    .containsExactly(
                            "/project/Broken.java:5:16: error: Type mismatch: cannot convert from String to int",
                            "/project/Broken.java:1:1: warning: A warning");
            assertThat(err()).isEmpty();
            assertThat(daemon.requests()).containsExactly(request(Path.of("/project/Broken.java")));
        }
    }

    @Test
    void refusesAnEmptyFileNameAsAnInvalidValueInsteadOfReadingTheWorkingDirectory() throws IOException {
        try (var daemon = daemonAnswering(CLEAN)) {
            assertThat(runWithSocket("diagnostics", "")).isEqualTo(2);

            assertThat(out()).isEmpty();
            assertThat(err())
                    .contains("The value file of the operation diagnostics must not be empty")
                    .contains("operation-invalid-values");
            assertThat(daemon.requests()).isEmpty();
        }
    }

    private static String resultOf(String message) {
        return new FileDiagnostics(
                        List.of(new FileDiagnostics.Diagnosis(FileDiagnostics.Severity.ERROR, 0, 0, message)))
                .toJson();
    }

    @Test
    void showsAnEscapeSequenceOfAMessageAsItsEscapeSoThatItCannotClearTheTerminal() throws IOException {
        try (var daemon = daemonAnswering(resultOf("bad \u001b[2J token"))) {
            assertThat(runWithSocket("diagnostics", "/project/A.java")).isZero();

            assertThat(out()).isEqualTo("/project/A.java:1:1: error: bad \\u001b[2J token" + System.lineSeparator());
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void keepsOneLinePerProblemWhenAMessageHoldsALineBreakAndAForgedLine() throws IOException {
        var message = "first\n/project/B.java:1:1: error: forged";
        try (var daemon = daemonAnswering(resultOf(message))) {
            assertThat(runWithSocket("diagnostics", "/project/A.java")).isZero();

            assertThat(out().lines())
                    .containsExactly("/project/A.java:1:1: error: first\\u000a/project/B.java:1:1: error: forged");
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void showsAnEscapeSequenceOfTheFileNameAsItsEscape() throws IOException {
        try (var daemon = daemonAnswering(resultOf("m"))) {
            assertThat(runWithSocket("diagnostics", "/project/a\u001b[2Jb.java"))
                    .isZero();

            assertThat(out()).isEqualTo("/project/a\\u001b[2Jb.java:1:1: error: m" + System.lineSeparator());
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void leavesTheMessageAsItIsInJson() throws IOException {
        var result = resultOf("bad \u001b[2J\nline");
        try (var daemon = daemonAnswering(result)) {
            assertThat(runWithSocket("--json", "diagnostics", "/project/A.java"))
                    .isZero();

            assertThat(out()).isEqualTo(result + System.lineSeparator());
            assertThat(out()).contains("bad \\u001b[2J\\nline");
        }
    }

    @Test
    void printsNothingForACleanFile() throws IOException {
        try (var daemon = daemonAnswering(CLEAN)) {
            assertThat(runWithSocket("diagnostics", "/project/Main.java")).isZero();

            assertThat(out()).isEmpty();
            assertThat(err()).isEmpty();
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void asksTheDaemonForTheAbsolutePathOfARelativeFileAndShowsTheFileAsTyped() throws IOException {
        try (var daemon = daemonAnswering(ERRORS)) {
            assertThat(runWithSocket("diagnostics", "src/Broken.java")).isZero();

            assertThat(out()).startsWith("src/Broken.java:5:16: error: ");
            assertThat(daemon.requests())
                    .containsExactly(request(Path.of("src/Broken.java").toAbsolutePath()));
        }
    }

    @Test
    void doesNotCollapseTheDotDotOfAPathThroughALinkBecauseOnlyTheDaemonCleansPaths() throws IOException {
        var target = Files.createDirectories(directory.resolve("targets/real"));
        Files.createFile(directory.resolve("targets/Foo.java"));
        var link = Files.createSymbolicLink(
                Files.createDirectories(directory.resolve("links")).resolve("src"), target);
        var typed = link.resolve("..").resolve("Foo.java");
        // The shell means the folder that holds the target of the link; the lexical collapse would mean the one
        // that
        // holds the link, where there is no such file.
        assertThat(typed).exists();
        assertThat(directory.resolve("links/Foo.java")).doesNotExist();
        try (var daemon = daemonAnswering(CLEAN)) {
            assertThat(runWithSocket("diagnostics", typed.toString())).isZero();

            assertThat(daemon.requests()).containsExactly(request(typed));
        }
    }

    @Test
    void givesTheResultAsJsonWithTheZeroBasedPositionsWithTheJsonOption() throws IOException {
        try (var daemon = daemonAnswering(ERRORS)) {
            assertThat(runWithSocket("--json", "diagnostics", "/project/Broken.java"))
                    .isZero();

            assertThat(out()).isEqualTo(ERRORS + System.lineSeparator());
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void givesAnEmptyListAsJsonForACleanFile() throws IOException {
        try (var daemon = daemonAnswering(CLEAN)) {
            assertThat(runWithSocket("diagnostics", "/project/Main.java", "--json"))
                    .isZero();

            assertThat(out()).isEqualTo(CLEAN + System.lineSeparator());
            assertThat(daemon.requests()).hasSize(1);
        }
    }
}
