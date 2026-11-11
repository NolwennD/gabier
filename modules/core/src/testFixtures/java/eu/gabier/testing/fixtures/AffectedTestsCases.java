package eu.gabier.testing.fixtures;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The contract scenarios of the selection of tests: a small shop with one test of each framework, a test that is
 * filtered out, a test that loads classes by itself (ArchUnit), an abstract test and a class no test uses, and the
 * questions asked of it ({@link AffectedTestsCase}). The sources are compiled by javac in {@code check} ({@link
 * CompiledProject}) or by the real language server in the integration tests, with the file of filters written at the
 * root of the project.
 */
public final class AffectedTestsCases {

    /** The files of the shop, and where its sources are. */
    private static final class Shop {

        private static final Path MAIN = Path.of("src/main/java/shop");
        private static final Path TESTS = Path.of("src/test/java/shop");

        private static final Map<String, String> MAIN_SOURCES = Map.of(
                "shop/Repository.java", """
                package shop;

                public class Repository {
                    public String load() {
                        return "item";
                    }
                }
                """,
                "shop/Service.java", """
                package shop;

                public class Service {
                    public String run() {
                        return new Repository().load();
                    }
                }
                """,
                "shop/Greeter.java", """
                package shop;

                public class Greeter {
                    public String greet() {
                        return "hello";
                    }
                }
                """,
                "shop/Unused.java", """
                package shop;

                public class Unused {}
                """);

        private static final Map<String, String> TEST_SOURCES = Map.of(
                "shop/ServiceTest.java", """
                package shop;

                class ServiceTest {
                    @org.junit.jupiter.api.Test
                    void runs() {
                        new Service().run();
                    }
                }
                """,
                "shop/GreeterTest.java", """
                package shop;

                public class GreeterTest {
                    @org.junit.Test
                    public void greets() {
                        new Greeter().greet();
                    }
                }
                """,
                "shop/GreeterIT.java", """
                package shop;

                public class GreeterIT {
                    @org.junit.Test
                    public void greetsEverywhere() {
                        new Greeter().greet();
                    }
                }
                """,
                "shop/ArchitectureTest.java", """
                package shop;

                @com.tngtech.archunit.junit.AnalyzeClasses
                class ArchitectureTest {
                    @com.tngtech.archunit.junit.ArchTest
                    static final Object rule = new Object();
                }
                """,
                "shop/LegacyTest.java", """
                package shop;

                public class LegacyTest extends junit.framework.TestCase {
                    public void testLoads() {
                        new Repository().load();
                    }
                }
                """,
                "shop/NgTest.java", """
                package shop;

                public class NgTest {
                    @org.testng.annotations.Test
                    public void runs() {
                        new Service().run();
                    }
                }
                """,
                "shop/AbstractGreeterTest.java", """
                package shop;

                public abstract class AbstractGreeterTest {
                    @org.junit.Test
                    public void greets() {
                        greeter().greet();
                    }

                    protected abstract Greeter greeter();
                }
                """,
                "shop/FrenchGreeterTest.java", """
                package shop;

                public class FrenchGreeterTest extends AbstractGreeterTest {
                    @Override
                    protected Greeter greeter() {
                        return new Greeter();
                    }
                }
                """);

        /**
         * The files that are neither main nor test sources: the file of filters, at the root of the project, which leaves the
         * integration tests out of the selection, and a readme.
         */
        private static final Map<Path, String> OTHER_FILES =
                Map.of(Path.of("infinitest.filters"), "exclude .*IT\n", Path.of("README.md"), "# Shop\n");

        private Shop() {}
    }

    /** A change to a class that two tests use directly and two others through a service. */
    public static final AffectedTestsCase A_CLASS_USED_BY_TESTS = new AffectedTestsCase(
            "a-class-used-by-tests",
            Map.of(),
            List.of(Shop.MAIN.resolve("Repository.java")),
            List.of("shop.ArchitectureTest", "shop.LegacyTest", "shop.NgTest", "shop.ServiceTest"));

