package eu.gabier.daemon;

import eu.gabier.shared.error.domain.GeneratorException;

/**
 * The daemon has decided to stop and answers nothing else: the request is neither read as a command nor run. The cli
 * that gets it waits for the socket to go away and asks again, which starts a new daemon.
 */
final class DaemonStoppingException extends GeneratorException {

    private DaemonStoppingException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    /** The daemon is stopping because nobody has used it for the idle delay. */
    static DaemonStoppingException idle() {
        return new DaemonStoppingException(GeneratorException.internalServerError(DaemonErrorKey.STOPPING)
                .message("The daemon is stopping, as nobody has used it for a while: this request was not run,"
                        + " ask again and a new daemon answers"));
    }
}
