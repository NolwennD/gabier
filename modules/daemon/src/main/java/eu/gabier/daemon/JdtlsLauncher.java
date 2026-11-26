package eu.gabier.daemon;

import eu.gabier.protocol.SocketLocation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Starts the jdtls launcher of the distribution, which talks the protocol on its standard input and output.
 *
 * @param launcher the {@code jdtls} script of the distribution
 * @param javaHome the JDK the launcher must run jdtls with, so that it does not depend on the shell's {@code java}
 * @param jvmArguments the options of the JVM jdtls runs in ({@code -Dname=value}), given to the launcher as {@code
 *     --jvm-arg=<option>}
 * @param cache the folder gabier caches in ({@link SharedIndexes#cache}), where the workspace of a project is kept;
 *     none for a jdtls whose workspace is never kept
 * @param output receives each line the launcher writes on its standard error, as it is written: the daemon writes them
 *     to its log, dated
 */
record JdtlsLauncher(
        Path launcher, Path javaHome, List<String> jvmArguments, Optional<Path> cache, Consumer<String> output)
        implements JdtlsStarter {

    JdtlsLauncher(Path launcher, Path javaHome, List<String> jvmArguments) {
        this(launcher, javaHome, jvmArguments, Optional.empty());
    }

    JdtlsLauncher(Path launcher, Path javaHome, List<String> jvmArguments, Optional<Path> cache) {
        this(launcher, javaHome, jvmArguments, cache, System.err::println);
    }

    /**
     * The workspace of the project in {@code <cache>/jdtls-<version>/workspaces/<project>}: a workspace of one version
     * of jdtls is not readable by another, so there is one per version; and it is kept for the JDK jdtls runs under,
     * since a project bound to another JDK is built again. None without a cache folder, or for a jdtls whose
     * distribution tells no version.
     */
    @Override
    public Optional<KeptWorkspace> keptWorkspace(Path projectRoot) {
        return cache.flatMap(folder -> SharedIndexes.version(launcher)
                .map(version -> new KeptWorkspace(
                        folder.resolve("jdtls-" + version)
                                .resolve("workspaces")
                                .resolve(SocketLocation.projectKey(projectRoot)),
                        "jdtls " + version + ", Java " + System.getProperty("java.runtime.version") + " in " + javaHome,
                        projectRoot)));
    }

    @Override
    public JdtlsConnection start(Path dataDirectory) throws IOException {
        List<String> line = new ArrayList<>(List.of(launcher.toString(), "-data", dataDirectory.toString()));
        jvmArguments.forEach(argument -> line.add("--jvm-arg=" + argument));
        ProcessBuilder command = new ProcessBuilder(line);
        command.environment().put("JAVA_HOME", javaHome.toString());
        Process process = command.start();
        OutputRelay.relayInBackground("jdtls-output", process.getErrorStream(), output);
        return new JdtlsConnection(process, process.getInputStream(), process.getOutputStream());
    }
}