    /** A change to a class used by a JUnit 4 test, by an integration test that the filters leave out, and by an abstract test's heir. */
    public static final AffectedTestsCase A_CLASS_USED_BY_A_FILTERED_TEST = new AffectedTestsCase(
            "a-class-used-by-a-filtered-test",
            Map.of(),
            List.of(Shop.MAIN.resolve("Greeter.java")),
            List.of("shop.ArchitectureTest", "shop.FrenchGreeterTest", "shop.GreeterTest"));

    /** A change to a test: the test itself, and nothing that uses it. */
    public static final AffectedTestsCase A_TEST = new AffectedTestsCase(
            "a-test",
            Map.of(),
            List.of(Shop.TESTS.resolve("ServiceTest.java")),
            List.of("shop.ArchitectureTest", "shop.ServiceTest"));

    /** A change to a class that no test uses: only the tests that load classes by themselves. */
    public static final AffectedTestsCase A_CLASS_NO_TEST_USES = new AffectedTestsCase(
            "a-class-no-test-uses",
            Map.of(),
            List.of(Shop.MAIN.resolve("Unused.java")),
            List.of("shop.ArchitectureTest"));

    /** A change to an abstract test: the tests that extend it. */
    public static final AffectedTestsCase AN_ABSTRACT_TEST = new AffectedTestsCase(
            "an-abstract-test",
            Map.of(),
            List.of(Shop.TESTS.resolve("AbstractGreeterTest.java")),
            List.of("shop.ArchitectureTest", "shop.FrenchGreeterTest"));

    /** A change to a test that the filters leave out. */
    public static final AffectedTestsCase A_FILTERED_TEST = new AffectedTestsCase(
            "a-filtered-test",
            Map.of(),
            List.of(Shop.TESTS.resolve("GreeterIT.java")),
            List.of("shop.ArchitectureTest"));

    /** A change to a file that is not Java: nothing to run. */
    public static final AffectedTestsCase A_FILE_THAT_IS_NOT_JAVA =
            new AffectedTestsCase("a-file-that-is-not-java", Map.of(), List.of(Path.of("README.md")), List.of());

    /**
     * A test edited on disk to use a class it did not use when the project was built, and that class changes: the
     * selection follows the edit, so it reads the classes the build wrote after it.
     */
    public static final AffectedTestsCase A_TEST_EDITED_TO_USE_ANOTHER_CLASS = new AffectedTestsCase(
            "a-test-edited-to-use-another-class",
            Map.of(Shop.TESTS.resolve("GreeterTest.java"), """
                package shop;

                public class GreeterTest {
                    @org.junit.Test
                    public void greets() {
                        new Greeter().greet();
                        new Unused();
                    }
                }
                """),
            List.of(Shop.MAIN.resolve("Unused.java")),
            List.of("shop.ArchitectureTest", "shop.GreeterTest"));

    private AffectedTestsCases() {}

    /** Every case, in the order they are played. */
    public static List<AffectedTestsCase> all() {
        return List.of(
                A_CLASS_USED_BY_TESTS,
                A_CLASS_USED_BY_A_FILTERED_TEST,
                A_TEST,
                A_CLASS_NO_TEST_USES,
                AN_ABSTRACT_TEST,
                A_FILTERED_TEST,
                A_FILE_THAT_IS_NOT_JAVA,
                A_TEST_EDITED_TO_USE_ANOTHER_CLASS);
    }

    /** The sources of the main code, by their path under {@code src/main/java}. */
    public static Map<String, String> mainSources() {
        return Shop.MAIN_SOURCES;
    }

    /** The sources of the tests, by their path under {@code src/test/java}. */
    public static Map<String, String> testSources() {
        return Shop.TEST_SOURCES;
    }

    /** The files of the project that are neither main nor test sources, by their path under the root. */
    public static Map<Path, String> otherFiles() {
        return Shop.OTHER_FILES;
    }
}
