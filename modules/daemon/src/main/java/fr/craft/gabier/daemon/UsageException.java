package fr.craft.gabier.daemon;

import fr.craft.gabier.shared.error.domain.GeneratorException;
import java.util.List;

/** The command line is not one the daemon understands. */
final class UsageException extends GeneratorException {

    private UsageException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    static UsageException unsupportedArguments(List<String> arguments) {
        return new UsageException(GeneratorException.badRequest(DaemonErrorKey.USAGE)
                .message("Unsupported arguments; usage: gabier-daemon [--version | --self-check]")
                .addParameter("arguments", String.join(" ", arguments)));
    }
}
