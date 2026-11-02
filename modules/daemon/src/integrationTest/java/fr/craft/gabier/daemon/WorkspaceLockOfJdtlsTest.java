package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A real jdtls takes no lock on its data folder ({@code .metadata/.lock} is the one of the Eclipse IDE), so the daemon
 * holds its own ({@link WorkspaceLock}) while jdtls runs on it. Not run by {@code check}: {@code ./gradlew jdtlsProbe}, with {@code GABIER_JDTLS} set.
 */
class WorkspaceLockOfJdtlsTest {

    @TempDir
    Path directory;

    @Test
    void theDaemonHoldsTheLockOfTheDataFolderWhileJdtlsRunsOnItAsJdtlsTakesNone() throws Exception {
        Path root = Files.createDirectories(directory.resolve("project")).toRealPath();
        Files.createDirectories(root.resolve("src/main/java"));
        Files.writeString(root.resolve("src/main/java/Main.java"), "class Main {}\n");
        EclipseProject.describe(root);
        Path data;
        try (JdtlsSession session = JdtlsSession.start(
                RealJdtls.launcher(),
                JdtlsSettings.of(root, List.of(root), Duration.ofMinutes(5)),
                System.out::println,
                ignored -> {})) {
            data = session.dataDirectory();

            assertThat(data.resolve(".metadata/.lock"))
                    .as("jdtls takes no lock of its own on its data folder")
                    .doesNotExist();
            assertThat(WorkspaceLock.isHeld(data)).as("the lock of the daemon").isTrue();
        }

        assertThat(data).as("the temporary data folder, removed at the close").doesNotExist();
    }
}
