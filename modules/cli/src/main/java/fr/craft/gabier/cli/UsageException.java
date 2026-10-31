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
                .message("Unsupported arguments; usage: gabier [--json] (--version | status | stop | diagnostics <file>"
                        + " | rename [--dry-run] (<file> <line> <column> | --token <file> <identifier>) <new-name>"
                        + " | organize-imports [--dry-run] <file>)")
                .addParameter("arguments", String.join(" ", arguments)));
    }
}
