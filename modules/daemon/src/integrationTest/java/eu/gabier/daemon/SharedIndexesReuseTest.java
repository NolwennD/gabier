package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Two jdtls on projects that use the same jar and the same folder of shared indexes ({@link SharedIndexes}): the first
 * writes the indexes of the JDK and of the jar there once its import is over, the second uses them and builds none of
 * its own. Not run by {@code check}: {@code ./gradlew jdtlsProbe}, with {@code GABIER_JDTLS} set.
 */
class SharedIndexesReuseTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(5);
    private static final Duration PATIENCE = Duration.ofSeconds(90);

    @TempDir
    Path directory;

    /** A jar that both projects name: copied, so that nothing else changes the file. */
    private Path library() throws IOException, URISyntaxException {
        Path original = Path.of(org.assertj.core.api.Assertions.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        return Files.copy(original, directory.resolve("library.jar"));
    }

    private Path project(String name, Path library) throws IOException {
        Path root = Files.createDirectories(directory.resolve(name)).toRealPath();
        Files.createDirectories(root.resolve("src/main/java"));
        Files.writeString(root.resolve("src/main/java/Main.java"), "class Main {}\n");
        EclipseProject.describe(root, List.of(library));
        return root;
    }

    private JdtlsSession start(Path root, Path indexes) throws Exception {
        var launcher = RealJdtls.launcher();
        var sharing = new JdtlsLauncher(
                launcher.launcher(), launcher.javaHome(), List.of("-Djdt.core.sharedIndexLocation=" + indexes));
        return JdtlsSession.start(
                sharing, JdtlsSettings.of(root, List.of(root), TIMEOUT), System.out::println, ignored -> {});
    }

    /** The names of the index files jdtls keeps in its data folder: one for each library and project it indexed. */
    private static List<String> localIndexes(Path data) throws IOException {
        return indexesIn(data.resolve(".metadata/.plugins/org.eclipse.jdt.core"), 1);
    }

    /** The names of the index files in the shared folder, which jdtls fills under a folder of the index version. */
    private static List<String> sharedIndexes(Path indexes) throws IOException {
        return Files.isDirectory(indexes) ? indexesIn(indexes, 2) : List.of();
    }

    private static List<String> indexesIn(Path folder, int depth) throws IOException {
        try (Stream<Path> files = Files.walk(folder, depth)) {
            return files.filter(Files::isRegularFile)
                    .map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".index"))
                    .sorted()
                    .toList();
        }
    }

    /** The first jdtls copies the indexes to the shared folder once its import is over: it does so after ServiceReady. */
    private static List<String> awaitSharedIndexes(Path indexes) throws Exception {
        long end = System.nanoTime() + PATIENCE.toNanos();
        while (System.nanoTime() < end && sharedIndexes(indexes).size() < 2) {
            Thread.sleep(500);
        }
        return sharedIndexes(indexes);
    }

    /** What is left of the shared indexes in the data folder, once jdtls has had time to drop them in favour of them. */
    private static List<String> awaitNoneOf(List<String> shared, Path data) throws Exception {
        long end = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        List<String> kept = localIndexes(data).stream().filter(shared::contains).toList();
        while (System.nanoTime() < end && !kept.isEmpty()) {
            Thread.sleep(500);
            kept = localIndexes(data).stream().filter(shared::contains).toList();
        }
        return kept;
    }

    /**
     * The first jdtls builds the indexes of the JDK and of the jar in its own data folder, and copies them to the shared
     * folder. The second one is asked for symbols, which it answers once its own indexing is over; it then has none of
     * those two indexes in its data folder. A jdtls that builds an index keeps it (the copy to the shared folder does
     * not remove it), so the absence is not a matter of timing.
     */
    @Test
    void aSecondJdtlsUsesTheIndexesTheFirstSharedInsteadOfBuildingThemAgain() throws Exception {
        Path library = library();
        Path indexes = directory.resolve("indexes");
        List<String> shared;
        try (JdtlsSession first = start(project("first", library), indexes)) {
            shared = awaitSharedIndexes(indexes);
            assertThat(shared).as("the indexes the first jdtls shared").hasSize(2);
            assertThat(localIndexes(first.dataDirectory()))
                    .as("the indexes the first jdtls built in its own data folder")
                    .containsAll(shared);
        }

        try (JdtlsSession second = start(project("second", library), indexes)) {
            second.server()
                    .getWorkspaceService()
                    .symbol(new WorkspaceSymbolParams("Main"))
                    .get(PATIENCE.toSeconds(), TimeUnit.SECONDS);

            assertThat(awaitNoneOf(shared, second.dataDirectory()))
                    .as("the shared indexes that the second jdtls built again in its own data folder")
                    .isEmpty();
        }
        assertThat(sharedIndexes(indexes))
                .as("the shared indexes, still the same")
                .isEqualTo(shared);
    }
}
