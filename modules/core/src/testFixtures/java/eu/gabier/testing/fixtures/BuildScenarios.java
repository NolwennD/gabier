package eu.gabier.testing.fixtures;

import eu.gabier.testing.domain.BuiltProject;
import eu.gabier.testing.domain.ClassFolder;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The contract scenarios of the build of a project, replayed against the real language server by the integration tests
 * of its adapter (see {@link BuildScenario}). The projects are hand-written Eclipse projects, which the server imports
 * with no network: the main sources are in {@code src/main/java} and are compiled into {@code bin}, the test sources in
 * {@code src/test/java}, marked as tests, and compiled into {@code test-bin}. What a project built with Maven ({@code
 * target/classes}, {@code target/test-classes}) or Gradle ({@code bin/main}, {@code bin/test}) gives is the business of
 * the adapter's tests, which keep the answers the server gave.
 */
public final class BuildScenarios {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Path TESTS = Path.of("src/test/java");

    private static final List<ClassFolder> FOLDERS =
            List.of(new ClassFolder(MAIN, Path.of("bin")), new ClassFolder(TESTS, Path.of("test-bin")));

    /** A project that compiles: the classes of its sources are in the class folders, and no file has an error. */
    public static final BuildScenario BUILD_OF_A_PROJECT_THAT_COMPILES = new BuildScenario(
            "build-of-a-project-that-compiles",
            Map.of(
                    MAIN.resolve("app/Greeter.java"), """
                    package app;

                    public class Greeter {
                        public String greet() {
                            return "hello";
                        }
                    }
                    """,
                    TESTS.resolve("app/GreeterTest.java"), """
                    package app;

                    public class GreeterTest {
                        public void greets() {
                            new Greeter().greet();
                        }
                    }
                    """),
            new BuiltProject(FOLDERS, List.of(), List.of()));

    /**
     * A project with an error in a main source and another in a test source: the build names both files, in the order of
     * the paths, and the class folders are the same.
     */
    public static final BuildScenario BUILD_OF_A_PROJECT_WITH_ERRORS = new BuildScenario(
            "build-of-a-project-with-errors",
            Map.of(
                    MAIN.resolve("app/Broken.java"), """
                    package app;

                    public class Broken {
                        public int answer() {
                            return "forty-two";
                        }
                    }
                    """,
                    MAIN.resolve("app/Clean.java"), """
                    package app;

                    public class Clean {}
                    """,
                    TESTS.resolve("app/BrokenTest.java"), """
                    package app;

                    public class BrokenTest {
                        String text() {
                            return 42;
                        }
                    }
                    """),
            new BuiltProject(
                    FOLDERS,
                    List.of(),
                    List.of(MAIN.resolve("app/Broken.java"), TESTS.resolve("app/BrokenTest.java"))));

    private BuildScenarios() {}

    /** Every scenario of the build of a project. */
    public static List<BuildScenario> all() {
        return List.of(BUILD_OF_A_PROJECT_THAT_COMPILES, BUILD_OF_A_PROJECT_WITH_ERRORS);
    }
}
