package fr.craft.gabier.daemon;

import fr.craft.gabier.shared.error.domain.GeneratorException;
import java.nio.file.Path;

/** The jdtls of the project could not be started: the reason is in the message, and the next request tries again. */
final class JdtlsStartException extends GeneratorException {

    private JdtlsStartException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    static JdtlsStartException of(Path project, JdtlsException failure) {
        return new JdtlsStartException(GeneratorException.internalServerError(DaemonErrorKey.JDTLS_START_FAILED)
                .message(failure.getMessage())
                .addParameter("project", project.toString())
                .cause(failure));
    }
}
