package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.JsonRpc;
import eu.gabier.protocol.Problem;
import eu.gabier.shared.error.domain.ErrorStatus;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A refusal of the daemon where the language server declined the operation ends with the line that points to the help
 * of the command, which lists the other ways; the key, the problem and the exit code are those of the daemon.
 */
class PointingToHelpTest extends CliTest {

    static Stream<Arguments> declinedOperations() {
        return Stream.of(
                Arguments.of(
                        List.of("rename", "/project/Greeter.java", "6:18", "welcome"),
                        "language-server-no-such-symbol",
                        "rename"),
                Arguments.of(
                        List.of("extract-method", "/project/Totals.java", "6:9", "6:47", "printCount"),
                        "language-server-not-extractable",
                        "extract-method"),
                Arguments.of(
                        List.of("move-type-to-file", "/project/Shapes.java", "6:25"),
                        "language-server-not-movable",
                        "move-type-to-file"),
                Arguments.of(
                        List.of("inline", "/project/Prices.java", "4:22"), "language-server-not-inlinable", "inline"),
                Arguments.of(
                        List.of("create", "/project/Widget.java", "class"), "language-server-not-creatable", "create"),
                Arguments.of(
                        List.of("apply-action", "/project/Offers.java", "4:5", "4:9", "Import 'List' (java.util)"),
                        "editing-no-such-code-action",
                        "apply-action"),
                Arguments.of(
                        List.of("cleanup", "/project/Banner.java", "stringConcatToTextBlock"),
                        "editing-unknown-clean-up",
                        "cleanup"));
    }

    static Stream<String> keysThatAreNotADeclinedOperation() {
        return Stream.of(
                "language-server-not-ready",
                "language-server-file-outside-project",
                "editing-stale-edit",
                "jdtls-start-failed",
                "no-build-to-import",
                "usage-error",
                "some-key");
    }

    private static Problem refusal(String key) {
        return new Problem(
                "urn:gabier:error:" + key,
                "Title of " + key,
                "It went wrong",
                key,
                Map.of("a", "1"),
                ErrorStatus.BAD_REQUEST);
    }

    private static String pointerTo(String command) {
        return "Usage and other ways: gabier help " + command;
    }

    @ParameterizedTest(name = "{2}: {1}")
    @MethodSource("declinedOperations")
    void aDeclinedOperationEndsWithThePointerToTheHelpOfTheCommand(List<String> words, String key, String command)
            throws IOException {
        var problem = refusal(key);
        try (var daemon = new ScriptedDaemon(socket(), JsonRpc.failure("1", problem))) {
            assertThat(runWithSocket(words.toArray(String[]::new))).isEqualTo(2);

            assertThat(out()).isEmpty();
            assertThat(err())
                    .isEqualTo(problem.toText() + System.lineSeparator() + pointerTo(command) + System.lineSeparator());
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @ParameterizedTest(name = "{2}: {1}")
    @MethodSource("declinedOperations")
    void withJsonThePointerIsAFieldOfTheProblemObject(List<String> words, String key, String command)
            throws IOException {
        var problem = refusal(key);
        try (var daemon = new ScriptedDaemon(socket(), JsonRpc.failure("1", problem))) {
            var withJson = Stream.concat(Stream.of("--json"), words.stream()).toArray(String[]::new);

            assertThat(runWithSocket(withJson)).isEqualTo(2);

            assertThat(out()).isEmpty();
            assertThat(err()).isEqualTo(problem.toJson(pointerTo(command)) + System.lineSeparator());
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("keysThatAreNotADeclinedOperation")
    void aRefusalThatIsNotADeclinedOperationHasNoPointer(String key) throws IOException {
        var problem = refusal(key);
        try (var daemon = new ScriptedDaemon(socket(), JsonRpc.failure("1", problem))) {
            assertThat(runWithSocket("rename", "/project/Greeter.java", "6:18", "welcome"))
                    .isEqualTo(2);
            assertThat(err()).isEqualTo(problem.toText() + System.lineSeparator());

            err.reset();
            assertThat(runWithSocket("--json", "rename", "/project/Greeter.java", "6:18", "welcome"))
                    .isEqualTo(2);
            assertThat(err()).isEqualTo(problem.toJson() + System.lineSeparator());
        }
    }
}
