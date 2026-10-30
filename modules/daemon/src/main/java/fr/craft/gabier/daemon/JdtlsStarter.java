package fr.craft.gabier.daemon;

import java.io.IOException;
import java.nio.file.Path;

/** How a jdtls process is started: the one seam between the session and the operating system. */
@FunctionalInterface
interface JdtlsStarter {

    /**
     * Starts jdtls.
     *
     * @param dataDirectory the folder jdtls keeps its workspace in, which belongs to this run alone
     */
    JdtlsConnection start(Path dataDirectory) throws IOException;
}
