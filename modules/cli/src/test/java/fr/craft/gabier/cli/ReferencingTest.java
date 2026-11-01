package fr.craft.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.protocol.FoundReferences;
import fr.craft.gabier.protocol.FoundReferences.Place;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ReferencingTest extends CliTest {

    private static final Place DECLARATION =
            new Place("/project/Ledger.java", 9, 16, "    public void deposit(int amount) {");
    private static final Place IN_BANK = new Place("/project/Bank.java", 7, 15, "\t\tledger.deposit(10);");
    private static final Place IN_TEST = new Place("/project/LedgerTest.java", 5, 15, "  ledger.deposit(20);  ");

    private static String found(List<Place> declarations, List<Place> references) {
        return new FoundReferences(declarations, references).toJson();
    }

    private static String request(Path file, int line, int column, boolean includeDeclaration) {
        return """
            {"jsonrpc":"2.0","id":1,"method":"references","params":{"column":"%d","file":"%s",\
            "includeDeclaration":"%s","line":"%d"}}\
            """.formatted(column, file, includeDeclaration, line);
    }

    @Test
    void asksTheDaemonForTheReferencesOfTheSymbolAtAPositionGivenFromOneAndSendsItFromZero() throws IOException {
        try (var daemon = daemonAnswering(found(List.of(), List.of(IN_BANK)))) {
            assertThat(runWithSocket("references", "/project/Ledger.java", "10:17"))
                    .isZero();

            assertThat(daemon.requests()).containsExactly(request(Path.of("/project/Ledger.java"), 9, 16, false));
        }
    }

    @Test
    void printsOneLineForEachReferenceWithItsPositionFromOneAndItsSourceLineWithoutItsIndentation() throws IOException {
        try (var daemon = daemonAnswering(found(List.of(), List.of(IN_BANK, IN_TEST)))) {
            assertThat(runWithSocket("references", "/project/Ledger.java", "10:17"))
                    .isZero();

            assertThat(out().lines())
                    .containsExactly(
                            "/project/Bank.java:8:16: ledger.deposit(10);",
                            "/project/LedgerTest.java:6:16: ledger.deposit(20);");
            assertThat(err()).isEmpty();
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    static Stream<List<String>> optionPlaces() {
        return Stream.of(
                List.of("references", "--include-declaration", "/project/Ledger.java", "10:17"),
                List.of("references", "/project/Ledger.java", "--include-declaration", "10:17"),
                List.of("references", "/project/Ledger.java", "10:17", "--include-declaration"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("optionPlaces")
    void asksForTheDeclarationWithTheOptionWhereverItStandsAndListsItApart(List<String> words) throws IOException {
        try (var daemon = daemonAnswering(found(List.of(DECLARATION), List.of(IN_BANK)))) {
            assertThat(runWithSocket(words.toArray(String[]::new))).isZero();

            assertThat(out().lines())
                    .containsExactly(
                            "declaration:",
                            "/project/Ledger.java:10:17: public void deposit(int amount) {",
                            "references:",
                            "/project/Bank.java:8:16: ledger.deposit(10);");
            assertThat(daemon.requests()).containsExactly(request(Path.of("/project/Ledger.java"), 9, 16, true));
        }
    }

    @Test
    void saysThatThereIsNoReferenceAndExitsWithZeroForASymbolNothingUses() throws IOException {
        try (var daemon = daemonAnswering(found(List.of(), List.of()))) {
            assertThat(runWithSocket("references", "/project/Ledger.java", "10:17"))
                    .isZero();

            assertThat(out().lines()).containsExactly("no references");
            assertThat(err()).isEmpty();
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void saysThatThereIsNoReferenceAfterTheDeclarationWhenOnlyTheDeclarationWasFound() throws IOException {
        try (var daemon = daemonAnswering(found(List.of(DECLARATION), List.of()))) {
            assertThat(runWithSocket("references", "--include-declaration", "/project/Ledger.java", "10:17"))
                    .isZero();

            assertThat(out().lines())
                    .containsExactly(
                            "declaration:",
                            "/project/Ledger.java:10:17: public void deposit(int amount) {",
                            "no references");
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void showsAnEscapeSequenceOfASourceLineAsItsEscapeSoThatItCannotClearTheTerminal() throws IOException {
        var hostile = new Place("/project/Bank.java", 0, 0, "x(); // \u001b[2J \u009b0m end");
        try (var daemon = daemonAnswering(found(List.of(), List.of(hostile)))) {
            assertThat(runWithSocket("references", "/project/Ledger.java", "1:1"))
                    .isZero();

            assertThat(out())
                    .isEqualTo("/project/Bank.java:1:1: x(); // \\u001b[2J \\u009b0m end" + System.lineSeparator());
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void showsAnEscapeSequenceOfAFileNameAsItsEscape() throws IOException {
        var hostile = new Place("/project/a\u001b[2Jb.java", 0, 0, "x();");
        try (var daemon = daemonAnswering(found(List.of(), List.of(hostile)))) {
            assertThat(runWithSocket("references", "/project/Ledger.java", "1:1"))
                    .isZero();

            assertThat(out()).isEqualTo("/project/a\\u001b[2Jb.java:1:1: x();" + System.lineSeparator());
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void keepsOneLinePerReferenceWhenASourceLineHoldsATabOrALineBreakCharacter() throws IOException {
        var inside = new Place("/project/Bank.java", 0, 0, "a\tb\r\u0085c");
        try (var daemon = daemonAnswering(found(List.of(), List.of(inside)))) {
            assertThat(runWithSocket("references", "/project/Ledger.java", "1:1"))
                    .isZero();

            assertThat(out().lines()).hasSize(1);
            assertThat(out()).contains("a\\u0009b\\u000d\\u0085c");
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void givesTheResultAsItIsWithTheZeroBasedPositionsAndTheExactSourceLinesWithTheJsonOption() throws IOException {
        var answer = found(List.of(DECLARATION), List.of(IN_BANK, IN_TEST));
        try (var daemon = daemonAnswering(answer)) {
            assertThat(runWithSocket("--json", "references", "--include-declaration", "/project/Ledger.java", "10:17"))
                    .isZero();

            assertThat(out()).isEqualTo(answer + System.lineSeparator());
            assertThat(out()).contains("\"source\":\"\\t\\tledger.deposit(10);\"");
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void givesTwoEmptyListsAsJsonForASymbolNothingUses() throws IOException {
        try (var daemon = daemonAnswering(found(List.of(), List.of()))) {
            assertThat(runWithSocket("references", "/project/Ledger.java", "10:17", "--json"))
                    .isZero();

            assertThat(out()).isEqualTo("{\"declarations\":[],\"references\":[]}" + System.lineSeparator());
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void asksTheDaemonForTheAbsolutePathOfARelativeFile() throws IOException {
        try (var daemon = daemonAnswering(found(List.of(), List.of()))) {
            assertThat(runWithSocket("references", "src/Ledger.java", "1:1")).isZero();

            assertThat(daemon.requests())
                    .containsExactly(request(Path.of("src/Ledger.java").toAbsolutePath(), 0, 0, false));
        }
    }
}
