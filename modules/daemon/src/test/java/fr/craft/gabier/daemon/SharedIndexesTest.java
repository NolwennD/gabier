package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Where the indexes of the libraries that jdtls shares between projects are kept. No jdtls is started. */
class SharedIndexesTest {

    private static final Path HOME = Path.of("/home/someone");
    private static final String VERSION = "1.63.0.202610191322";

    @TempDir
    Path distribution;

    /** A distribution of jdtls as the archive of Eclipse unpacks: {@code bin/jdtls} and {@code plugins/*.jar}. */
    private Path launcherOfVersion(String version) throws IOException {
        Files.createDirectories(distribution.resolve("bin"));
        Files.createDirectories(distribution.resolve("plugins"));
        Files.createFile(distribution.resolve("plugins/org.eclipse.jdt.core_3.48.0.v20261007-1246.jar"));
        Files.createFile(distribution.resolve("plugins/org.eclipse.jdt.ls.core_" + version + ".jar"));
        return Files.createFile(distribution.resolve("bin/jdtls"));
    }

    private Path launcher() throws IOException {
        return launcherOfVersion(VERSION);
    }

    @Test
    void isInTheCacheOfGabierInTheHomeOfTheUserByDefault() throws IOException {
        var indexes = SharedIndexes.of(launcher(), Map.of(), HOME);

        assertThat(indexes.orElseThrow().folder())
                .isEqualTo(HOME.resolve(".cache/gabier/jdtls-" + VERSION + "/indexes"));
    }

    @Test
    void isInTheXdgCacheFolderWhenThereIsOne() throws IOException {
        var indexes = SharedIndexes.of(launcher(), Map.of("XDG_CACHE_HOME", "/var/cache/someone"), HOME);

        assertThat(indexes.orElseThrow().folder())
                .isEqualTo(Path.of("/var/cache/someone/gabier/jdtls-" + VERSION + "/indexes"));
    }

    @Test
    void isInTheCacheFolderOfGabierWhenTheVariableNamesOne() throws IOException {
        var environment = Map.of("GABIER_CACHE", "/data/gabier-cache", "XDG_CACHE_HOME", "/var/cache/someone");

        var indexes = SharedIndexes.of(launcher(), environment, HOME);

        assertThat(indexes.orElseThrow().folder())
                .isEqualTo(Path.of("/data/gabier-cache/jdtls-" + VERSION + "/indexes"));
    }

    @Test
    void ignoresACacheFolderThatIsBlankOrRelative() throws IOException {
        var environment = Map.of("GABIER_CACHE", " ", "XDG_CACHE_HOME", "relative/cache");

        var indexes = SharedIndexes.of(launcher(), environment, HOME);

        assertThat(indexes.orElseThrow().folder())
                .isEqualTo(HOME.resolve(".cache/gabier/jdtls-" + VERSION + "/indexes"));
    }

    @Test
    void hasAFolderForEachVersionOfJdtlsBecauseTheirIndexesDoNotFitEachOther() throws IOException {
        var newer = SharedIndexes.of(launcherOfVersion("1.64.0.202611010000"), Map.of(), HOME);

        assertThat(newer.orElseThrow().folder())
                .isEqualTo(HOME.resolve(".cache/gabier/jdtls-1.64.0.202611010000/indexes"));
    }

    @Test
    void givesJdtlsTheFolderAsTheSharedIndexLocationProperty() throws IOException {
        var indexes = SharedIndexes.of(launcher(), Map.of("GABIER_CACHE", "/data/gabier-cache"), HOME);

        assertThat(indexes.orElseThrow().jvmArgument())
                .isEqualTo("-Djdt.core.sharedIndexLocation=/data/gabier-cache/jdtls-" + VERSION + "/indexes");
    }

    @Test
    void isNotThereWhenTheDistributionHasNoVersionToTellIt() throws IOException {
        Files.createDirectories(distribution.resolve("bin"));
        Files.createDirectories(distribution.resolve("plugins"));
        Files.createFile(distribution.resolve("plugins/org.eclipse.jdt.core_3.48.0.v20261007-1246.jar"));
        Files.createFile(distribution.resolve("plugins/org.eclipse.jdt.ls.core_1.63.0.202610191322.txt"));
        var launcher = Files.createFile(distribution.resolve("bin/jdtls"));

        assertThat(SharedIndexes.of(launcher, Map.of(), HOME)).isEmpty();
    }

    @Test
    void isNotThereWhenTheLauncherIsNotInADistribution() {
        assertThat(SharedIndexes.of(Path.of("/no/such/bin/jdtls"), Map.of(), HOME))
                .isEmpty();
    }
}
