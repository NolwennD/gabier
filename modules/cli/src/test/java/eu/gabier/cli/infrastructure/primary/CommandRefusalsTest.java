package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The refusals in the help of a command are those of its page in {@code docs/commands}: the keys under the heading
 * {@code ## Refusals} of the page, one bullet each ({@code - `key`: what it means}), read here, not copied. A command
 * that has no page ({@code move}: the help is its documentation, {@code .claude/rules/help.md}) is not compared with
 * one; the keys of its help are checked against the code by {@code RefusalKeysTest} of {@code :architecture}.
 */
class CommandRefusalsTest {

    private static final String HEADING = "## Refusals";
    private static final Set<String> SHARED_PAGE_OF = Set.of("status", "stop");

    static Stream<Command> commands() {
        return Capabilities.all().stream().map(Capability::command).filter(command -> Files.exists(page(command)));
    }

    @ParameterizedTest
    @MethodSource("commands")
    void theRefusalsInTheHelpOfACommandAreThoseOfItsPage(Command command) throws IOException {
        var keysInTheHelp = command.refusals().stream()
                .map(refusal -> refusal.substring(0, refusal.indexOf(':')))
                .toList();

        assertThat(keysInTheHelp)
                .as("the refusals of %s in Command, and in %s", command.commandName(), page(command))
                .containsExactlyInAnyOrderElementsOf(refusalKeysOf(page(command)));
    }

    /** The page of a command: its own, except {@code status} and {@code stop}, which share one. */
    private static Path page(Command command) {
        var docs = Path.of(System.getProperty("gabier.docs.commands"));
        var own = docs.resolve(command.commandName() + ".md");

        return Files.exists(own) || !SHARED_PAGE_OF.contains(command.commandName())
                ? own
                : docs.resolve("status-and-stop.md");
    }

    private static List<String> refusalKeysOf(Path page) throws IOException {
        var lines = Files.readAllLines(page);
        assertThat(lines).as("%s has the heading %s", page, HEADING).contains(HEADING);

        return lines.stream()
                .skip(lines.indexOf(HEADING) + 1)
                .takeWhile(line -> !line.startsWith("#"))
                .filter(line -> line.startsWith("- `"))
                .map(line -> line.substring(3, line.indexOf('`', 3)))
                .toList();
    }
}
