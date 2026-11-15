package eu.gabier.daemon;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/** How a jdtls process is started: the one seam between the session and the operating system. */
@FunctionalInterface
interface JdtlsStarter {

    /**
     * Starts jdtls.
     *
     * @param dataDirectory the folder jdtls keeps its workspace in, which belongs to this run alone
     */
    JdtlsConnection start(Path dataDirectory) throws IOException;

    /**
     * The workspace of the project that a jdtls of this kind keeps between two daemons ({@link KeptWorkspace}). None by
     * default: the folder of each run is then temporary and removed with it.
     *
     * @param projectRoot the project jdtls is started on
     * @throws IOException when the launcher that would start jdtls is not found
     */
    default Optional<KeptWorkspace> keptWorkspace(Path projectRoot) throws IOException {
        return Optional.empty();
    }
}
