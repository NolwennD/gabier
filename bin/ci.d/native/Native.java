import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Stage {@code native} of the local CI (see {@code bin/ci}): builds the native cli and runs the binary alone, in an
 * empty directory with an empty environment, so that it cannot lean on anything of the checkout, then against the
 * installed daemon on a socket in a temporary directory ({@code GABIER_SOCKET}, never the default path of the user),
 * served by the fake language server so that no jdtls runs in this stage. The checks are in the classes of this folder,
 * one per area: {@code CliChecks} and {@code McpChecks} for the cli alone, and for the daemon the classes that extend
 * {@code Checks} ({@code LifecycleChecks}, {@code DiagnosticsChecks}, {@code RewriteChecks}, {@code ExtractChecks},
 * {@code MoveChecks}, {@code QueryChecks}), run by {@code Session.check}. A command that is added gets its check in the
 * class of its area, and its call in {@code Session.check}. It runs in the current directory, the checkout under
 * verification, and starts and signals only the processes it started, by their handle, and the daemons the cli started,
 * by the pid their {@code status} gave.
 */
public final class Native extends Support {

    public static void main(String[] args) throws Exception {
        String skip = appliesGraalvm(Path.of("modules/cli/build.gradle.kts")) ? null : "no native build yet";
        // The native cli is built from the cli and what it requires at run time (protocol, shared) and from the build;
        // the daemon it talks to is built from the daemon and what it requires (core, protocol, shared).
        if (skip == null
                && !reachedBy(
                        "modules/cli/",
                        "modules/protocol/",
                        "modules/shared/",
                        "modules/daemon/",
                        "modules/core/",
                        "build-logic/",
                        "gradle/",
                        "settings.gradle.kts",
                        "build.gradle.kts",
                        "gradle.properties",
                        "bin/ci.d/40-native",
                        "bin/ci.d/native/")) {
            skip = "no changed file reaches the native cli or the daemon (bin/ci --all forces it)";
        }
        if (args.length > 0 && args[0].equals("--describe")) {
            System.out.println(
                    "native cli, run alone in an empty directory (version, help, the guide and a question to it,"
                        + " init-agent in a temporary project, mcp answering server/discover (with its instructions),"
                        + " tools/list and the guide tool over a pipe, then line by line against a daemon that holds a"
                        + " call: ping and status answered while it waits, usage error as text and JSON), then against"
                        + " the real daemon (status, stop, no daemon, stale socket, second daemon, SIGTERM,"
                        + " diagnostics with no jdtls to start and with the fake language server, diagnostics --watch,"
                        + " rename, organize-imports, cleanup, extract-variable, extract-method, extract-constant,"
                        + " extract-field, move-type-to-file, move, inline and affected-tests included, the daemon"
                        + " started on demand, start and start --wait)");
            if (skip != null) {
                System.out.println("skip: " + skip);
            }
            return;
        }
        if (skip != null) {
            System.out.println("skip: " + skip);
            System.exit(77);
        }
        System.exit(nativeCli(Path.of(".").toAbsolutePath().normalize()));
    }

    /** True when a changed file starts with one of the prefixes, or when the changed files are unknown. */
    private static boolean reachedBy(String... prefixes) {
        String changed = System.getenv("GABIER_CI_CHANGED");
        if (changed == null) {
            return true;
        }
        return changed.lines().anyMatch(path -> Arrays.stream(prefixes).anyMatch(path::startsWith));
    }

    /** The native build exists once the build file of the cli applies the GraalVM plugin. */
    private static boolean appliesGraalvm(Path buildFile) throws IOException {
        return Files.isRegularFile(buildFile) && Files.readString(buildFile).contains("graalvm");
    }

    private static int nativeCli(Path root) throws IOException, InterruptedException {
        int status = run(
                "./gradlew",
                ":cli:nativeCompile",
                ":daemon:installDist",
                ":daemon:installFakeServed",
                "--console=plain");
        if (status != 0) {
            return status;
        }
        Path binary = root.resolve("modules/cli/build/native/nativeCompile/gabier");
        Path daemon = root.resolve("modules/daemon/build/install/gabier-daemon/bin/gabier-daemon");
        Path fakeDaemon = root.resolve("modules/daemon/build/install/gabier-daemon-fake/bin/gabier-daemon-fake");
        if (!Files.isExecutable(binary)) {
            System.err.println("Missing native binary: " + binary);
            return 1;
        }
        if (!Files.isExecutable(daemon)) {
            System.err.println("Missing installed daemon: " + daemon);
            return 1;
        }
        if (!Files.isExecutable(fakeDaemon)) {
            System.err.println("Missing installed daemon served by the fake language server: " + fakeDaemon);
            return 1;
        }
        Path empty = Files.createTempDirectory("gabier-ci");
        Path copy = empty.resolve("gabier");
        try {
            Files.copy(binary, copy);
            int failures = CliChecks.version(copy, empty)
                    + CliChecks.usageError(copy, empty, false)
                    + CliChecks.usageError(copy, empty, true)
                    + CliChecks.help(copy, empty)
                    + CliChecks.initAgent(copy, empty)
                    + McpChecks.mcp(copy, empty)
                    + McpChecks.mcpWaiting(copy, empty)
                    + CliChecks.socketOfTheProject(copy, empty);
            failures += new Session(copy, daemon, fakeDaemon, empty).check();
            return failures == 0 ? 0 : 1;
        } finally {
            Files.deleteIfExists(copy);
            deleteTree(empty);
        }
    }
}
