package fr.craft.gabier.cli;

import static fr.craft.gabier.cli.CliFixtures.HOSTILE;
import static fr.craft.gabier.cli.CliFixtures.IN_EVERY_WAY;
import static fr.craft.gabier.cli.CliFixtures.MODIFIED;
import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.protocol.ChangedFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What every command that returns changed files does the same way, written once. A command gives its name, what
 * follows the file on its command line and the request the daemon must get, and inherits the tests.
 */
interface ChangesCommandContract {

    String FILE = "/project/Greeter.java";

    /** The test whose folder, socket and output the contract uses. */
    CliTest cli();

    /** The word that names the command on the command line. */
    String commandName();

    /** What follows the file on the command line: the positions and the name; none for a command of the file alone. */
    List<String> afterTheFile();

    /** The request the daemon must get for this file, once the command line is read. */
    String request(boolean dryRun, Path file);

    @Test
    default void sendsTheAbsolutePathOfARelativeFile() throws IOException {
        try (var daemon = cli().daemonAnswering(MODIFIED.toJson())) {
            assertThat(cli().runWithSocket(commandLine("src/Greeter.java"))).isZero();

            assertThat(daemon.requests())
                    .containsExactly(request(false, Path.of("src/Greeter.java").toAbsolutePath()));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    default void asksForADryRunWithTheDryRunOptionWhereverItIsAndSaysThatNothingWasWritten(boolean firstOption)
            throws IOException {
        var words = new ArrayList<>(List.of(commandLine(FILE)));
        words.add(firstOption ? 1 : words.size(), "--dry-run");
        try (var daemon = cli().daemonAnswering(MODIFIED.toJson())) {
            assertThat(cli().runWithSocket(words.toArray(String[]::new))).isZero();

            assertThat(daemon.requests()).containsExactly(request(true, Path.of(FILE)));
            assertThat(cli().out().lines()).startsWith("dry run, nothing written", "modified /project/Greeter.java");
        }
    }

    @Test
    default void listsTheFilesThatChangedThenShowsTheDiff() throws IOException {
        try (var daemon = cli().daemonAnswering(MODIFIED.toJson())) {
            assertThat(cli().runWithSocket(commandLine(FILE))).isZero();

            assertThat(cli().out().lines())
                    .containsExactlyElementsOf("""
                        modified /project/Greeter.java
                        modified /project/Main.java

                        --- /project/Greeter.java
                        +++ /project/Greeter.java
                        @@ -1 +1 @@
                        -    public String greet(String name) {
                        +    public String welcome(String name) {
                        --- /project/Main.java
                        +++ /project/Main.java
                        @@ -1 +1 @@
                        -        System.out.println(greeter.greet("world"));
                        +        System.out.println(greeter.welcome("world"));
                        """.lines().toList());
            assertThat(cli().err()).isEmpty();
        }
    }

    @Test
    default void listsTheFilesCreatedMovedAndDeletedToo() throws IOException {
        try (var daemon = cli().daemonAnswering(IN_EVERY_WAY.toJson())) {
            assertThat(cli().runWithSocket(commandLine(FILE))).isZero();

            assertThat(cli().out().lines())
                    .containsExactly(
                            "modified /project/Main.java",
                            "created /project/New.java",
                            "renamed /project/Greeter.java -> /project/Welcomer.java",
                            "deleted /project/Gone.java",
                            "",
                            "--- /project/Main.java",
                            "+++ /project/Main.java");
        }
    }

    @Test
    default void showsWhatATerminalWouldObeyInAFileNameOrADiffAsEscapes() throws IOException {
        try (var daemon = cli().daemonAnswering(HOSTILE.toJson())) {
            assertThat(cli().runWithSocket(commandLine(FILE))).isZero();

            assertThat(cli().out().lines())
                    .containsExactly(
                            "modified /project/a\\u001b[2Jb.java",
                            "",
                            "--- /project/a\\u001b[2Jb.java",
                            "+forged\\u001b[2J");
        }
    }

    @ParameterizedTest
    @MethodSource("fr.craft.gabier.cli.CliFixtures#answers")
    default void givesTheResultAsJsonWithTheJsonOptionWhateverItHolds(ChangedFiles answer) throws IOException {
        var words = new ArrayList<>(List.of(commandLine(FILE)));
        words.addFirst("--json");
        try (var daemon = cli().daemonAnswering(answer.toJson())) {
            assertThat(cli().runWithSocket(words.toArray(String[]::new))).isZero();

            assertThat(cli().out()).isEqualTo(answer.toJson() + System.lineSeparator());
            assertThat(cli().err()).isEmpty();
        }
    }

    /** The command line for this file: the command, the file, then what follows it. */
    default String[] commandLine(String file) {
        return Stream.concat(Stream.of(commandName(), file), afterTheFile().stream())
                .toArray(String[]::new);
    }
}
