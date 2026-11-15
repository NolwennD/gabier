package eu.gabier.daemon;

import eu.gabier.protocol.Directories;
import eu.gabier.protocol.SocketLocation;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The AOT cache (JEP 483: the classes of the dependency jars loaded and linked ahead of time) that the JVM of the tests
 * starts from, made when it is missing and never used when it is stale.
 *
 * <p>A cache is made by a JVM of its own, on the dependency jars and the runner jar alone (a class folder on the class
 * path of the creation makes it fail), which runs the runner with no test: the classes of the launcher and the engines it
 * loads are the ones cached. It is kept in {@code <cache>/<project>/<key>.aot}, where the key is made of what a cache is
 * valid for: the JDK build (the {@code release} file of its home), and the path, size and date of each jar. The JDK 25
 * and 26 do not check the jars a cache was made from (a rebuilt jar runs from the old cache with no warning), so the key
 * is the only check: a jar that changed gives another key, so another cache. A project keeps the cache of its current key
 * only, and the folders of the projects that no longer exist are removed.
 *
 * <p>A cache that cannot be made is never an error: the tests start with no cache, the reason is logged and kept in
 * {@code <key>.refused}, so that the same key is not tried again (a jar, the JDK or the runner that changes is another
 * key).
 */
final class AotCache {

    /** The setting that asks for the caches: {@value #ON} makes the daemon start the JVM of the tests from one. */
    static final String SETTING = "GABIER_TEST_AOT_CACHE";

    static final String ON = "on";

    /** The first JDK whose {@code -XX:AOTCacheOutput} makes the cache in one step. */
    static final int FIRST_JAVA = 25;

    /** How long a JVM may take to make a cache: it takes some seconds, and ten times that on a loaded machine. */
    private static final Duration CREATION_LIMIT = Duration.ofMinutes(2);

    private static final String ROOT = "root";
    private static final Pattern JAVA_VERSION = Pattern.compile("^JAVA_VERSION=\"(\\d+)", Pattern.MULTILINE);

    private final Path folder;
    private final Duration creationLimit;
    private final Consumer<String> log;

    /**
     * Creates the caches of a user.
     *
     * @param folder where the caches are kept, a folder of its own in the cache of gabier
     * @param creationLimit how long a JVM may take to make one
     * @param log receives a line for each reason a run starts with no cache
     */
    AotCache(Path folder, Duration creationLimit, Consumer<String> log) {
        this.folder = folder;
        this.creationLimit = creationLimit;
        this.log = log;
    }

    /**
     * The caches of a daemon whose environment asks for them ({@link #SETTING} is {@value #ON}), kept in the folder {@code
     * test-jvm} of the cache of gabier ({@code GABIER_CACHE}, ...); none otherwise, so that the tests start as they did.
     */
    static Optional<AotCache> asked(Map<String, String> environment, Path home, Consumer<String> log) {
        if (!ON.equals(environment.get(SETTING))) {
            return Optional.empty();
        }

        return Optional.of(
                new AotCache(SharedIndexes.cache(environment, home).resolve("test-jvm"), CREATION_LIMIT, log));
    }

    /**
     * The cache to start the JVM of the tests of a project with, made now when there is none for the JDK and the jars.
     *
     * @param java the {@code java} launcher the tests run with
     * @param libraries the dependency jars, in their order on the class path of the tests
     * @param runner the jar that holds {@link TestRunnerMain}, which comes right after the libraries
     * @param projectRoot the root of the project the tests are of
     * @return none when there is nothing to cache or the cache cannot be made: the tests then run with no cache
     */
    Optional<Path> forRun(Path java, List<Path> libraries, Path runner, Path projectRoot) {
        if (libraries.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(cacheFor(java, libraries, runner, projectRoot));
        } catch (NoCache | IOException | UncheckedIOException reason) {
            log.accept("the tests start with no AOT cache: " + reason.getMessage());

            return Optional.empty();
        }
    }

    private Path cacheFor(Path java, List<Path> libraries, Path runner, Path projectRoot) throws IOException, NoCache {
        if (!Files.isRegularFile(runner)) {
            throw new NoCache("the daemon does not run from a jar (" + runner + ")");
        }
        Path home = java.toRealPath().resolve("../..").normalize();
        String key = key(releaseOf(home), home, withRunner(libraries, runner));
        Path project = folder.resolve(SocketLocation.projectKey(projectRoot));
        Path cache = project.resolve(key + ".aot");
        if (Files.isRegularFile(cache)) {
            return cache;
        }
        Path refused = project.resolve(key + ".refused");
        if (Files.isRegularFile(refused)) {
            throw new NoCache("it was not made before: " + Files.readString(refused));
        }
        make(java, withRunner(libraries, runner), projectRoot, project, key);

        return cache;
    }

