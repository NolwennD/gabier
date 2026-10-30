package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.Test;

/**
 * Starts a real jdtls on this project through the adapter ({@link JdtlsSession}, which waits until jdtls has really
 * imported every module), then opens the sources and requires jdtls to publish diagnostics for each of them (after its
 * own {@code didOpen}), and an error for a deliberately broken file. It judges the diagnostics only once jdtls has
 * published nothing for {@link #QUIET}, none of them an error. Not run by {@code check} (which only compiles it): run
 * it with {@code ./gradlew jdtlsProbe}, with {@code GABIER_JDTLS} set to the jdtls launcher (mise exports it). It fails
 * at once when jdtls exits or sends a status {@code Error}; its waits share one deadline of ten minutes.
 */
class JdtlsProbeTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(10);
    /** How long jdtls must have published nothing before the diagnostics are judged. */
    private static final Duration QUIET = Duration.ofSeconds(5);

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

        var diagnostics = new Diagnostics();
        // The launcher runs the java of JAVA_HOME: the one that runs this test, not whatever the shell has.
        var jdtls = new JdtlsLauncher(launcher, Path.of(System.getProperty("java.home")));
        var settings = JdtlsSettings.of(projectRoot, modules, TIMEOUT);
        try (JdtlsSession session = JdtlsSession.start(jdtls, settings, System.out::println, publication -> {
            diagnostics.publish(publication);
            log(diagnostics, publication);
        })) {
            System.out.println("jdtls knows the projects " + session.projects());
            checkDiagnostics(session, diagnostics, projectRoot, modules);
        }
    }

    /** The evidence of the probe: every publication, with what it holds. */
    private static void log(Diagnostics diagnostics, PublishDiagnosticsParams publication) {
        System.out.println(
                String.format(Locale.ROOT, "[%.1fs] ", diagnostics.sinceStart().toMillis() / 1000.0)
                        + "jdtls published " + publication.getDiagnostics().size() + " diagnostics for "
                        + publication.getUri() + ": "
                        + publication.getDiagnostics().stream()
                                .map(d -> d.getSeverity() + " line "
                                        + (d.getRange().getStart().getLine() + 1) + " " + Diagnostics.text(d))
                                .toList());
    }

    /**
     * Opens the sources of the project, waits for the diagnostics jdtls publishes for each, then opens a file that
     * does not compile and requires jdtls to say so: a probe that sees nothing must not pass. The project must then
     * have no error.
     */
    private static void checkDiagnostics(
            JdtlsSession session, Diagnostics diagnostics, Path projectRoot, List<Path> modules)
            throws IOException, InterruptedException {
        JdtlsWatch watch = session.watch();
        LanguageServer server = session.server();
        List<Path> sources = sourcesOf(projectRoot, modules, session.projects());
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
    private static void awaitSettled(JdtlsWatch watch, Diagnostics diagnostics, List<Path> sources, Path control)
            throws InterruptedException {
        List<Path> opened = Stream.concat(sources.stream(), Stream.of(control)).toList();
        int before = published(diagnostics, opened);
        long begin = System.nanoTime();
        QuietWait.await(watch, "the diagnostics of the opened files", QUIET, diagnostics::quietFor);
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
    private static List<Path> sourcesOf(Path projectRoot, List<Path> modules, List<Path> projects) throws IOException {
        List<Path> roots = new ArrayList<>();
        modules.forEach(module -> roots.add(module.resolve("src/main/java")));
        Path architecture = projectRoot.resolve("architecture").toAbsolutePath().normalize();
        boolean imported = projects.contains(architecture);
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
}
