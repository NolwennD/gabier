package eu.gabier.cli;

import eu.gabier.shared.error.domain.GeneratorException;
import java.util.List;

/** The command line is not one gabier understands. */
final class UsageException extends GeneratorException {

    private UsageException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    /** The arguments given, the {@code --json} option not included. */
    static UsageException unsupportedArguments(List<String> arguments) {
        return new UsageException(GeneratorException.badRequest(CliErrorKey.USAGE)
                .message("Unsupported arguments; run gabier help for the commands, "
                        + "and gabier help <command> for the usage of one")
                .addParameter("arguments", String.join(" ", arguments)));
    }

    /** The first of the arguments is a command with a typo: the problem names the command it was meant for. */
    static UsageException unknownCommandTypo(List<String> arguments, Command meant) {
        return new UsageException(GeneratorException.badRequest(CliErrorKey.USAGE)
                .message("Unsupported arguments; did you mean " + meant.commandName()
                        + "? Run gabier help for the commands, and gabier help <command> for the usage of one")
                .addParameter("arguments", String.join(" ", arguments))
                .addParameter("suggestion", meant.commandName()));
    }

    /** {@code gabier help <command>} for a word that names no command; {@code known} lists the ones that exist. */
    static UsageException unknownCommand(String command, String known) {
        return new UsageException(GeneratorException.badRequest(CliErrorKey.USAGE)
                .message("Unknown command; the commands are " + known)
                .addParameter("command", command));
    }
}
