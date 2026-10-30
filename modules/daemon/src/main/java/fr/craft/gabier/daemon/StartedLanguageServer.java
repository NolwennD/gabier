package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.domain.JavaLanguageServer;
import fr.craft.gabier.protocol.JdtlsState;
import java.io.IOException;

/** A language server that starts in the background as soon as it is created, and that can be asked where it is. */
interface StartedLanguageServer extends JavaLanguageServer, AutoCloseable {

    /** Where the start is: {@code STARTING}, then {@code READY} or {@code FAILED}. */
    JdtlsState state();

    /**
     * Waits until the start is over.
     *
     * @throws JdtlsException when the start failed (the message says why)
     */
    void awaitReady() throws InterruptedException;

    /** Ends the server, even when its start is not over. */
    @Override
    void close() throws IOException, InterruptedException;
}
