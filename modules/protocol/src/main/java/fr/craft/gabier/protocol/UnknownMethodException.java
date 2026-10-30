package fr.craft.gabier.protocol;

import fr.craft.gabier.shared.error.domain.GeneratorException;

/** The daemon has no method of this name. */
public final class UnknownMethodException extends GeneratorException {

    private UnknownMethodException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    public static UnknownMethodException of(String method) {
        return new UnknownMethodException(GeneratorException.badRequest(ProtocolErrorKey.UNKNOWN_METHOD)
                .message("The daemon has no method " + Json.quote(method))
                .addParameter("method", method));
    }
}
