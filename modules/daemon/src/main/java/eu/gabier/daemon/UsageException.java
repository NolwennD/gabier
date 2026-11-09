package eu.gabier.daemon;

import eu.gabier.shared.error.domain.GeneratorException;
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

    static UsageException invalidIdleTimeout(String value) {
        return new UsageException(GeneratorException.badRequest(DaemonErrorKey.USAGE)
                .message("GABIER_IDLE_TIMEOUT is the number of minutes with no request after which the daemon stops,"
                        + " 0 for never; \"" + value
                        + "\" is not a whole number of minutes between 0 and 153722867 (about 292"
                        + " years)")
                .addParameter("GABIER_IDLE_TIMEOUT", value));
    }

    static UsageException notAFolder(Path folder) {
        return new UsageException(GeneratorException.badRequest(DaemonErrorKey.USAGE)
                .message("The project " + folder + " is not a folder")
                .addParameter("project", folder.toString()));
    }
}
