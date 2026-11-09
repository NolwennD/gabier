package eu.gabier.cli.infrastructure.primary.mcp;

import eu.gabier.shared.error.domain.GeneratorException;
import java.time.Duration;

/** A call was given up: the daemon had not answered when the bound ran out. */
final class CallTimedOutException extends GeneratorException {

    private CallTimedOutException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    /** The daemon did not answer within the bound; the operation may still run in it, which {@code status} shows. */
    static CallTimedOutException after(Duration bound) {
        return new CallTimedOutException(GeneratorException.internalServerError(McpErrorKey.DAEMON_TIMEOUT)
                .message("The daemon did not answer within " + bound.toSeconds() + " seconds; the call is given up,"
                        + " but the daemon may still be working on it: look at status, then call again")
                .addParameter("timeoutSeconds", String.valueOf(bound.toSeconds())));
    }
}
