package fr.craft.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class HelpFlagTest {

    @TempDir
    Path directory;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(Map<String, String> environment, String... args) {
        return Gabier.run(args, environment, printStream(out), printStream(err));
    }

    private static PrintStream printStream(ByteArrayOutputStream stream) {
        return new PrintStream(stream, true, StandardCharsets.UTF_8);
    }

    /** What the run prints on the standard output; it must exit 0 and print nothing on the standard error. */
    private String printedBy(String... args) {
        return printedBy(Map.of(), args);
    }

    private String printedBy(Map<String, String> environment, String... args) {
        out.reset();
        err.reset();
        var description = String.join(" ", args);

        assertThat(run(environment, args)).as(description).isZero();
        assertThat(err.toString(StandardCharsets.UTF_8)).as(description).isEmpty();

        return out.toString(StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @EnumSource(Command.class)
    void theLongFlagGivesTheSameTextAsHelpOfTheCommand(Command command) {
        assertThat(printedBy(command.commandName(), "--help")).isEqualTo(printedBy("help", command.commandName()));
    }

    @ParameterizedTest
    @EnumSource(Command.class)
    void theShortFlagGivesTheSameTextAsHelpOfTheCommand(Command command) {
        assertThat(printedBy(command.commandName(), "-h")).isEqualTo(printedBy("help", command.commandName()));
    }

    @ParameterizedTest
    @EnumSource(Command.class)
    void withJsonTheFlagGivesTheSameObjectAsHelpOfTheCommand(Command command) {
        var help = printedBy("--json", "help", command.commandName());

        assertThat(printedBy("--json", command.commandName(), "--help")).isEqualTo(help);
        assertThat(printedBy(command.commandName(), "-h", "--json")).isEqualTo(help);
    }

    @ParameterizedTest
    @ValueSource(strings = {"--help", "-h"})
    void theFlagStandsAnywhereAfterTheCommandWhateverTheOtherWordsAre(String flag) {
        var help = printedBy("help", "rename");

        assertThat(printedBy("rename", "--dry-run", "A.java", flag, "bogus")).isEqualTo(help);
        assertThat(printedBy("rename", flag, "--token")).isEqualTo(help);
    }

    @Test
    void theFlagNeverStartsADaemonNorOpensTheSocket() throws IOException {
        var marker = directory.resolve("started");
        var bin = directory.resolve("bin");
        FakeDaemonScripts.install(bin, "echo started > '" + marker + "'");
        var socket = directory.resolve("gabier.sock");
        var environment = Map.of("GABIER_SOCKET", socket.toString(), "PATH", bin.toString());

        for (var command : Command.values()) {
            printedBy(environment, command.commandName(), "--help");
            printedBy(environment, command.commandName(), "-h");
        }

        assertThat(marker).doesNotExist();
        assertThat(socket).doesNotExist();
    }

    @Test
    void theFlagBeforeTheCommandStaysTheListOfTheCommands() {
        assertThat(printedBy("--help")).isEqualTo(printedBy("help"));
        assertThat(printedBy("-h")).isEqualTo(printedBy("help"));
    }
}
