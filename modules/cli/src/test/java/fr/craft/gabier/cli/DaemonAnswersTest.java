package fr.craft.gabier.cli;

import static fr.craft.gabier.cli.CliFixtures.REFUSAL;
import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.protocol.JsonRpc;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** What every command that talks to the daemon does with an answer that is a refusal, or that it cannot read. */
class DaemonAnswersTest extends CliTest {

    static Stream<List<String>> commandsWithAResult() {
        return Stream.of(
                List.of("status"),
                List.of("diagnostics", "/project/Main.java"),
                List.of("rename", "/project/Greeter.java", "6", "18", "welcome"),
                List.of("organize-imports", "/project/Untidy.java"),
                List.of("extract-variable", "/project/Greeter.java", "7:26", "7:48", "joined"),
                List.of("extract-method", "/project/Totals.java", "6:9", "6:47", "printCount"),
                List.of("move-type-to-file", "/project/Shapes.java", "6:25"),
                List.of("inline", "/project/Prices.java", "4:22"),
                List.of("create", "/project/Widget.java", "class"));
    }

    static Stream<List<String>> commandsThatTalkToTheDaemon() {
        return Stream.concat(commandsWithAResult(), Stream.of(List.of("stop")));
    }

    static Stream<List<String>> commandsThatTalkToTheDaemonWithTheJsonOption() {
        return commandsThatTalkToTheDaemon()
                .flatMap(words -> Stream.of(jsonOption(words, true), jsonOption(words, false)));
    }

    private static List<String> jsonOption(List<String> words, boolean first) {
        var withJson = new ArrayList<>(words);
        withJson.add(first ? 0 : withJson.size(), "--json");

        return withJson;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("commandsThatTalkToTheDaemon")
    void reportsARefusalOfTheDaemonAsItsProblemObjectWithTheExitCodeOfItsCategory(List<String> words)
            throws IOException {
        try (var daemon = new ScriptedDaemon(socket(), JsonRpc.failure("1", REFUSAL))) {
            assertThat(runWithSocket(words.toArray(String[]::new))).isEqualTo(2);

            assertThat(out()).isEmpty();
            assertThat(err()).isEqualTo(REFUSAL.toText() + System.lineSeparator());
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("commandsThatTalkToTheDaemonWithTheJsonOption")
    void reportsARefusalOfTheDaemonAsJsonWithTheJsonOption(List<String> withJson) throws IOException {
        try (var daemon = new ScriptedDaemon(socket(), JsonRpc.failure("1", REFUSAL))) {
            assertThat(runWithSocket(withJson.toArray(String[]::new))).isEqualTo(2);

            assertThat(out()).isEmpty();
            assertThat(err()).isEqualTo(REFUSAL.toJson() + System.lineSeparator());
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("commandsWithAResult")
    void reportsAResultThatIsNotWhatTheCommandExpectsAsAnInternalError(List<String> words) throws IOException {
        try (var daemon = daemonAnswering("{}")) {
            assertThat(runWithSocket(words.toArray(String[]::new))).isEqualTo(1);

            assertThat(out()).isEmpty();
            assertThat(err()).contains("(invalid-response)");
            assertThat(daemon.requests()).hasSize(1);
        }
    }
}