    /** The release file of the JDK, which says its build; the JDK before the first that makes a cache has none to use. */
    private static String releaseOf(Path home) throws IOException, NoCache {
        String release = Files.readString(home.resolve("release"));
        var version = JAVA_VERSION.matcher(release);
        if (!version.find() || Integer.parseInt(version.group(1)) < FIRST_JAVA) {
            throw new NoCache("the JDK " + home + " is older than " + FIRST_JAVA);
        }

        return release;
    }

    /** What a cache is valid for: the JDK build and, in order, each jar with the size and date it has. */
    private static String key(String release, Path home, List<Path> jars) throws IOException {
        var material = new StringBuilder(release).append(home);
        for (Path jar : jars) {
            material.append('\n')
                    .append(jar.toAbsolutePath().normalize())
                    .append('\t')
                    .append(Files.size(jar))
                    .append('\t')
                    .append(Files.getLastModifiedTime(jar).toMillis());
        }

        return UUID.nameUUIDFromBytes(material.toString().getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
    }

    private static List<Path> withRunner(List<Path> libraries, Path runner) {
        List<Path> jars = new ArrayList<>(libraries);
        jars.add(runner);

        return jars;
    }

    /** Makes the cache of the key in the folder of the project, after removing what is not the project's current. */
    private void make(Path java, List<Path> jars, Path projectRoot, Path project, String key)
            throws IOException, NoCache {
        Files.createDirectories(project);
        removeWhatIsStale(project, key);
        Files.writeString(project.resolve(ROOT), projectRoot.toString());
        Path made = project.resolve(key + "." + System.nanoTime() + ".tmp");
        try {
            runCreation(java, jars, projectRoot, made);
            Files.move(made, project.resolve(key + ".aot"), StandardCopyOption.ATOMIC_MOVE);
        } catch (NoCache | IOException failure) {
            Files.writeString(project.resolve(key + ".refused"), String.valueOf(failure.getMessage()));
            throw failure;
        } finally {
            Files.deleteIfExists(made);
            Files.deleteIfExists(Path.of(made + ".log"));
            Files.deleteIfExists(Path.of(made + ".report"));
        }
    }

    /**
     * The JVM that makes the cache: the runner with no test class, on the jars. Its output is kept in a file, and said
     * when it fails.
     */
    private void runCreation(Path java, List<Path> jars, Path projectRoot, Path made) throws IOException, NoCache {
        Path output = Path.of(made + ".log");
        var command = new ArrayList<String>();
        command.add(java.toString());
        command.add("-XX:AOTCacheOutput=" + made);
        command.add("-cp");
        command.add(jars.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
        command.add(TestRunnerMain.class.getName());
        command.add(made + ".report");
        command.add("");
        Process process = new ProcessBuilder(command)
                .directory(projectRoot.toFile())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        if (!endsIn(process)) {
            throw new NoCache("making it took more than " + creationLimit.toSeconds() + " seconds");
        }
        if (process.exitValue() != 0 || !Files.isRegularFile(made)) {
            throw new NoCache("the JVM that makes it ended with " + process.exitValue() + ": " + lastLineOf(output));
        }
    }

    /** True when the process ended in time; else it is killed, with what it started, and so it is when interrupted. */
    private boolean endsIn(Process process) throws NoCache {
        try {
            if (process.waitFor(creationLimit.toMillis(), TimeUnit.MILLISECONDS)) {
                return true;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            ProcessTestRunner.kill(process);
            throw new NoCache("the daemon was interrupted");
        }
        ProcessTestRunner.kill(process);

        return false;
    }

    private static String lastLineOf(Path output) throws IOException {
        return Files.readAllLines(output).stream().reduce((first, last) -> last).orElse("no output");
    }

    /** What the cache of the project does not keep: another key's files, and the folders of projects that are gone. */
    private void removeWhatIsStale(Path project, String key) throws IOException {
        for (File entry : filesOf(project)) {
            if (!entry.getName().equals(ROOT) && !entry.getName().startsWith(key + ".")) {
                Files.delete(entry.toPath());
            }
        }
        for (File other : filesOf(folder)) {
            if (!other.toPath().equals(project) && isOfAGoneProject(other.toPath())) {
                Directories.delete(other.toPath());
            }
        }
    }

    private static List<File> filesOf(Path directory) {
        return Arrays.asList(Optional.ofNullable(directory.toFile().listFiles()).orElseGet(() -> new File[0]));
    }

    private static boolean isOfAGoneProject(Path other) throws IOException {
        Path root = other.resolve(ROOT);

        return Files.isDirectory(other, LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(root, LinkOption.NOFOLLOW_LINKS)
                && Files.notExists(Path.of(Files.readString(root)));
    }

    /** The reason there is no cache: told in the log, never an error of the tests. */
    private static final class NoCache extends Exception {

        private static final long serialVersionUID = 1L;

        private NoCache(String reason) {
            super(reason);
        }
    }
}
