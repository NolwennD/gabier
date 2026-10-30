package fr.craft.gabier.daemon;

import fr.craft.gabier.shared.error.domain.GeneratorException;
import java.nio.file.Path;
import java.util.List;

/** The command line is not one the daemon understands. */
final class UsageException extends GeneratorException {

    private UsageException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    static UsageException unsupportedArguments(List<String> arguments) {
        return new UsageException(GeneratorException.badRequest(DaemonErrorKey.USAGE)
                .message("Unsupported arguments; usage: gabier-daemon [<project folder> | --version | --self-check]")
                .addParameter("arguments", String.join(" ", arguments)));
    }

    static UsageException notAFolder(Path folder) {
        return new UsageException(GeneratorException.badRequest(DaemonErrorKey.USAGE)
                .message("The project " + folder + " is not a folder")
                .addParameter("project", folder.toString()));
    }
}
