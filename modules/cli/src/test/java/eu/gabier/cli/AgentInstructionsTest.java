package eu.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.Json;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * {@code gabier help agents} prints the lines to paste in the instructions of a coding agent ({@code AGENTS.md}). The
 * text is a resource of the help, kept beside the guide; these tests keep it short, paste-ready and in step with the
 * commands the help lists.
 */
class AgentInstructionsTest extends CliTest {

    /** A command cited as {@code `gabier <word>`}; after {@code gabier help} the word may be a topic of the help. */
    private static final Pattern CITED = Pattern.compile("`gabier (help )?([a-z][a-z-]*)");

    private static final Set<String> TOPICS = Set.of(Guide.TOPIC, AgentInstructions.TOPIC);

    /** The commands a text cites, so that a test can compare them to the ones the help lists. */
    static List<String> citedCommands(String text) {
        return CITED.matcher(text)
                .results()
                .map(match -> match.group(2))
                .distinct()
                .toList();
    }

    /** The cited names that are neither a command of the help nor, after {@code gabier help}, a topic of it. */
    static List<String> unknown(String text) {
        var listed = Capabilities.all().stream()
                .map(capability -> capability.command().commandName())
                .toList();

        return CITED.matcher(text)
                .results()
                .filter(match -> !listed.contains(match.group(2))
                        && !(match.group(1) != null && TOPICS.contains(match.group(2))))
                .map(match -> match.group(2))
                .distinct()
                .toList();
    }

    private static Map<String, Object> object(Object json) {
        var members = new LinkedHashMap<String, Object>();
        ((Map<?, ?>) json).forEach((name, value) -> members.put((String) name, value));

        return members;
    }

    @Test
    void helpAgentsPrintsTheTextToPasteOnTheStandardOutputWithExitZero() {
        assertThat(run("help", "agents")).isZero();

        assertThat(err()).isEmpty();
        assertThat(out()).isEqualTo(AgentInstructions.text().stripTrailing() + System.lineSeparator());
    }

    @Test
    void theTextIsAShortMarkdownSectionThatCanBePastedAsItIs() {
        var lines = AgentInstructions.text().stripTrailing().lines().toList();

        assertThat(lines).hasSizeBetween(10, 20);
        assertThat(lines.getFirst()).startsWith("## ");
        assertThat(lines).allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(120));
    }

    @Test
    void theTextTellsTheAgentToStartASessionWithGabierStartAndToAskTheHelpBeforeEditingJavaByHand() {
        assertThat(AgentInstructions.text())
                .contains("`gabier start`")
                .contains("`gabier help --for=\"<the task, in English>\"`")
                .contains("Before editing Java by hand")
                .contains("--dry-run");
    }

    @Test
    void everyCommandTheTextCitesIsOneTheHelpLists() {
        assertThat(citedCommands(AgentInstructions.text())).isNotEmpty();
        assertThat(unknown(AgentInstructions.text())).isEmpty();
    }

    @Test
    void aTextThatCitesACommandTheHelpDoesNotListIsCaught() {
        assertThat(unknown("Run `gabier rename` then `gabier frobnicate <file>`, `gabier guide`, `gabier agents`"
                        + " and `gabier help agents`."))
                .containsExactly("frobnicate", "guide", "agents");
    }

    @Test
    void theTextCitesTheCommandsThatReplaceATextEdit() {
        assertThat(citedCommands(AgentInstructions.text()))
                .contains("start", "help", "rename", "diagnostics", "organize-imports", "actions", "apply-action");
    }

    @Test
    void helpAgentsAsJsonIsAnObjectWithTheText() {
        assertThat(run("--json", "help", "agents")).isZero();

        assertThat(out().lines()).hasSize(1);
        var agents = object(Json.parse(out()));
        assertThat(agents).containsOnlyKeys("name", "summary", "text");
        assertThat(agents.get("name")).isEqualTo("agents");
        assertThat((String) agents.get("summary")).contains("instructions");
        assertThat((String) agents.get("text")).isEqualTo(AgentInstructions.text());
    }

    @Test
    void helpAgentsWithAnotherWordIsAUsageError() {
        assertThat(run("help", "agents", "rename")).isEqualTo(2);

        assertThat(out()).isEmpty();
        assertThat(err()).isEqualTo(usageText("help agents rename"));
    }

    @Test
    void theListPointsToTheInstructionsForAnAgent() {
        run("help");

        assertThat(out())
                .contains("gabier help agents")
                .contains("the lines to paste in the instructions of a coding agent (AGENTS.md, CLAUDE.md)");
    }

    @Test
    void helpAgentsNeverStartsADaemonNorOpensTheSocket() throws IOException {
        var marker = directory.resolve("started");
        var bin = directory.resolve("bin");
        FakeDaemonScripts.install(bin, "echo started > '" + marker + "'");
        var socket = directory.resolve("gabier.sock");
        var environment = Map.of("GABIER_SOCKET", socket.toString(), "PATH", bin.toString());

        assertThat(run(environment, "help", "agents")).isZero();

        assertThat(marker).doesNotExist();
        assertThat(socket).doesNotExist();
    }
}
