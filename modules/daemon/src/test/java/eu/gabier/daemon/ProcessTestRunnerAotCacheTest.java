package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.testing.domain.TestReport;
import eu.gabier.testing.domain.TestRun;
import eu.gabier.testing.fixtures.CompiledSources;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The JVM of the tests started from an AOT cache, on a real JDK 25 ({@code gabier.testJdk}, the Java 25 of the build, not
 * the JDK this test happens to run on). JDK 25 and 26 do not check the jars a cache was made from: a jar that changed and
 * is found in the cache runs as it was, which these tests show cannot happen. The runner is a stub that loads
 * {@code lib.Marker} (the class of a dependency jar, so that the cache holds it) and reports its number as the number of
 * tests that passed.
 */
class ProcessTestRunnerAotCacheTest {

    private static final Duration LIMIT = Duration.ofSeconds(120);

    private static final String MARKER = """
        package lib;

        public final class Marker {
            public static int value() {
                return %d;
            }
        }
        """;

    private static final String STUB_RUNNER = """
        package eu.gabier.daemon;

        public final class TestRunnerMain {
            public static void main(String[] arguments) throws Exception {
                int value = (Integer) Class.forName("lib.Marker").getMethod("value").invoke(null);
                java.nio.file.Files.writeString(
                        java.nio.file.Path.of(arguments[0]), "passed\\t" + (value + %d) + "\\nskipped\\t0\\n");
            }
        }
        """;

    @TempDir
    Path project;

    @TempDir
    Path elsewhere;

    private final List<String> log = new ArrayList<>();
    private Path realHome;
    private Path classes;
    private Path caches;
    private Path classLoads;
    private Path arguments;

    @BeforeEach
    void setUp() throws IOException {
        realHome = Path.of(System.getProperty("gabier.testJdk"));
        classes = TestedProject.compiledIn(project);
        caches = elsewhere.resolve("caches");
        classLoads = elsewhere.resolve("class-load.log");
        arguments = elsewhere.resolve("arguments.log");
    }

