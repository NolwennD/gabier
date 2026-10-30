package fr.craft.gabier.daemon;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * What a jdtls session is asked to do and how long it may take.
 *
 * @param projectRoot the folder jdtls is initialized on
 * @param expectedProjects the folders of the projects jdtls must have imported when it says it is ready (absolute and
 *     normalized, whatever they were given as); none when the project is served whatever jdtls finds in it
 * @param timeout how long the whole start may take, all its waits together, and then each request to the started jdtls
 * @param treeInterval how long to wait between two readings of the process tree of jdtls
 * @param shutdownTimeout how long jdtls is given to answer the shutdown and to exit, and then each process of its tree
 *     to end once killed
 */
record JdtlsSettings(
        Path projectRoot,
        List<Path> expectedProjects,
        Duration timeout,
        Duration treeInterval,
        Duration shutdownTimeout) {

    private static final Duration TREE_INTERVAL = Duration.ofMillis(250);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    JdtlsSettings {
        expectedProjects = expectedProjects.stream()
                .map(project -> project.toAbsolutePath().normalize())
                .toList();
    }

    static JdtlsSettings of(Path projectRoot, List<Path> expectedProjects, Duration timeout) {
        return new JdtlsSettings(projectRoot, expectedProjects, timeout, TREE_INTERVAL, SHUTDOWN_TIMEOUT);
    }
}
