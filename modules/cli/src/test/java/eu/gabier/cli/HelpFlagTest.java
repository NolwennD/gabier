package eu.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class HelpFlagTest {

    @TempDir
    Path directory;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    static Stream<Command> commands() {
        return Capabilities.all().stream().map(Capability::command);
    }

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

    @Test
    void theHelpOfMoveTypeToFileWarnsThatANestedTestClassCannotBeInstantiatedOnceMovedAndHowToAvoidIt() {
        for (var help : new String[][] {{"help", "move-type-to-file"}, {"move-type-to-file", "--help"}}) {
            assertThat(printedBy(help))
                    .contains("Notes:")
                    .contains("gets a field and a constructor parameter of the outer type")
                    .contains("@Nested test class can then no longer be instantiated by JUnit")
                    .contains("having it extend the shared base class")
                    .contains("annotation of the class, such as @Nested, stays on it after the move")
                    .contains("Refusals");
        }
    }

    @Test
    void theHelpOfMoveSaysThatAMoveCanBreakWhatNeedsThePackageAndThatThePackageCannotBeTheDefaultOne() {
        for (var help : new String[][] {{"help", "move"}, {"move", "--help"}}) {
            assertThat(printedBy(help))
                    .contains("gabier [--json] move [--dry-run] <file>... <package>")
                    .contains("Notes:")
                    .contains("package-private")
                    .contains("gabier diagnostics")
                    .contains("default package")
                    .contains("language-server-not-movable-to-package")
                    .contains("editing-not-a-package-name");
        }
    }

    @Test
    void theHelpOfInlineSaysWhereToPutThePositionOfAMethodOrAConstantToInlineAllItsUses() {
        for (var help : new String[][] {{"help", "inline"}, {"inline", "--help"}}) {
            assertThat(printedBy(help))
                    .contains("Notes:")
                    .contains("on the declaration of a method or a constant inlines every use and removes it")
                    .contains("on one use inlines that use only")
                    .contains("A local variable is inlined everywhere")
                    .contains("Refusals");
        }
    }

    @Test
    void theHelpOfCreateSaysWhereThePackageComesFromAndThatTheFoldersAreMade() {
        for (var help : new String[][] {{"help", "create"}, {"create", "--help"}}) {
            assertThat(printedBy(help))
                    .contains("Notes:")
                    .contains("the package line comes from the folder of the file")
                    .contains("The folders of the file that do not exist are made with it")
                    .contains("A dry run makes none")
                    .contains("A file where a folder would go is refused")
                    .doesNotContain("must exist")
                    .contains("editing-file-exists")
                    .contains("language-server-not-creatable")
                    .contains("Refusals");
        }
    }

    @Test
    void theHelpOfReferencesSaysWhatIsListedAndWhatIsNot() {
        for (var help : new String[][] {{"help", "references"}, {"references", "--help"}}) {
            assertThat(printedBy(help))
                    .contains("Notes:")
                    .contains("--include-declaration")
                    .contains("accessors")
                    .contains("test sources")
                    .contains("language-server-no-such-symbol")
                    .contains("Refusals");
        }
    }

    @Test
    void theHelpOfACommandWithoutNotesHasNoNotesSection() {
        assertThat(printedBy("help", "rename")).doesNotContain("Notes:");
    }

    @ParameterizedTest
    @MethodSource("commands")
    void theLongFlagGivesTheSameTextAsHelpOfTheCommand(Command command) {
        assertThat(printedBy(command.commandName(), "--help")).isEqualTo(printedBy("help", command.commandName()));
    }

    @ParameterizedTest
    @MethodSource("commands")
    void theShortFlagGivesTheSameTextAsHelpOfTheCommand(Command command) {
        assertThat(printedBy(command.commandName(), "-h")).isEqualTo(printedBy("help", command.commandName()));
    }

    @ParameterizedTest
    @MethodSource("commands")
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

        for (var command : commands().toList()) {
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