    /**
     * A JDK that is the real one, seen through a script: it notes the arguments the tests start with, and logs the classes
     * the JVM of the tests loads and where from. The JVMs that make a cache are left alone, but for the options the test
     * adds to them.
     */
    private Path jdk(String creationOptions, String runOptions) throws IOException {
        Path home = Files.createDirectories(elsewhere.resolve("jdk"));
        Files.copy(realHome.resolve("release"), home.resolve("release"), StandardCopyOption.REPLACE_EXISTING);
        Path script = home.resolve("bin/java");
        Files.createDirectories(script.getParent());
        Files.writeString(script, """
            #!/bin/sh
            case "$*" in
              *AOTCacheOutput*) exec %1$s %2$s "$@" ;;
            esac
            echo "$@" >> %3$s
            exec %1$s -Xlog:class+load:file=%4$s %5$s "$@"
            """.formatted(
                        realHome.resolve("bin/java"), creationOptions, arguments, classLoads, runOptions));
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));

        return script;
    }

    private ProcessTestRunner cachedRunner(Path java, Path runnerJar) {
        return new ProcessTestRunner(java, runnerJar, Optional.of(new AotCache(caches, LIMIT, log::add)));
    }

    private static Path classesOfTheDaemon() {
        return Path.of(URI.create(TestRunnerMain.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toString()));
    }

    /** The classes of the daemon as a jar, which is what the daemon runs from. */
    private Path daemonJar() throws IOException {
        Path source = classesOfTheDaemon();
        if (Files.isRegularFile(source)) {
            return source;
        }
        Path jar = elsewhere.resolve("daemon.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar));
                Stream<Path> files = Files.walk(source)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                output.putNextEntry(new JarEntry(source.relativize(file).toString()));
                Files.copy(file, output);
                output.closeEntry();
            }
        }

        return jar;
    }

    private TestReport run(ProcessTestRunner runner, List<Path> libraries, String name) {
        List<Path> classpath = new ArrayList<>(List.of(classes));
        classpath.addAll(libraries);

        return runner.run(new TestRun(List.of(name), classpath, project, LIMIT));
    }

    private int cachesMade() throws IOException {
        if (!Files.exists(caches)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(caches)) {
            return (int) files.filter(file -> file.toString().endsWith(".aot")).count();
        }
    }

    private boolean loadedFromTheCache(String className) throws IOException {
        return Files.readAllLines(classLoads).stream()
                .anyMatch(line -> line.contains(" " + className + " ") && line.contains("shared objects file"));
    }

    @Test
    void startsTheTestsFromTheCacheItMadeAtTheFirstCallAndGivesTheReportOfAJvmWithNone() throws IOException {
        var runner = cachedRunner(jdk("", ""), daemonJar());

        var first = run(runner, TestedProject.junit(), "app.MixedTest");
        assertThat(cachesMade()).isEqualTo(1);
        var second = run(runner, TestedProject.junit(), "app.MixedTest");

        var plain = run(
                new ProcessTestRunner(realHome.resolve("bin/java"), daemonJar()),
                TestedProject.junit(),
                "app.MixedTest");
        assertThat(first).isEqualTo(plain);
        assertThat(second).isEqualTo(plain);
        assertThat(plain.passed()).isEqualTo(2);
        assertThat(cachesMade()).isEqualTo(1);
        assertThat(loadedFromTheCache("org.junit.platform.launcher.core.LauncherFactory"))
                .isTrue();
        assertThat(log).isEmpty();
    }

    @Test
    void putsTheDependencyJarsFirstThenTheRunnerThenWhatIsLeftOfTheClassPathInItsOrder() throws IOException {
        Path built = Files.writeString(project.resolve("built.jar"), "a jar the build of the project writes");
        var jars = TestedProject.junit();
        var runnerJar = daemonJar();

        cachedRunner(jdk("", ""), runnerJar)
                .run(new TestRun(
                        List.of("app.MixedTest"),
                        Stream.concat(Stream.of(classes, built), jars.stream()).toList(),
                        project,
                        LIMIT));

        var classPath = Files.readAllLines(arguments).getFirst().split(" ")[2];
        assertThat(classPath)
                .isEqualTo(String.join(
                        ":",
                        Stream.of(jars.stream(), Stream.of(runnerJar, classes, built))
                                .flatMap(paths -> paths)
                                .map(Path::toString)
                                .toList()));
    }

    @Test
    void neverStartsFromTheCacheOfADependencyJarThatChangedInSize() throws IOException {
        Path library =
                markerJar(Files.createDirectories(elsewhere.resolve("libs")).resolve("lib.jar"), 1);
        Path runnerJar = CompiledSources.jar(
                elsewhere.resolve("runner.jar"),
                Map.of("eu/gabier/daemon/TestRunnerMain.java", STUB_RUNNER.formatted(0)));
        var runner = cachedRunner(jdk("", ""), runnerJar);
        var dateBefore = Files.getLastModifiedTime(library);

        assertThat(run(runner, List.of(library), "any.Test").passed()).isEqualTo(1);
        assertThat(loadedFromTheCache("lib.Marker")).isTrue();
        markerJar(library, 1000);
        Files.setLastModifiedTime(library, dateBefore);

        assertThat(run(runner, List.of(library), "any.Test").passed()).isEqualTo(1000);
        assertThat(cachesMade()).isEqualTo(1);
    }

    /**
     * The jar of the marker with the value, packed without compression: two values give two jars of one size, so that a
     * change of date can be the only change.
     */
    private Path markerJar(Path jar, int value) throws IOException {
        Path built = Files.createTempDirectory(elsewhere, "marker");
        CompiledSources.compile(Map.of("lib/Marker.java", MARKER.formatted(value)), built, List.of());
        try (var output = new JarOutputStream(Files.newOutputStream(jar));
                Stream<Path> files = Files.walk(built)) {
            output.setLevel(0);
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                output.putNextEntry(new JarEntry(built.relativize(file).toString()));
                Files.copy(file, output);
                output.closeEntry();
            }
        }

        return jar;
    }

    @Test
    void neverStartsFromTheCacheOfADependencyJarThatChangedInDateAlone() throws IOException {
        Path library =
                markerJar(Files.createDirectories(elsewhere.resolve("libs")).resolve("lib.jar"), 1);
        Path runnerJar = CompiledSources.jar(
                elsewhere.resolve("runner.jar"),
                Map.of("eu/gabier/daemon/TestRunnerMain.java", STUB_RUNNER.formatted(0)));
        var runner = cachedRunner(jdk("", ""), runnerJar);
        var dateBefore = Files.getLastModifiedTime(library);
        long sizeBefore = Files.size(library);

        assertThat(run(runner, List.of(library), "any.Test").passed()).isEqualTo(1);
        assertThat(loadedFromTheCache("lib.Marker")).isTrue();
        markerJar(library, 2);
        assertThat(Files.size(library)).isEqualTo(sizeBefore);
        Files.setLastModifiedTime(library, FileTime.fromMillis(dateBefore.toMillis() + 10_000));

        assertThat(run(runner, List.of(library), "any.Test").passed()).isEqualTo(2);
        assertThat(cachesMade()).isEqualTo(1);
    }

    @Test
    void neverStartsFromTheCacheOfADaemonJarThatChanged() throws IOException {
        Path library =
                markerJar(Files.createDirectories(elsewhere.resolve("libs")).resolve("lib.jar"), 1);
        Path runnerJar = CompiledSources.jar(
                elsewhere.resolve("runner.jar"),
                Map.of("eu/gabier/daemon/TestRunnerMain.java", STUB_RUNNER.formatted(0)));
        var runner = cachedRunner(jdk("", ""), runnerJar);

        assertThat(run(runner, List.of(library), "any.Test").passed()).isEqualTo(1);
        assertThat(loadedFromTheCache("eu.gabier.daemon.TestRunnerMain")).isTrue();
        CompiledSources.jar(runnerJar, Map.of("eu/gabier/daemon/TestRunnerMain.java", STUB_RUNNER.formatted(41)));

        assertThat(run(runner, List.of(library), "any.Test").passed()).isEqualTo(42);
        assertThat(cachesMade()).isEqualTo(1);
    }

    @Test
    void runsTheTestsWithNoCacheWhenTheJvmCannotMakeOne() throws IOException {
        var runner = cachedRunner(jdk("-XX:NoSuchOptionOfTheJvm", ""), daemonJar());

        var report = run(runner, TestedProject.junit(), "app.MixedTest");

        assertThat(report.passed()).isEqualTo(2);
        assertThat(cachesMade()).isZero();
        assertThat(log).singleElement().asString().contains("the tests start with no AOT cache");
    }

    @Test
    void runsTheTestsWhenTheJvmCannotUseTheCacheItWasGiven() throws IOException {
        var runner = cachedRunner(jdk("", "-XX:+UseZGC"), daemonJar());

        var report = run(runner, TestedProject.junit(), "app.MixedTest");

        assertThat(report.passed()).isEqualTo(2);
        assertThat(report.failures()).hasSize(1);
        assertThat(cachesMade()).isEqualTo(1);
        assertThat(loadedFromTheCache("org.junit.platform.launcher.core.LauncherFactory"))
                .isFalse();
    }

    @Test
    void runsTheTestsWhenTheCacheCannotBeWritten() throws IOException {
        Files.writeString(caches, "a file where the folder of the caches should be, as a full disk would leave it");
        var runner = cachedRunner(jdk("", ""), daemonJar());

        var report = run(runner, TestedProject.junit(), "app.MixedTest");

        assertThat(report.passed()).isEqualTo(2);
        assertThat(log).singleElement().asString().contains("the tests start with no AOT cache");
    }

    @Test
    void runsTheTestsWhenTheDaemonDoesNotRunFromAJar() throws IOException {
        var runner = cachedRunner(jdk("", ""), classesOfTheDaemon());

        var report = run(runner, TestedProject.junit(), "app.MixedTest");

        assertThat(report.passed()).isEqualTo(2);
    }

    @Test
    void runsOnAJdkTooOldForTheCacheWithNoCache() throws IOException {
        Path java = jdk("", "");
        Files.writeString(java.getParent().getParent().resolve("release"), "JAVA_VERSION=\"24.0.1\"\n");

        var report = run(cachedRunner(java, daemonJar()), TestedProject.junit(), "app.MixedTest");

        assertThat(report.passed()).isEqualTo(2);
        assertThat(cachesMade()).isZero();
    }
}
