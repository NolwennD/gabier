package eu.gabier.daemon;

import eu.gabier.editing.domain.JavaLanguageServer;
import eu.gabier.protocol.JdtlsProgress;
import eu.gabier.protocol.JdtlsState;
import eu.gabier.testing.domain.ProjectBuild;
import java.io.IOException;
import java.util.Optional;

/**
 * A language server that starts in the background as soon as it is created, and that can be asked where it is. It
 * compiles the project too: it is the build the selection of tests reads.
 */
interface StartedLanguageServer extends JavaLanguageServer, ProjectBuild, AutoCloseable {

    /** Where the start is: {@code STARTING}, then {@code READY} or {@code FAILED}. */
    JdtlsState state();

    /** What the server is doing while it is {@code STARTING}; empty in any other state. */
    Optional<JdtlsProgress> progress();

    /** Why the server is {@code FAILED} (the failure of its start, or its death); empty when it is not failed. */
    Optional<String> failure();

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
