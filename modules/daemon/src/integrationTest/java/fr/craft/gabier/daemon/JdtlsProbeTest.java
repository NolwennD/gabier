package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.gabier.editing.domain.Diagnostic;
import fr.craft.gabier.editing.domain.Diagnostic.Severity;
import fr.craft.gabier.editing.domain.LanguageServerErrorKey;
import fr.craft.gabier.editing.domain.LanguageServerException;
import java.io.IOException;
import java.io.UncheckedIOException;
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
            checkImportsOfTheProjectAreInOrder(server, projectRoot, modules);
            checkImportsJdtlsCannotResolveAreRefused(server, projectRoot);
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

    /**
     * Organizing the imports of every source of the project (main, test, fixtures, integration tests, the architecture
     * tests) changes nothing: the imports are as the formatter keeps them (static first, one block in ASCII order, no
     * star), which jdtls must agree with, or an agent that organizes the imports of a file would reorder what the
     * project orders already. A probe that sees nothing must not pass: a copy of a main source and of a test source
     * that uses static imports, each with an unused import added, must get an edit that removes it.
     */
    private static void checkImportsOfTheProjectAreInOrder(
            JdtlsLanguageServer server, Path projectRoot, List<Path> modules) throws IOException {
        List<Path> sources = new ArrayList<>();
        List<Path> roots = new ArrayList<>(
                modules.stream().map(module -> module.resolve("src")).toList());
        roots.add(projectRoot.resolve("architecture/src"));
        for (Path root : roots) {
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(file -> file.getFileName().toString().endsWith(".java"))
                        .filter(JdtlsProbeTest::isClassSource)
                        // Apart: jdtls does not see the packages of the test fixtures from this source set ("not
                        // accessible"), so it drops its imports: see checkImportsJdtlsCannotResolveAreRefused.
                        .filter(file -> !file.toString().contains("/src/fakeServed/"))
                        .sorted()
                        .forEach(sources::add);
            }
        }
        List<String> changed = new ArrayList<>();
        for (Path source : sources) {
            server.organizeImports(source)
                    .ifPresent(change -> changed.add(projectRoot.relativize(source) + ": " + change));
        }
        System.out.println("organized the imports of " + sources.size() + " sources of the project, " + changed.size()
                + " would change");
        assertThat(changed)
                .as("the sources whose imports jdtls would change: the project orders them differently")
                .isEmpty();

        Path main = sources.stream()
                .filter(source -> source.toString().contains("/src/main/"))
                .filter(JdtlsProbeTest::hasImports)
                .findFirst()
                .orElseThrow();
        Path test = sources.stream()
                .filter(source -> source.toString().contains("/src/test/"))
                .filter(JdtlsProbeTest::hasStaticImports)
                .findFirst()
                .orElseThrow();
        checkUntidyCopy(server, main);
        checkUntidyCopy(server, test);
    }

    /**
     * The source set {@code fakeServed} has no classpath for the test fixtures in jdtls ("The package
     * fr.craft.gabier.editing is not accessible" on its imports), and jdtls answers an edit that deletes those imports.
     * Organizing them is refused, names the imports, and the file is not touched.
     */
    private static void checkImportsJdtlsCannotResolveAreRefused(JdtlsLanguageServer server, Path projectRoot)
            throws IOException {
        Path fakeServed =
                projectRoot.resolve("modules/daemon/src/fakeServed/java/fr/craft/gabier/daemon/FakeServedMain.java");
        String before = Files.readString(fakeServed);

        assertThatThrownBy(() -> server.organizeImports(fakeServed))
                .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                    System.out.println("organizing the imports of " + projectRoot.relativize(fakeServed) + ": "
                            + refusal.getMessage());
                    assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.UNRESOLVED_IMPORTS);
                    assertThat(refusal.parameters().get("imports"))
                            .contains("fr.craft.gabier.editing.fixtures.FakeJavaLanguageServer")
                            .contains("fr.craft.gabier.editing.fixtures.Scenario")
                            .contains("fr.craft.gabier.editing.fixtures.Scenarios");
                });
        assertThat(Files.readString(fakeServed)).as("the source is not touched").isEqualTo(before);
    }

    private static boolean hasImports(Path source) {
        return lines(source).anyMatch(line -> line.startsWith("import "));
    }

    private static boolean hasStaticImports(Path source) {
        return lines(source).anyMatch(line -> line.startsWith("import static "));
    }

    private static Stream<String> lines(Path source) {
        try {
            return Files.readAllLines(source).stream();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** A temporary copy of a source with an unused import before its first import: jdtls must offer to remove it. */
    private static void checkUntidyCopy(JdtlsLanguageServer server, Path source) throws IOException {
        String unused = "import java.util.concurrent.locks.StampedLock;\n";
        String original = Files.readString(source);
        String type = source.getFileName().toString().replace(".java", "");
        String renamed = original.replaceAll("\\b" + Pattern.quote(type) + "\\b", BROKEN_TYPE);
        int first = renamed.indexOf("import ");
        Path copy = source.resolveSibling(BROKEN_TYPE + ".java");
        Files.writeString(copy, renamed.substring(0, first) + unused + renamed.substring(first));
        try {
            var change = server.organizeImports(copy);
            System.out.println(
                    "copy of " + source.getFileName() + " with an unused import, as jdtls organizes it: " + change);
            assertThat(change)
                    .as("the organization of a copy of %s with an unused import", source.getFileName())
                    .isPresent();
            assertThat(change.orElseThrow().toString())
                    .as("the edit that organizes a copy of %s", source.getFileName())
                    .doesNotContain("StampedLock");
        } finally {
            Files.delete(copy);
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
