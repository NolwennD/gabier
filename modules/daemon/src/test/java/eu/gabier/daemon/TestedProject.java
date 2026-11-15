package eu.gabier.daemon;

import eu.gabier.testing.fixtures.CompiledSources;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A small project of JUnit Jupiter tests compiled with javac, and the jars of the JUnit this test runs with: what the
 * process of the tests is started on.
 */
final class TestedProject {

    private static final String HELPER = """
        package app;

        public class Helper {
            public static void explode() {
                throw new IllegalStateException("exploded\\tin the helper");
            }
        }
        """;

    private static final String MIXED = """
        package app;

        import org.junit.jupiter.api.Assumptions;
        import org.junit.jupiter.api.Disabled;
        import org.junit.jupiter.api.Test;

        class MixedTest {

            @Test
            void passes() {}

            @Test
            void alsoPasses() {}

            @Test
            void fails() {
                Helper.explode();
            }

            @Disabled
            @Test
            void isDisabled() {}

            @Test
            void doesNotApply() {
                Assumptions.assumeTrue(false);
            }
        }
        """;

    private static final String BROKEN_SETUP = """
        package app;

        import org.junit.jupiter.api.BeforeAll;
        import org.junit.jupiter.api.Test;

        class BrokenSetupTest {

            @BeforeAll
            static void setUp() {
                throw new IllegalStateException();
            }

            @Test
            void neverRuns() {}
        }
        """;

    private static final String ODD_STACKS = """
        package app;

        import org.junit.jupiter.api.Test;

        class OddStacksTest {

            @Test
            void stackOfTheJdkOnly() {
                var thrown = new IllegalStateException("from the JDK");
                thrown.setStackTrace(new StackTraceElement[] {new StackTraceElement("java.lang.Thread", "run", null, -1)});
                throw thrown;
            }

            @Test
            void noStack() {
                var thrown = new IllegalStateException("no stack");
                thrown.setStackTrace(new StackTraceElement[0]);
                throw thrown;
            }
        }
        """;

    private static final String DYNAMIC = """
        package app;

        import java.util.List;
        import org.junit.jupiter.api.DynamicTest;
        import org.junit.jupiter.api.TestFactory;

        class DynamicFailureTest {

            @TestFactory
            List<DynamicTest> tests() {
                return List.of(DynamicTest.dynamicTest("named", () -> {
                    throw new IllegalStateException("dynamic");
                }));
            }
        }
        """;

    private static final String SLEEP = """
        package app;

        import org.junit.jupiter.api.Test;

        class SleepTest {

            @Test
            void sleeps() throws InterruptedException {
                Thread.sleep(600_000);
            }
        }
        """;

    private static final String EXIT = """
        package app;

        import org.junit.jupiter.api.Test;

        class ExitTest {

            @Test
            void stopsTheJvm() {
                System.out.println("goodbye from the test");
                System.exit(3);
            }
        }
        """;

    private static final String DYNAMIC_WITH_A_URI = """
        package app;

        import java.net.URI;
        import java.util.List;
        import org.junit.jupiter.api.DynamicTest;
        import org.junit.jupiter.api.TestFactory;

        class DynamicUriTest {

            @TestFactory
            List<DynamicTest> tests() {
                return List.of(DynamicTest.dynamicTest("named", URI.create("file:///nowhere"), () -> {
                    throw new IllegalStateException("dynamic with a uri");
                }));
            }
        }
        """;

    private static final String DISABLED_CLASS = """
        package app;

        import org.junit.jupiter.api.Disabled;
        import org.junit.jupiter.api.Test;

        @Disabled
        class DisabledClassTest {

            @Test
            void one() {}

            @Test
            void two() {}
        }
        """;

    private static final String NOT_A_TEST = """
        package app;

        public class NotATest {}
        """;

    private TestedProject() {}

    /** Compiles the project into a folder: the classes are what the tests of the daemon run. */
    static Path compiledIn(Path folder) throws IOException {
        Path classes = folder.resolve("classes");
        CompiledSources.compile(
                Map.of(
                        "app/Helper.java", HELPER,
                        "app/MixedTest.java", MIXED,
                        "app/BrokenSetupTest.java", BROKEN_SETUP,
                        "app/OddStacksTest.java", ODD_STACKS,
                        "app/DynamicFailureTest.java", DYNAMIC,
                        "app/DynamicUriTest.java", DYNAMIC_WITH_A_URI,
                        "app/DisabledClassTest.java", DISABLED_CLASS,
                        "app/SleepTest.java", SLEEP,
                        "app/ExitTest.java", EXIT,
                        "app/NotATest.java", NOT_A_TEST),
                classes,
                junit());

        return classes;
    }

    /** The jars that hold the JUnit Platform and the Jupiter engine this test runs with. */
    static List<Path> junit() {
        return Stream.of(
                        "org.junit.platform.launcher.core.LauncherFactory",
                        "org.junit.jupiter.engine.JupiterTestEngine",
                        "org.junit.jupiter.api.Test",
                        "org.junit.platform.engine.TestEngine",
                        "org.junit.platform.commons.JUnitException",
                        "org.opentest4j.AssertionFailedError")
                .map(TestedProject::jarOf)
                .toList();
    }

    private static Path jarOf(String className) {
        try {
            return Path.of(URI.create(Class.forName(className)
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toString()));
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException(missing);
        }
    }
}
