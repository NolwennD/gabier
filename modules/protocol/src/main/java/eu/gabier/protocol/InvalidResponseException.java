package eu.gabier.protocol;

import eu.gabier.shared.error.domain.GeneratorException;
import org.jspecify.annotations.Nullable;

/** What the daemon answered is not a JSON-RPC 2.0 response of gabier: a failure of the daemon, not of the request. */
public final class InvalidResponseException extends GeneratorException {

    private InvalidResponseException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    static InvalidResponseException of(String message, @Nullable Throwable cause) {
        return new InvalidResponseException(GeneratorException.internalServerError(ProtocolErrorKey.INVALID_RESPONSE)
                .message(message)
                .cause(cause));
    }
}
