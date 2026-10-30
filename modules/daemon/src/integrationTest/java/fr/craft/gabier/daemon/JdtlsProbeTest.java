package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.gabier.editing.domain.Diagnostic;
import fr.craft.gabier.editing.domain.Diagnostic.Severity;
import fr.craft.gabier.editing.domain.LanguageServerErrorKey;
import fr.craft.gabier.editing.domain.LanguageServerException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Starts a real jdtls on this project through the adapter ({@link JdtlsLanguageServer}, which waits until jdtls has
 * really imported every module) and asks it for diagnostics, through the port, the way an agent does. It checks that
 * jdtls is refused as not ready while it imports, that every source of the project is free of errors, that a clean
 * file gets an empty list, that a temporary broken copy of a source gets its error, and that a file outside the project is
 * refused. Not run by {@code check} (which only compiles it): run it with {@code ./gradlew jdtlsProbe}, with {@code
 * GABIER_JDTLS} set to the jdtls launcher (mise exports it). It fails at once when jdtls exits or sends a status {@code
 * Error}; its waits share one deadline of ten minutes.
 */
class JdtlsProbeTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    private static final String BROKEN_TYPE = "GabierProbeBroken";

    /** Appended to the copy, just before the closing brace of its type: a String where an {@code int} is expected. */
    private static final String BROKEN_FIELD = "    int gabierProbeControl = \"not an int\";\n";

    @Test
    void answersTheDiagnosticsOfTheProject() throws Exception {
        Path projectRoot = Path.of(System.getProperty("gabier.projectRoot"))
                .toAbsolutePath()
                .normalize();
        List<Path> modules = modulesOf(projectRoot);
        assertThat(modules)
                .as("modules under %s: the probe would wait for nothing", projectRoot.resolve("modules"))
                .isNotEmpty();

        var settings = JdtlsSettings.of(projectRoot, modules, TIMEOUT);
        try (var server = JdtlsLanguageServer.start(
                RealJdtls.launcher(), settings, JdtlsLanguageServer.QUIET, System.out::println)) {
            // Importing every module takes about a minute: jdtls is still at it when it is asked.
            Path anySource = modules.getFirst().resolve("src/main/java/module-info.java");
            assertThatThrownBy(() -> server.diagnostics(anySource))
                    .as("a jdtls that is still importing")
                    .isInstanceOfSatisfying(
                            LanguageServerException.class,
                            refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
            System.out.println("refused as not ready while jdtls imports: ok");

            List<Path> projects = server.awaitStarted();
            System.out.println("jdtls knows the projects " + projects);
            List<Path> sources = sourcesOf(projectRoot, modules, projects);

            Path clean = checkEverySource(server, sources, projectRoot);
            checkBrokenCopy(server, clean);
            checkFileOutsideTheProject(server);
        }
    }

    /**
     * Asks for all the sources at once and requires that none has an error. Returns a source whose diagnostics are
     * none, asked for again alone: the adapter answers an empty list for a clean file.
     */
    private static Path checkEverySource(JdtlsLanguageServer server, List<Path> sources, Path projectRoot) {
        long begin = System.nanoTime();
        Map<Path, List<Diagnostic>> answers = server.diagnostics(sources);
        System.out.println("diagnostics of " + sources.size() + " files in "
                + String.format(Locale.ROOT, "%.1f", (System.nanoTime() - begin) / 1e9) + " seconds");

        List<String> errors = new ArrayList<>();
        answers.forEach((file, diagnostics) -> {
            long warnings = diagnostics.stream()
                    .filter(diagnostic -> diagnostic.severity() == Severity.WARNING)
                    .count();
            System.out.println(projectRoot.relativize(file) + ": " + diagnostics.size() + " diagnostics, " + warnings
                    + " warnings");
            diagnostics.stream()
                    .filter(diagnostic -> diagnostic.severity() == Severity.ERROR)
                    .forEach(diagnostic -> errors.add(projectRoot.relativize(file) + ":"
                            + (diagnostic.position().line() + 1) + ": " + diagnostic.message()));
        });
        assertThat(errors)
                .as("the errors jdtls reports in the %d files of the project it opened", sources.size())
                .isEmpty();

        Path clean = answers.entrySet().stream()
                .filter(answer -> answer.getValue().isEmpty())
                .filter(answer -> isClassSource(answer.getKey()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No class of the project is without any diagnostic"));
        assertThat(server.diagnostics(clean))
                .as("the diagnostics of %s, asked for alone", projectRoot.relativize(clean))
                .isEmpty();
        System.out.println("clean file " + projectRoot.relativize(clean) + ": no diagnostic");
        return clean;
    }

    /**
     * A temporary copy of a clean source, its type renamed and a String put where an {@code int} is expected: a probe
     * that sees nothing must not pass. The copy is deleted whatever happens.
     */
    private static void checkBrokenCopy(JdtlsLanguageServer server, Path clean) throws IOException {
        String original = Files.readString(clean);
        String type = clean.getFileName().toString().replace(".java", "");
        String copy = original.replaceAll("\\b" + Pattern.quote(type) + "\\b", BROKEN_TYPE);
        int end = copy.lastIndexOf('}');
        int brokenLine =
                (int) copy.substring(0, end).chars().filter(c -> c == '\n').count();
        Path broken = clean.resolveSibling(BROKEN_TYPE + ".java");
        Files.writeString(broken, copy.substring(0, end) + BROKEN_FIELD + copy.substring(end));
        try {
            List<Diagnostic> found = server.diagnostics(broken);
            System.out.println("copy of " + clean.getFileName() + " with an error on line " + (brokenLine + 1)
                    + ", as jdtls reports it: " + found);
            assertThat(found)
                    .as("the diagnostics of a copy whose line %d assigns a String to an int", brokenLine + 1)
                    .anySatisfy(error -> {
                        assertThat(error.severity()).isEqualTo(Severity.ERROR);
                        assertThat(error.position().line()).isEqualTo(brokenLine);
                        assertThat(error.message()).contains("String").contains("int");
                    });
        } finally {
            Files.delete(broken);
        }
    }

    /** A type of a package, which a copy can be made of: not a {@code module-info.java} or a {@code package-info.java}. */
    private static boolean isClassSource(Path file) {
        return !file.getFileName().toString().endsWith("-info.java");
    }

    private static void checkFileOutsideTheProject(JdtlsLanguageServer server) throws IOException {
        Path folder = Files.createTempDirectory("gabier-outside-");
        Path outside = folder.resolve("Elsewhere.java");
        Files.writeString(outside, "class Elsewhere {}\n");
        try {
            assertThatThrownBy(() -> server.diagnostics(outside))
                    .isInstanceOfSatisfying(
                            LanguageServerException.class,
                            refusal ->
                                    assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));
            System.out.println("refused a file outside the project: ok");
        } finally {
            Files.delete(outside);
            Files.delete(folder);
        }
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
