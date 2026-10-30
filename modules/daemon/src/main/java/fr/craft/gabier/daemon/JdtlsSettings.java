package fr.craft.gabier.daemon;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * What a jdtls session is asked to do and how long it may take.
 *
 * @param projectRoot the folder jdtls is initialized on
 * @param expectedProjects the folders of the projects jdtls must have imported before the session is ready (absolute
 *     and normalized, whatever they were given as)
 * @param timeout how long the whole start may take, all its waits together, and then each request to the started jdtls
 * @param pollInterval how long to wait between two questions to jdtls about its projects
 * @param shutdownTimeout how long jdtls is given to answer the shutdown and to exit, and then each process of its tree
 *     to end once killed
 */
record JdtlsSettings(
        Path projectRoot,
        List<Path> expectedProjects,
        Duration timeout,
        Duration pollInterval,
        Duration shutdownTimeout) {

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    JdtlsSettings {
        expectedProjects = expectedProjects.stream()
                .map(project -> project.toAbsolutePath().normalize())
                .toList();
    }

    static JdtlsSettings of(Path projectRoot, List<Path> expectedProjects, Duration timeout) {
        return new JdtlsSettings(projectRoot, expectedProjects, timeout, POLL_INTERVAL, SHUTDOWN_TIMEOUT);
    }
}
