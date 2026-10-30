package fr.craft.gabier.daemon;

import fr.craft.gabier.shared.error.domain.GeneratorException;

/** A client sent a line longer than the daemon reads: the problem object gives the limit. */
final class LineTooLongException extends GeneratorException {

    private LineTooLongException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    static LineTooLongException over(int limit) {
        return new LineTooLongException(GeneratorException.badRequest(DaemonErrorKey.LINE_TOO_LONG)
                .message("A request line is longer than " + limit + " bytes")
                .addParameter("limit", String.valueOf(limit)));
    }
}
