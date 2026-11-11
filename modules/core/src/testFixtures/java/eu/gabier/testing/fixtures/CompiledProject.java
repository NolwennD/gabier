package eu.gabier.testing.fixtures;

import eu.gabier.testing.domain.BuiltProject;
import eu.gabier.testing.domain.ClassFolder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A small project on disk, compiled by javac as a language server compiles it: {@code src/main/java} into {@code bin},
 * {@code src/test/java} into {@code test-bin}, and the test frameworks ({@link TestFrameworkStubs}) in a jar of the
 * class path, as a build tool gives them. It is what the build of a project hands the selection of tests, with no
 * language server.
 */
public final class CompiledProject {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Path TESTS = Path.of("src/test/java");

    private CompiledProject() {}

    /**
     * Writes the sources, compiles them and describes the build.
     *
     * @param root the folder of the project
     * @param main the text of each main source, by its path under {@code src/main/java}
     * @param tests the text of each test source, by its path under {@code src/test/java}
     * @param filesWithErrors the files, under the root, that the build reports an error in
     */
    public static BuiltProject of(
            Path root, Map<String, String> main, Map<String, String> tests, List<Path> filesWithErrors)
            throws IOException {
        Path jar = frameworks(root);
        Path mainClasses = root.resolve("bin");
        Path testClasses = root.resolve("test-bin");
        write(root.resolve(MAIN), main);
        write(root.resolve(TESTS), tests);
        CompiledSources.compile(main, mainClasses, List.of(jar));
        CompiledSources.compile(tests, testClasses, List.of(jar, mainClasses));

        return new BuiltProject(
                List.of(
                        new ClassFolder(root.resolve(MAIN), mainClasses),
                        new ClassFolder(root.resolve(TESTS), testClasses)),
                List.of(jar),
                filesWithErrors);
    }

    /** The same, for a build that found no error. */
    public static BuiltProject of(Path root, Map<String, String> main, Map<String, String> tests) throws IOException {
        return of(root, main, tests, List.of());
    }

    /**
     * Writes the jar of the test frameworks ({@link TestFrameworkStubs}) in the {@code lib} folder of the project, where
     * a build tool would put what the tests are compiled against.
     *
     * @return the jar
     */
    public static Path frameworks(Path root) throws IOException {
        Map<String, String> sources = new HashMap<>();
        sources.putAll(TestFrameworkStubs.JUNIT_5);
        sources.putAll(TestFrameworkStubs.JUNIT_4_AND_3);
        sources.putAll(TestFrameworkStubs.TESTNG);
        sources.putAll(TestFrameworkStubs.ARCHUNIT);
        Files.createDirectories(root.resolve("lib"));

        return CompiledSources.jar(root.resolve("lib/test-frameworks.jar"), sources);
    }

    private static void write(Path folder, Map<String, String> sources) throws IOException {
        for (var source : sources.entrySet()) {
            Path file = folder.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
        }
    }
}
