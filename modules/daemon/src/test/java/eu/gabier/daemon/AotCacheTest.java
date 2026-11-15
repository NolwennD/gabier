package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.SocketLocation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The caches of the JVM of the tests, made by fake JDKs: a folder with a {@code release} file and a {@code java} script
 * that writes the cache it is asked for and keeps its arguments. What a real JVM does with the cache is
 * {@link ProcessTestRunnerAotCacheTest}.
 */
class AotCacheTest {

    private static final String MAKES_THE_CACHE = """
        for a in "$@"; do
          case "$a" in
            -XX:AOTCacheOutput=*) printf made > "${a#-XX:AOTCacheOutput=}" ;;
          esac
        done
        """;

    @TempDir
    Path folder;

    private final List<String> log = new ArrayList<>();
    private Path caches;
    private Path libraryA;
    private Path libraryB;
    private Path runner;
    private Path root;
    private Path java;
    private AotCache cache;

    @BeforeEach
    void setUp() throws IOException {
        caches = folder.resolve("caches");
        libraryA = Files.writeString(folder.resolve("a.jar"), "a1");
        libraryB = Files.writeString(folder.resolve("b.jar"), "b1");
        runner = Files.writeString(folder.resolve("daemon.jar"), "d1");
        root = Files.createDirectories(folder.resolve("project"));
        java = jdk("jdk25", "25.0.2", MAKES_THE_CACHE);
        cache = new AotCache(caches, Duration.ofSeconds(30), log::add);
    }

    /** A JDK whose {@code java} runs the script, after noting its arguments in {@code calls}. */
    private Path jdk(String name, String version, String script) throws IOException {
        Path home = Files.createDirectories(folder.resolve(name));
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"" + version + "\"\n");

