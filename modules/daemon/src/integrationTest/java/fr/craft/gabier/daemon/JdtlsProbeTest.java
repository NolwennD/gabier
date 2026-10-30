package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.Test;

/**
 * Starts a real jdtls on this project and waits until it has really imported it. Not run by {@code check} (which only
 * compiles it): run it with {@code ./gradlew jdtlsProbe}, with {@code GABIER_JDTLS} set to the jdtls launcher (mise
 * exports it). It fails at once when jdtls exits or sends a status {@code Error}; its three waits share one deadline of
 * ten minutes.
 */
class JdtlsProbeTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(10);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void importsEveryModuleOfTheProject() throws Exception {
        Path launcher = launcher();
        Path projectRoot = Path.of(System.getProperty("gabier.projectRoot"));
        List<Path> modules = modulesOf(projectRoot);
        assertThat(modules)
                .as("modules under %s: the probe would wait for nothing", projectRoot.resolve("modules"))
                .isNotEmpty();

        Path data = Files.createTempDirectory("gabier-jdtls-data-");
        Process jdtls = null;
        LanguageServer server = null;
        try {
            // The launcher runs the java of JAVA_HOME: the one that runs this test, not whatever the shell has.
            ProcessBuilder launch = new ProcessBuilder(launcher.toString(), "-data", data.toString())
                    .redirectError(ProcessBuilder.Redirect.INHERIT);
            launch.environment().put("JAVA_HOME", System.getProperty("java.home"));
            jdtls = launch.start();
            System.out.println("jdtls started, pid " + jdtls.pid() + ", data " + data);

            JdtlsClient client = new JdtlsClient();
            var connection = LSPLauncher.createClientLauncher(client, jdtls.getInputStream(), jdtls.getOutputStream());
            connection.startListening();
            server = connection.getRemoteProxy();

            var initialize = new InitializeParams();
            initialize.setRootUri(projectRoot.toUri().toString());
            initialize.setCapabilities(new ClientCapabilities());
            initialize.setInitializationOptions(Map.of());
            var jdtlsServer = server;
            var watch = new ProbeWatch(jdtls, client.error(), TIMEOUT);
            watch.await("the answer to initialize", () -> jdtlsServer.initialize(initialize));
            // Telling jdtls that the client is ready is part of the wait: writing to a jdtls that died throws.
            watch.await("the language/status notification of type ServiceReady", () -> {
                jdtlsServer.initialized(new InitializedParams());
                return client.serviceReady();
            });
            List<String> projects = awaitProjects(watch, server, modules);
            System.out.println("jdtls knows the projects " + projects);
        } finally {
            try {
                stop(jdtls, server);
            } finally {
                delete(data);
            }
        }
    }

    /** The launcher is required: a missing one is a failure to fix, never a reason to skip the probe. */
    private static Path launcher() {
        String path = System.getenv("GABIER_JDTLS");
        if (path == null || path.isBlank()) {
            return fail("GABIER_JDTLS is not set: it must be the path of the jdtls launcher. Run `mise install` (it"
                    + " installs jdtls and exports the variable), then `mise exec -- ./gradlew jdtlsProbe`.");
        }
        Path launcher = Path.of(path);
        if (!Files.isRegularFile(launcher) || !Files.isExecutable(launcher)) {
            return fail("GABIER_JDTLS is " + path + ", which is not an executable file. "
                    + "It must be the path of the jdtls launcher: run `mise install` to install it.");
        }
        return launcher;
    }

    /** The folders under {@code modules/} that hold a {@code module-info.java}, which are the Gradle projects. */
    private static List<Path> modulesOf(Path projectRoot) throws IOException {
        try (Stream<Path> folders = Files.list(projectRoot.resolve("modules"))) {
            return folders.filter(folder -> Files.exists(folder.resolve("src/main/java/module-info.java")))
                    .map(folder -> folder.toAbsolutePath().normalize())
                    .sorted()
                    .toList();
        }
    }

    /** Polls {@code java.project.getAll} until jdtls lists every module, and returns what it listed. */
    private static List<String> awaitProjects(ProbeWatch watch, LanguageServer server, List<Path> modules)
            throws InterruptedException {
        List<String> projects = List.of();
        List<Path> missing = modules;
        while (true) {
            String what = "jdtls to list the modules " + modules + " with java.project.getAll (still missing " + missing
                    + ", it listed " + projects + ")";
            var command = new ExecuteCommandParams("java.project.getAll", List.of());
            Object answer = watch.await(what, () -> server.getWorkspaceService().executeCommand(command));
            projects = ((List<?>) answer).stream().map(String::valueOf).toList();
            missing = missingFrom(projects, modules);
            if (missing.isEmpty()) {
                return projects;
            }
            watch.pause(what, POLL_INTERVAL);
        }
    }

    private static List<Path> missingFrom(List<String> projects, List<Path> modules) {
        return modules.stream()
                .filter(module -> projects.stream().noneMatch(project -> isProjectOf(project, module)))
                .toList();
    }

    /** jdtls reports a project by the URI of its folder, which must be the folder of the module itself. */
    private static boolean isProjectOf(String project, Path module) {
        return Path.of(URI.create(project)).toAbsolutePath().normalize().equals(module);
    }

    /** Asks jdtls to leave, then ends whatever is left of its process tree: the launcher starts a java process. */
    private static void stop(Process jdtls, LanguageServer server) throws InterruptedException {
        if (jdtls == null) {
            return;
        }
        List<ProcessHandle> tree =
                Stream.concat(jdtls.descendants(), Stream.of(jdtls.toHandle())).toList();
        if (server != null) {
            try {
                server.shutdown().get(SHUTDOWN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                server.exit();
            } catch (ExecutionException | TimeoutException | RuntimeException e) {
                // A dead server makes the proxy throw an unchecked JsonRpcException: the kill below must still happen.
                System.out.println("jdtls did not shut down cleanly: " + e);
            }
        }
        jdtls.waitFor(SHUTDOWN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        tree.forEach(ProcessHandle::destroyForcibly);
        for (ProcessHandle process : tree) {
            try {
                process.onExit().get(SHUTDOWN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException e) {
                System.out.println("process " + process.pid() + " is still running: " + e);
            }
        }
    }

    private static void delete(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
    }
}
