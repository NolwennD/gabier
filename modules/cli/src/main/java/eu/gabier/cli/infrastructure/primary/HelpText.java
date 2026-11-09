package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.application.AskTheGuide;
import eu.gabier.cli.application.GuideMatch;
import eu.gabier.cli.application.GuideText;
import eu.gabier.cli.domain.Guide;
import eu.gabier.protocol.Json;
import java.util.List;
import java.util.stream.Collectors;

/**
 * What {@code gabier help} prints, from the commands of {@link Capabilities}: plain text, or the same content as a JSON
 * object.
 */
final class HelpText {

    private HelpText() {}

    /** The names of all the commands, in the order of {@code gabier help}. */
    static String names() {
        return commands().stream().map(Command::commandName).collect(Collectors.joining(", "));
    }

    /** Every command with its summary, and how to ask for more. */
    static String index(boolean json) {
        if (json) {
            return "{\"commands\":["
                    + commands().stream()
                            .map(command -> "{\"name\":" + Json.quote(command.commandName()) + ",\"summary\":"
                                    + Json.quote(command.summary()) + ",\"usage\":" + Json.quote(command.usage()) + "}")
                            .collect(Collectors.joining(","))
                    + "]}";
        }
        var width = commands().stream()
                .mapToInt(command -> command.commandName().length())
                .max()
                .orElse(0);
        var lines = commands().stream()
                .map(command -> "  " + command.commandName()
                        + " ".repeat(width - command.commandName().length()) + "  " + command.summary())
                .collect(Collectors.joining("\n"));

        return """
            gabier changes Java through a Java language server.

            Usage: gabier [--json] <command> [arguments]

            Commands:
            %s

            gabier help <command> gives the usage, an example and the refusals of a command.
            gabier help %s says %s (text blocks, a switch expression, an if to turn round...).
            gabier help %s=<task> gives the sections of that guide that answer a task, in English, with the command.
            gabier help %s prints %s.
            A command whose operation is declined ends its refusal with the line that points to gabier help <command>,
            an unknown command with gabier help --for="<task>".
            gabier --version prints the version. With --json, the answer and a refusal are JSON.\
            """.formatted(
                        lines,
                        GuideTopic.TOPIC,
                        GuideTopic.SUMMARY,
                        GuideTopic.FOR,
                        AgentInstructions.TOPIC,
                        AgentInstructions.SUMMARY);
    }

    /** The instructions for a coding agent, as they are written: the text of {@code agents.md}, or a JSON object. */
    static String agents(boolean json) {
        if (json) {
            return "{\"name\":" + Json.quote(AgentInstructions.TOPIC) + ",\"summary\":"
                    + Json.quote(AgentInstructions.SUMMARY) + ",\"text\":" + Json.quote(AgentInstructions.text()) + "}";
        }

        return AgentInstructions.text().stripTrailing();
    }

    /** The guide of the improvements, as it is written: the text of {@code docs/improve.md}, or a JSON object. */
    static String guide(boolean json) {
        if (json) {
            return "{\"name\":" + Json.quote(GuideTopic.TOPIC) + ",\"summary\":" + Json.quote(GuideTopic.SUMMARY)
                    + ",\"text\":" + Json.quote(GuideTopic.text()) + "}";
        }

        return GuideTopic.text().stripTrailing();
    }

    /**
     * The sections of the guide that answer a task, best first, each with what to do and a confidence; or the plain
     * word that none does. The answer is worked out by {@link AskTheGuide} from the guide of the build, with no daemon
     * and no network; this only writes it.
     */
    static String answer(String task, boolean json) {
        var found = AskTheGuide.answer(task, new Guide(GuideTopic.text()));

        return json ? answerAsJson(task, found) : answerAsText(task, found);
    }

    private static String answerAsJson(String task, List<GuideMatch> found) {
        var matches = found.stream()
                .map(match -> "{\"rank\":" + match.rank() + ",\"title\":"
                        + Json.quote(match.section().title())
                        + ",\"kind\":" + Json.quote(match.section().byHand() ? "by-hand" : "ask") + ",\"how\":"
                        + Json.quote(match.section().instruction()) + ",\"confidence\":" + GuideText.confidence(match)
                        + "}")
                .collect(Collectors.joining(","));

        return "{\"question\":" + Json.quote(task) + ",\"matches\":[" + matches + "]}";
    }

    /** The text of {@link GuideText}, then the line that points to the whole guide, as only the command line says it. */
    private static String answerAsText(String task, List<GuideMatch> found) {
        var answer = GuideText.answer(task, found);
        if (found.isEmpty()) {
            return """
                %s
                `gabier help guide` prints the whole guide, `gabier help` lists the commands.\
                """.formatted(answer);
        }

        return """
            %s

            `gabier help guide` prints the whole guide.\
            """.formatted(answer);
    }

    /** The usage, an example and the refusals of one command. */
    static String of(Command command, boolean json) {
        if (json) {
            return "{\"name\":" + Json.quote(command.commandName()) + ",\"summary\":" + Json.quote(command.summary())
                    + ",\"usage\":" + Json.quote(command.usage()) + ",\"example\":" + Json.quote(command.example())
                    + (command.notes().isEmpty() ? "" : ",\"notes\":" + Json.quote(command.notes()))
                    + ",\"refusals\":["
                    + command.refusals().stream().map(Json::quote).collect(Collectors.joining(","))
                    + "]}";
        }
        var notes = command.notes().isEmpty() ? "" : """
            Notes:
            %s

            """.formatted(indented(command.notes()));

        return """
            %s

            Usage:
            %s

            Example:
            %s

            %sRefusals (a problem object on the standard error, exit code 2):
            %s\
            """.formatted(
                        capitalised(command.summary()),
                        indented(command.usage()),
                        indented(command.example()),
                        notes,
                        indented(String.join("\n", command.refusals())));
    }

    private static List<Command> commands() {
        return Capabilities.all().stream().map(Capability::command).toList();
    }

    private static String capitalised(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1) + ".";
    }

    private static String indented(String text) {
        return text.lines().map(line -> "  " + line).collect(Collectors.joining("\n"));
    }
}
