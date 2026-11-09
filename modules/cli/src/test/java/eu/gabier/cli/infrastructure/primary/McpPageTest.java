package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@code gabier help mcp} and {@code docs/commands/mcp.md} say the same of the keys a failed call can hold: the command
 * ends only with a usage error, and the page lists the other keys under {@code ## Failed calls}, one bullet each ({@code
 * - `key`: what it means}), which the help names in its notes. Read here, not copied.
 */
class McpPageTest {

    private static final String HEADING = "## Failed calls";

    private static List<String> failedCallKeys() throws IOException {
        var page = Path.of(System.getProperty("gabier.docs.commands")).resolve("mcp.md");
        var lines = Files.readAllLines(page);
        assertThat(lines).as("%s has the heading %s", page, HEADING).contains(HEADING);

        return lines.stream()
                .skip(lines.indexOf(HEADING) + 1)
                .takeWhile(line -> !line.startsWith("#"))
                .filter(line -> line.startsWith("- `"))
                .map(line -> line.substring(3, line.indexOf('`', 3)))
                .toList();
    }

    @Test
    void theNotesOfTheHelpNameTheKeysOfAFailedCallThatThePageLists() throws IOException {
        var keys = failedCallKeys();

        assertThat(keys).contains("operation-invalid-values", "daemon-start-failed", "daemon-connection-failed");
        assertThat(Mcp.COMMAND.notes()).contains(keys.toArray(new String[0]));
    }

    @Test
    void theRefusalsOfTheHelpAreTheUsageErrorAloneAndNoKeyOfAFailedCall() throws IOException {
        var refusals = String.join("\n", Mcp.COMMAND.refusals());

        assertThat(refusals).startsWith("usage-error");
        assertThat(failedCallKeys()).noneSatisfy(key -> assertThat(refusals).contains(key));
    }

    @Test
    void onlyStatusStartsNoDaemonSoTheHelpOfMcpNeverSaysTheCommandStartsNone() {
        assertThat(Mcp.COMMAND.notes()).contains("daemon-not-running").contains("status");
        assertThat(String.join("\n", Mcp.COMMAND.refusals()) + Mcp.COMMAND.notes())
                .doesNotContain("the command never starts one");
    }
}
