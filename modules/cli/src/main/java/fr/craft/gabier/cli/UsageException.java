package fr.craft.gabier.cli;

import fr.craft.gabier.shared.error.domain.GeneratorException;
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

    /** {@code gabier help <command>} for a word that names no command; {@code known} lists the ones that exist. */
    static UsageException unknownCommand(String command, String known) {
        return new UsageException(GeneratorException.badRequest(CliErrorKey.USAGE)
                .message("Unknown command; the commands are " + known)
                .addParameter("command", command));
    }
}
