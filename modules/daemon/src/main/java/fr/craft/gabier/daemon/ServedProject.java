package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.domain.JavaLanguageServer;
import fr.craft.gabier.protocol.JdtlsState;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The project a daemon serves: its root, the language server the methods of the catalogue ask, and where that server
 * is. The daemon closes it when it stops, which ends whatever the server started.
 */
interface ServedProject extends JavaLanguageServer, AutoCloseable {

    /** The root of the project, absolute and normalized. */
    Path root();

    /** Where the language server is. Asking it never starts the server. */
    JdtlsState state();

    /** Ends the language server, whether it is still starting or ready. Closing it again does nothing. */
    @Override
    void close() throws IOException, InterruptedException;
}
