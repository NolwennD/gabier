package fr.craft.gabier.protocol;

import fr.craft.gabier.shared.error.domain.GeneratorException;
import org.jspecify.annotations.Nullable;

/** The line is JSON, but not a JSON-RPC 2.0 request: the answer to it still carries the id when it could be read. */
public final class InvalidRequestException extends GeneratorException {

    private final @Nullable String id;

    private InvalidRequestException(GeneratorExceptionBuilder builder, @Nullable String id) {
        super(builder);
        this.id = id;
    }

    static InvalidRequestException of(@Nullable String id, String message) {
        return new InvalidRequestException(
                GeneratorException.badRequest(ProtocolErrorKey.INVALID_REQUEST).message(message), id);
    }

    /** The request is refused because of one of its parameters, which the problem object names in {@code parameter}. */
    static InvalidRequestException of(@Nullable String id, String message, String parameter) {
        return new InvalidRequestException(
                GeneratorException.badRequest(ProtocolErrorKey.INVALID_REQUEST)
                        .message(message)
                        .addParameter(JsonRpc.PARAMETER, parameter),
                id);
    }

    /** The id of the request as JSON text, or null when there is none or it is not one. */
    public @Nullable String id() {
        return id;
    }
}