        return executable(
                Files.createDirectories(home.resolve("bin")).resolve("java"),
                "#!/bin/sh\necho \"$@\" >> " + folder.resolve("calls") + "\n" + script);
    }

    private static Path executable(Path file, String content) throws IOException {
        Files.writeString(file, content);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));

        return file;
    }

    private Optional<Path> cacheOf(Path... libraries) {
        return cache.forRun(java, List.of(libraries), runner, root);
    }

    private Optional<Path> cache() {
        return cacheOf(libraryA, libraryB);
    }

    private List<String> calls() throws IOException {
        Path calls = folder.resolve("calls");

        return Files.exists(calls) ? Files.readAllLines(calls) : List.of();
    }

    private List<Path> cachesOf(Path projectRoot) throws IOException {
        try (Stream<Path> files = Files.list(caches.resolve(SocketLocation.projectKey(projectRoot)))) {
            return files.filter(file -> file.toString().endsWith(".aot")).toList();
        }
    }

    @Test
    void makesTheCacheOnTheDependencyJarsAndTheRunnerWithNoTest() throws IOException {
        var made = cache();

        assertThat(made).hasValueSatisfying(file -> assertThat(file).isRegularFile());
        assertThat(calls()).singleElement().satisfies(call -> {
            assertThat(call).contains("-XX:AOTCacheOutput=");
            assertThat(call).contains("-cp " + libraryA + ":" + libraryB + ":" + runner + " ");
            assertThat(call).contains(TestRunnerMain.class.getName());
        });
        assertThat(log).isEmpty();
    }

    @Test
    void givesTheSameCacheAgainWithoutMakingAnother() throws IOException {
        var first = cache();
        var second = cache();

        assertThat(second).isEqualTo(first);
        assertThat(calls()).hasSize(1);
    }

    @Test
    void isAskedForBySettingItToOnAndKeepsItsCachesInTheCacheFolderOfGabier() throws IOException {
        var environment =
                Map.of("GABIER_CACHE", folder.resolve("gabier-cache").toString(), "GABIER_TEST_AOT_CACHE", "on");

        var asked = AotCache.asked(environment, folder.resolve("home"), log::add);

        assertThat(asked).isPresent();
        var made = asked.orElseThrow().forRun(java, List.of(libraryA), runner, root);
        assertThat(made)
                .hasValueSatisfying(file -> assertThat(file)
                        .startsWith(folder.resolve("gabier-cache/test-jvm"))
                        .isRegularFile());
    }

    @Test
    void isNotAskedForWhateverElseTheSettingSays() {
        var home = folder.resolve("home");

        assertThat(AotCache.asked(Map.of(), home, log::add)).isEmpty();
        assertThat(AotCache.asked(Map.of("GABIER_TEST_AOT_CACHE", "off"), home, log::add))
                .isEmpty();
        assertThat(AotCache.asked(Map.of("GABIER_TEST_AOT_CACHE", ""), home, log::add))
                .isEmpty();
    }

    @Test
    void makesAnotherCacheWhenADependencyJarChangesInSize() throws IOException {
        var before = cache();

        Files.writeString(libraryA, "a-longer-one");

        assertANewCacheReplaces(before);
    }

    @Test
    void makesAnotherCacheWhenADependencyJarChangesInDateAlone() throws IOException {
        var before = cache();

        Files.setLastModifiedTime(
                libraryB,
                FileTime.fromMillis(Files.getLastModifiedTime(libraryB).toMillis() + 5000));

        assertANewCacheReplaces(before);
    }

    @Test
    void makesAnotherCacheWhenTheJarsAreInAnotherOrder() throws IOException {
        var before = cache();

        var after = cacheOf(libraryB, libraryA);

        assertThat(after).isNotEqualTo(before);
    }

    @Test
    void makesAnotherCacheWhenADependencyJarIsAnotherFile() throws IOException {
        var before = cache();
        Path moved = Files.copy(libraryA, folder.resolve("moved.jar"));
        Files.setLastModifiedTime(moved, Files.getLastModifiedTime(libraryA));

        var after = cacheOf(moved, libraryB);

        assertThat(after).isNotEmpty().isNotEqualTo(before);
        assertThat(cachesOf(root)).hasSize(1);
    }

    @Test
    void makesAnotherCacheWhenTheRunnerJarChanges() throws IOException {
        var before = cache();

        Files.writeString(runner, "a-new-build-of-the-daemon");

        assertANewCacheReplaces(before);
    }

    @Test
    void makesAnotherCacheWhenTheJdkIsAnotherBuildAtTheSamePath() throws IOException {
        var before = cache();

        Files.writeString(java.getParent().getParent().resolve("release"), "JAVA_VERSION=\"25.0.3\"\n");

        assertANewCacheReplaces(before);
    }

    private void assertANewCacheReplaces(Optional<Path> before) throws IOException {
        var after = cache();

        assertThat(after).isNotEmpty().isNotEqualTo(before);
        assertThat(calls()).hasSize(2);
        assertThat(cachesOf(root)).containsExactly(after.orElseThrow());
    }

    @Test
    void makesNoCacheWhenThereIsNoLibraryToCache() throws IOException {
        assertThat(cacheOf()).isEmpty();
        assertThat(calls()).isEmpty();
    }

    @Test
    void makesNoCacheOfAJdkBeforeTheFirstThatMakesItInOneStep() throws IOException {
        java = jdk("jdk24", "24.0.1", MAKES_THE_CACHE);

        assertThat(cache()).isEmpty();
        assertThat(calls()).isEmpty();
        assertThat(log).singleElement().asString().contains("older than " + AotCache.FIRST_JAVA);
    }

    @Test
    void makesNoCacheOfAJdkWhoseVersionIsNotKnown() throws IOException {
        Files.writeString(java.getParent().getParent().resolve("release"), "IMPLEMENTOR=\"nobody\"\n");

        assertThat(cache()).isEmpty();
        assertThat(log).hasSize(1);
    }

    @Test
    void makesNoCacheOfAJdkWithNoReleaseFile() throws IOException {
        Files.delete(java.getParent().getParent().resolve("release"));

        assertThat(cache()).isEmpty();
        assertThat(log).hasSize(1);
    }

    @Test
    void makesNoCacheWhenTheDaemonDoesNotRunFromAJar() throws IOException {
        Path classes = Files.createDirectories(folder.resolve("classes"));

        var made = cache.forRun(java, List.of(libraryA), classes, root);

        assertThat(made).isEmpty();
        assertThat(calls()).isEmpty();
        assertThat(log).singleElement().asString().contains("does not run from a jar");
    }

    @Test
    void makesNoCacheOfAJarThatIsGone() throws IOException {
        assertThat(cacheOf(folder.resolve("gone.jar"))).isEmpty();
        assertThat(log).hasSize(1);
    }

    @Test
    void makesNoCacheWhereItCannotWrite() throws IOException {
        Files.writeString(caches, "a file where the folder of the caches should be");

        assertThat(cache()).isEmpty();
        assertThat(calls()).isEmpty();
        assertThat(log).hasSize(1);
    }

    @Test
    void doesNotTryAgainACacheThatCouldNotBeMadeUntilAJarChanges() throws IOException {
        java = jdk("failing", "25.0.2", "echo 'Unrecognized VM option'\nexit 1\n");

        assertThat(cache()).isEmpty();
        assertThat(cache()).isEmpty();

        assertThat(calls()).hasSize(1);
        assertThat(log).hasSize(2);
        assertThat(log.get(0)).contains("ended with 1").contains("Unrecognized VM option");
        assertThat(log.get(1)).contains("was not made before");
        Files.writeString(libraryA, "a-longer-one");
        assertThat(cache()).isEmpty();
        assertThat(calls()).hasSize(2);
    }

    @Test
    void leavesNothingButTheMarkerWhenTheJvmFails() throws IOException {
        java = jdk("failing", "25.0.2", "exit 1\n");

        cache();

        try (Stream<Path> files = Files.list(caches.resolve(SocketLocation.projectKey(root)))) {
            assertThat(files.map(file -> file.getFileName().toString()))
                    .anyMatch(name -> name.endsWith(".refused"))
                    .noneMatch(name -> name.endsWith(".tmp") || name.endsWith(".log") || name.endsWith(".report"));
        }
    }

    @Test
    void makesNoCacheWhenTheJvmEndsWellWithoutWritingIt() throws IOException {
        java = jdk("silent", "25.0.2", "exit 0\n");

        assertThat(cache()).isEmpty();
        assertThat(log).singleElement().asString().contains("ended with 0");
    }

    @Test
    void stopsAJvmThatTakesTooLongToMakeTheCache() throws IOException {
        java = jdk("slow", "25.0.2", "sleep 30\n");
        var quick = new AotCache(caches, Duration.ofSeconds(1), log::add);
        long start = System.nanoTime();

        var made = quick.forRun(java, List.of(libraryA), runner, root);

        assertThat(made).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(20));
        assertThat(log).singleElement().asString().contains("more than 1 seconds");
    }

    @Test
    void stopsMakingTheCacheWhenTheCallerIsInterruptedAndKeepsTheInterruption()
            throws InterruptedException, IOException {
        java = jdk("slow-too", "25.0.2", "sleep 30\n");
        var result = new AtomicReference<Optional<Path>>();
        var interrupted = new AtomicBoolean();
        var caller = new Thread(() -> {
            result.set(cache());
            interrupted.set(Thread.currentThread().isInterrupted());
        });
        caller.start();
        Thread.sleep(1500);

        caller.interrupt();
        caller.join(Duration.ofSeconds(20));

        assertThat(result.get()).isEmpty();
        assertThat(interrupted).isTrue();
        assertThat(log).singleElement().asString().contains("interrupted");
    }

    @Test
    void removesTheFoldersOfTheProjectsThatAreGoneAndKeepsTheOthers() throws IOException {
        Path gone = Files.createDirectories(folder.resolve("gone-project"));
        Path alive = Files.createDirectories(folder.resolve("alive-project"));
        cache.forRun(java, List.of(libraryA), runner, gone);
        cache.forRun(java, List.of(libraryA), runner, alive);
        Files.delete(gone);

        cache.forRun(java, List.of(libraryA), runner, root);

        assertThat(caches.resolve(SocketLocation.projectKey(gone))).doesNotExist();
        assertThat(cachesOf(alive)).hasSize(1);
        assertThat(cachesOf(root)).hasSize(1);
    }

    @Test
    void leavesTheFileOfACreationOfTheSameKeyThatAnotherRunIsMaking() throws IOException {
        var made = cache().orElseThrow();
        Files.delete(made);
        var other = Files.writeString(
                made.resolveSibling(made.getFileName().toString().replace(".aot", ".1.tmp")), "x");

        cache();

        assertThat(other).exists();
    }

    @Test
    void leavesAloneWhatIsNotTheFolderOfAProject() throws IOException {
        cache();
        Files.writeString(caches.resolve("not-a-project"), "a file");
        Path noRoot = Files.createDirectories(caches.resolve("no-root-file"));

        cache.forRun(java, List.of(libraryA), runner, root);

        assertThat(caches.resolve("not-a-project")).exists();
        assertThat(noRoot).exists();
    }
}
