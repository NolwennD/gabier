package eu.gabier.cli;

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
            gabier --version prints the version. With --json, the answer and a refusal are JSON.\
            """.formatted(lines);
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
