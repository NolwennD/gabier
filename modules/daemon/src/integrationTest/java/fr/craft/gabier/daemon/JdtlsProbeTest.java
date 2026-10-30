package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.Test;

/**
 * Starts a real jdtls on this project, waits until it has really imported it, then opens the sources and requires jdtls
 * to publish diagnostics for each of them (after its own {@code didOpen}), and an error for a deliberately broken file.
 * It judges the diagnostics only once jdtls has published nothing for {@link #QUIET}, none of them an error. Not run by
 * {@code check} (which only compiles it): run it with {@code ./gradlew jdtlsProbe}, with {@code GABIER_JDTLS} set to the jdtls launcher (mise
 * exports it). It fails at once when jdtls exits or sends a status {@code Error}; its waits share one deadline of ten
 * minutes.
 */
class JdtlsProbeTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(10);
    /** How long jdtls must have published nothing before the diagnostics are judged. */
    private static final Duration QUIET = Duration.ofSeconds(5);

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
    /** The line of the control file that assigns a String to an int (see {@code controlSource}). */
    private static final int CONTROL_LINE = 4;

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
            checkDiagnostics(watch, server, client.diagnostics(), projectRoot, modules, projects);
        } finally {
            try {
                stop(jdtls, server);
            } finally {
                delete(data);
            }
        }
    }

    /**
     * Opens the sources of the project, waits for the diagnostics jdtls publishes for each, then opens a file that
     * does not compile and requires jdtls to say so: a probe that sees nothing must not pass. The project must then
     * have no error.
     */
    private static void checkDiagnostics(
            ProbeWatch watch,
            LanguageServer server,
            Diagnostics diagnostics,
            Path projectRoot,
            List<Path> modules,
            List<String> projects)
            throws IOException, InterruptedException {
        List<Path> sources = sourcesOf(projectRoot, modules, projects);
        // Each wait is registered right before the didOpen of its file: a publication that arrived earlier (the build
        // of
        // the workspace publishes while the files are being opened) is not the answer for a file not yet opened.
        Map<Path, CompletableFuture<List<Diagnostic>>> publications = new LinkedHashMap<>();
        for (Path file : sources) {
            String text = Files.readString(file);
            publications.put(file, diagnostics.open(file, () -> open(server, file, text)));
        }
        for (var publication : publications.entrySet()) {
            watch.await(
                    "the diagnostics of " + projectRoot.relativize(publication.getKey()) + " (opened " + sources.size()
                            + " files)",
                    publication::getValue);
        }

        Path control = controlOf(sources);
        var controlErrors = diagnostics.expectError(control);
        open(server, control, controlSource(control));
        List<Diagnostic> found = watch.await(
                "the diagnostics of the control file " + projectRoot.relativize(control), () -> controlErrors);
        System.out.println("control error as jdtls reports it: "
                + found.stream()
                        .map(d -> projectRoot.relativize(control) + ":"
                                + (d.getRange().getStart().getLine() + 1) + ": " + Diagnostics.text(d))
                        .toList());
        assertThat(found)
                .as(
                        "the errors jdtls reports on the control file, whose line %d assigns a String to an int",
                        CONTROL_LINE)
                .anySatisfy(error -> {
                    assertThat(error.getRange().getStart().getLine()).isEqualTo(CONTROL_LINE - 1);
                    assertThat(Diagnostics.text(error)).contains("String").contains("int");
                });

        awaitSettled(watch, diagnostics, sources, control);

        for (Path file : sources) {
            System.out.println(projectRoot.relativize(file) + ": " + diagnostics.publications(file)
                    + " publications, latest " + diagnostics.count(DiagnosticSeverity.Error, List.of(file))
                    + " errors, " + diagnostics.count(DiagnosticSeverity.Warning, List.of(file)) + " warnings");
        }
        assertThat(diagnostics.errors(sources, projectRoot))
                .as("the errors jdtls reports in the %d files of the project it opened", sources.size())
                .isEmpty();
    }

    /**
     * jdtls publishes in stages (a quick reconcile, then after the build), so the first publication of a file is not
     * the last: the verdict waits until jdtls has published nothing for {@link #QUIET}, inside the shared deadline.
     */
    private static void awaitSettled(ProbeWatch watch, Diagnostics diagnostics, List<Path> sources, Path control)
            throws InterruptedException {
        List<Path> opened = Stream.concat(sources.stream(), Stream.of(control)).toList();
        int before = published(diagnostics, opened);
        long begin = System.nanoTime();
        watch.awaitQuiet("the diagnostics of the opened files", QUIET, diagnostics::quietFor);
        System.out.println("jdtls settled: quiet for " + QUIET.toSeconds() + " seconds, waited "
                + String.format(Locale.ROOT, "%.1f", (System.nanoTime() - begin) / 1e9) + " seconds for it, "
                + (published(diagnostics, opened) - before)
                + " more publications for the opened files came in that time");
    }

    private static int published(Diagnostics diagnostics, List<Path> files) {
        return files.stream().mapToInt(diagnostics::publications).sum();
    }

    /**
     * The {@code module-info.java} and the classes of each module, and the sources of the {@code architecture} project
     * when jdtls imported it.
     */
    private static List<Path> sourcesOf(Path projectRoot, List<Path> modules, List<String> projects)
            throws IOException {
        List<Path> roots = new ArrayList<>();
        modules.forEach(module -> roots.add(module.resolve("src/main/java")));
        Path architecture = projectRoot.resolve("architecture").toAbsolutePath().normalize();
        boolean imported = projects.stream().anyMatch(project -> isProjectOf(project, architecture));
        System.out.println("jdtls " + (imported ? "imported" : "did not import") + " the project " + architecture);
        if (imported) {
            roots.add(architecture.resolve("src"));
        }
        List<Path> sources = new ArrayList<>();
        for (Path root : roots) {
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(file -> file.getFileName().toString().endsWith(".java"))
                        .sorted()
                        .forEach(sources::add);
            }
        }
        return sources;
    }

    /** A file that does not exist, next to a class of the first module: jdtls compiles it with that module. */
    private static Path controlOf(List<Path> sources) {
        Path neighbour = sources.stream()
                .filter(file -> !file.getFileName().toString().equals("module-info.java"))
                .findFirst()
                .orElseThrow();
        return neighbour.resolveSibling("GabierProbeControl.java");
    }

    private static String controlSource(Path control) {
        Path folder = control.getParent();
        String sourceRoot = "src" + File.separator + "main" + File.separator + "java" + File.separator;
        String relative = folder.toString().substring(folder.toString().indexOf(sourceRoot) + sourceRoot.length());
        // The line CONTROL_LINE is the one that does not compile.
        return "package " + relative.replace(File.separatorChar, '.') + ";\n\n"
                + "final class GabierProbeControl {\n"
                + "    int control = \"not an int\";\n"
                + "}\n";
    }

    private static void open(LanguageServer server, Path file, String text) {
        var item = new TextDocumentItem(file.toUri().toString(), "java", 1, text);
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(item));
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
        // A dead jdtls never answers: asking it to shut down would only wait for the timeout.
        if (server != null && jdtls.isAlive()) {
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
