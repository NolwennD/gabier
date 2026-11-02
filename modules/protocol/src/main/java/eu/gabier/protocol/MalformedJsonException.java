package eu.gabier.protocol;

import eu.gabier.shared.error.domain.GeneratorException;

/** The text is not JSON. It says where and why, and never quotes the text: it may hold what a client should not read back. */
public final class MalformedJsonException extends GeneratorException {

    private MalformedJsonException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    static MalformedJsonException at(int position, String reason) {
        return new MalformedJsonException(GeneratorException.badRequest(ProtocolErrorKey.MALFORMED_JSON)
                .message("Malformed JSON at position " + position + ": " + reason)
                .addParameter("position", String.valueOf(position)));
    }
}
