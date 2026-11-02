package eu.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.CodeActions;
import eu.gabier.protocol.CodeActions.Action;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListingActionsTest extends CliTest {

    private static final Action IMPORT = new Action("quickfix", "Import 'List' (java.util)");
    private static final Action CREATE = new Action("quickfix", "Create class 'List<T>'");
    private static final Action TEXT_BLOCK = new Action("quickassist", "Convert String concatenation to Text Block");

    private static String offered(Action... actions) {
        return new CodeActions(List.of(actions)).toJson();
    }

    private static String request(Path file) {
        return """
            {"jsonrpc":"2.0","id":1,"method":"actions","params":{"endColumn":"8","endLine":"3","file":"%s",\
            "startColumn":"4","startLine":"3"}}\
            """.formatted(file);
    }

    @Test
    void asksTheDaemonForTheActionsOfTheRangeCountedFromOneAsItIsShownAndSendsItFromZero() throws IOException {
        try (var daemon = daemonAnswering(offered(IMPORT))) {
            assertThat(runWithSocket("actions", "/project/Offers.java", "4:5", "4:9"))
                    .isZero();

            assertThat(daemon.requests()).containsExactly(request(Path.of("/project/Offers.java")));
        }
    }

    @Test
    void printsOneLineForEachActionWithItsNumberItsKindAndItsTitleInTheOrderOfTheServer() throws IOException {
        try (var daemon = daemonAnswering(offered(IMPORT, CREATE, TEXT_BLOCK))) {
            assertThat(runWithSocket("actions", "/project/Offers.java", "4:5", "4:9"))
                    .isZero();

            assertThat(out().lines())
                    .containsExactly(
                            "1 quickfix Import 'List' (java.util)",
                            "2 quickfix Create class 'List<T>'",
                            "3 quickassist Convert String concatenation to Text Block");
            assertThat(err()).isEmpty();
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void saysThatThereIsNoActionAndExitsWithZeroWhereNothingIsOffered() throws IOException {
        try (var daemon = daemonAnswering(offered())) {
            assertThat(runWithSocket("actions", "/project/Offers.java", "7:1", "7:1"))
                    .isZero();

            assertThat(out().lines()).containsExactly("no actions");
            assertThat(err()).isEmpty();
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void writesADashForAnActionTheServerGaveNoKind() throws IOException {
        try (var daemon = daemonAnswering(offered(new Action("", "Without a kind")))) {
            assertThat(runWithSocket("actions", "/project/Offers.java", "4:5", "4:9"))
                    .isZero();

            assertThat(out().lines()).containsExactly("1 - Without a kind");
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void showsAnEscapeSequenceOfATitleAsItsEscapeSoThatItCannotClearTheTerminal() throws IOException {
        try (var daemon = daemonAnswering(offered(new Action("quickfix", "Fix \u001b[2J \u009b0m end")))) {
            assertThat(runWithSocket("actions", "/project/Offers.java", "4:5", "4:9"))
                    .isZero();

            assertThat(out()).isEqualTo("1 quickfix Fix \\u001b[2J \\u009b0m end" + System.lineSeparator());
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void keepsOneLinePerActionWhenATitleHoldsALineBreakCharacter() throws IOException {
        try (var daemon = daemonAnswering(offered(new Action("quickfix", "a\tb\r\u0085c")))) {
            assertThat(runWithSocket("actions", "/project/Offers.java", "4:5", "4:9"))
                    .isZero();

            assertThat(out().lines()).hasSize(1);
            assertThat(out()).contains("a\\u0009b\\u000d\\u0085c");
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void givesTheResultAsItIsWithTheJsonOption() throws IOException {
        var answer = offered(IMPORT, TEXT_BLOCK);
        try (var daemon = daemonAnswering(answer)) {
            assertThat(runWithSocket("--json", "actions", "/project/Offers.java", "4:5", "4:9"))
                    .isZero();

            assertThat(out()).isEqualTo(answer + System.lineSeparator());
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void givesAnEmptyListAsJsonWhereNothingIsOffered() throws IOException {
        try (var daemon = daemonAnswering(offered())) {
            assertThat(runWithSocket("actions", "/project/Offers.java", "4:5", "4:9", "--json"))
                    .isZero();

            assertThat(out()).isEqualTo("{\"actions\":[]}" + System.lineSeparator());
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void asksTheDaemonForTheAbsolutePathOfARelativeFile() throws IOException {
        try (var daemon = daemonAnswering(offered())) {
            assertThat(runWithSocket("actions", "src/Offers.java", "4:5", "4:9"))
                    .isZero();

            assertThat(daemon.requests())
                    .containsExactly(request(Path.of("src/Offers.java").toAbsolutePath()));
        }
    }
}
