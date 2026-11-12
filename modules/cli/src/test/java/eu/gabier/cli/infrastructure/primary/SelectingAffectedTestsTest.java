package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.SelectedTests;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SelectingAffectedTestsTest extends CliTest {

    private static String selected(String... tests) {
        return new SelectedTests(List.of(tests)).toJson();
    }

    private static String request(Path... files) {
        return """
            {"jsonrpc":"2.0","id":1,"method":"affected-tests","params":{"files":"%s"}}\
            """.formatted(Stream.of(files).map(Path::toString).collect(Collectors.joining("\\n")));
    }

    @Test
    void asksTheDaemonForTheTestsOfTheFilesInOneCallWithTheirAbsolutePaths() throws IOException {
        try (var daemon = daemonAnswering(selected("demo.LedgerTest"))) {
            assertThat(runWithSocket("affected-tests", "/project/Ledger.java", "/project/Bank.java"))
                    .isZero();

            assertThat(daemon.requests())
                    .containsExactly(request(Path.of("/project/Ledger.java"), Path.of("/project/Bank.java")));
        }
    }

    @Test
    void printsOneLineForEachTestInTheOrderTheDaemonGaveThem() throws IOException {
        try (var daemon = daemonAnswering(selected("demo.LedgerTest", "demo.app.BankTest$Nested"))) {
            assertThat(runWithSocket("affected-tests", "/project/Ledger.java")).isZero();

            assertThat(out().lines()).containsExactly("demo.LedgerTest", "demo.app.BankTest$Nested");
            assertThat(err()).isEmpty();
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void saysThatNoTestIsAffectedAndExitsWithZeroWhenTheListIsEmpty() throws IOException {
        try (var daemon = daemonAnswering(selected())) {
            assertThat(runWithSocket("affected-tests", "/project/Unused.java")).isZero();

            assertThat(out().lines()).containsExactly("no affected tests");
            assertThat(err()).isEmpty();
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void givesTheResultAsJsonAsTheDaemonGaveIt() throws IOException {
        try (var daemon = daemonAnswering(selected("demo.LedgerTest"))) {
            assertThat(runWithSocket("--json", "affected-tests", "/project/Ledger.java"))
                    .isZero();

            assertThat(out()).isEqualTo(selected("demo.LedgerTest") + System.lineSeparator());
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void givesAnEmptyListAsJsonWhenNoTestIsAffected() throws IOException {
        try (var daemon = daemonAnswering(selected())) {
            assertThat(runWithSocket("--json", "affected-tests", "/project/Unused.java"))
                    .isZero();

            assertThat(out()).isEqualTo("{\"tests\":[]}" + System.lineSeparator());
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void showsAnEscapeSequenceOfAClassNameAsItsEscapeSoThatItCannotClearTheTerminal() throws IOException {
        try (var daemon = daemonAnswering(selected("demo.\u001b[2JTest"))) {
            assertThat(runWithSocket("affected-tests", "/project/Ledger.java")).isZero();

            assertThat(out()).isEqualTo("demo.\\u001b[2JTest" + System.lineSeparator());
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void refusesTheDryRunOptionItDoesNotHaveAsAUsageErrorWithFilesAndWithout() throws IOException {
        try (var daemon = daemonAnswering(selected())) {
            assertThat(runWithSocket("affected-tests", "--dry-run")).isEqualTo(2);
            assertThat(err()).contains("usage-error");
            err.reset();
            assertThat(runWithSocket("affected-tests", "--dry-run", "/project/Ledger.java"))
                    .isEqualTo(2);
            assertThat(err()).contains("usage-error");

            assertThat(daemon.requests()).isEmpty();
        }
    }

    @Nested
    class WithNoFile {

        private static final String REQUEST = """
            {"jsonrpc":"2.0","id":1,"method":"affected-tests-changed"}\
            """;

        @Test
        void asksTheDaemonForTheTestsOfWhatChangedSinceItsLastAnswerAndSendsNoParameter() throws IOException {
            try (var daemon = daemonAnswering(selected("demo.LedgerTest"))) {
                assertThat(runWithSocket("affected-tests")).isZero();

                assertThat(daemon.requests()).containsExactly(REQUEST);
            }
        }

        @Test
        void printsOneLineForEachTestAsTheCallWithFilesDoes() throws IOException {
            try (var daemon = daemonAnswering(selected("demo.LedgerTest", "demo.app.BankTest$Nested"))) {
                assertThat(runWithSocket("affected-tests")).isZero();

                assertThat(out().lines()).containsExactly("demo.LedgerTest", "demo.app.BankTest$Nested");
                assertThat(err()).isEmpty();
                assertThat(daemon.requests()).hasSize(1);
            }
        }

        @Test
        void saysThatNoTestIsAffectedAndExitsWithZeroWhenTheListIsEmpty() throws IOException {
            try (var daemon = daemonAnswering(selected())) {
                assertThat(runWithSocket("affected-tests")).isZero();

                assertThat(out().lines()).containsExactly("no affected tests");
                assertThat(err()).isEmpty();
                assertThat(daemon.requests()).hasSize(1);
            }
        }

        @Test
        void givesTheResultAsJsonAsTheDaemonGaveIt() throws IOException {
            try (var daemon = daemonAnswering(selected("demo.LedgerTest"))) {
                assertThat(runWithSocket("--json", "affected-tests")).isZero();

                assertThat(out()).isEqualTo(selected("demo.LedgerTest") + System.lineSeparator());
                assertThat(daemon.requests()).containsExactly(REQUEST);
            }
        }
    }

    @Test
    void refusesAFileNameWithALineBreakAsAUsageErrorBecauseTheDaemonTakesTheFilesOneToALine() throws IOException {
        try (var daemon = daemonAnswering(selected())) {
            assertThat(runWithSocket("affected-tests", "/project/A.java", "/project/B\n.java"))
                    .isEqualTo(2);
            assertThat(runWithSocket("affected-tests", "/project/C\r.java")).isEqualTo(2);

            assertThat(err()).contains("usage-error");
            assertThat(daemon.requests()).isEmpty();
        }
    }
}
