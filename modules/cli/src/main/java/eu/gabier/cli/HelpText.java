package eu.gabier.cli;

import eu.gabier.cli.ranking.Match;
import eu.gabier.cli.ranking.Ranking;
import eu.gabier.protocol.Json;
import eu.gabier.protocol.TerminalText;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
            gabier --version prints the version. With --json, the answer and a refusal are JSON.\
            """.formatted(lines, Guide.TOPIC, Guide.SUMMARY, Guide.FOR);
    }

    /** The guide of the improvements, as it is written: the text of {@code docs/improve.md}, or a JSON object. */
    static String guide(boolean json) {
        if (json) {
            return "{\"name\":" + Json.quote(Guide.TOPIC) + ",\"summary\":" + Json.quote(Guide.SUMMARY) + ",\"text\":"
                    + Json.quote(Guide.text()) + "}";
        }

        return Guide.text().stripTrailing();
    }

    /**
     * The sections of the guide that answer a task, best first, each with what to do and a confidence; or the plain
     * word that none does. Computed from the guide of the build, with no daemon and no network.
     */
    static String answer(String task, boolean json) {
        var sections = GuideSections.ofTheGuide();
        var matches = Ranking.of(sections.stream().map(GuideSection::document).toList())
                .answer(task);
        var found = IntStream.range(0, matches.size())
                .mapToObj(rank ->
                        new Found(rank + 1, sections.get(matches.get(rank).index()), confidence(matches.get(rank))))
                .toList();

        return json ? answerAsJson(task, found) : answerAsText(task, found);
    }

    private static String answerAsJson(String task, List<Found> found) {
        var matches = found.stream()
                .map(match -> "{\"rank\":" + match.rank() + ",\"title\":"
                        + Json.quote(match.section().title())
                        + ",\"kind\":" + Json.quote(match.section().byHand() ? "by-hand" : "ask") + ",\"how\":"
                        + Json.quote(match.section().instruction()) + ",\"confidence\":" + match.confidence() + "}")
                .collect(Collectors.joining(","));

        return "{\"question\":" + Json.quote(task) + ",\"matches\":[" + matches + "]}";
    }

    private static String answerAsText(String task, List<Found> found) {
        if (found.isEmpty()) {
            return """
                Nothing in the guide matches "%s".
                `gabier help guide` prints the whole guide, `gabier help` lists the commands.\
                """.formatted(TerminalText.neutralise(task));
        }
        var matches = found.stream()
                .map(match -> "%d. %s (confidence %s)\n   %s"
                        .formatted(
                                match.rank(),
                                match.section().title(),
                                match.confidence(),
                                match.section().how()))
                .collect(Collectors.joining("\n\n"));

        return """
            For "%s":

            %s

            `gabier help guide` prints the whole guide.\
            """.formatted(TerminalText.neutralise(task), matches);
    }

    private static String confidence(Match match) {
        return String.format(Locale.ROOT, "%.2f", match.confidence());
    }

    /** A section of the guide that answers, with its rank (from 1) and its confidence written with two decimals. */
    private record Found(int rank, GuideSection section, String confidence) {}

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
