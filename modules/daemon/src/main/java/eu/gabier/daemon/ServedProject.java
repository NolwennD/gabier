package eu.gabier.daemon;

import eu.gabier.editing.domain.JavaLanguageServer;
import eu.gabier.protocol.JdtlsProgress;
import eu.gabier.protocol.JdtlsState;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The project a daemon serves: its root, the language server the methods of the catalogue ask, and where that server
 * is. The daemon closes it when it stops, which ends whatever the server started.
 */
interface ServedProject extends JavaLanguageServer, AutoCloseable {

    /** The root of the project, absolute and normalized. */
    Path root();

    /** Where the language server is. Asking it never starts the server. */
    JdtlsState state();

    /** What the language server is doing while it is {@code STARTING}; empty in any other state. */
    Optional<JdtlsProgress> jdtlsProgress();

    /**
     * Why the language server is {@code FAILED}, as it or the daemon said it; empty when it is not failed or the reason
     * is not known. The text comes from jdtls: it is shown only through {@code TerminalText}.
     */
    Optional<String> jdtlsFailure();

    /** Ends the language server, whether it is still starting or ready. Closing it again does nothing. */
    @Override
    void close() throws IOException, InterruptedException;
}
