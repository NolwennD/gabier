package eu.gabier.testing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.testing.fixtures.CompiledProject;
import eu.gabier.testing.infrastructure.secondary.NioProjectFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestSelectionTest {

    private static final Map<String, String> SHOP = Map.of(
            "app/Repository.java", """
            package app;

            public class Repository {
                public String find() {
                    return "item";
                }
            }
            """,
            "app/Service.java", """
            package app;

            public class Service {
                private final Repository repository = new Repository();

                public String get() {
                    return repository.find();
                }
            }
            """,
            "app/Greeter.java", """
            package app;

            public class Greeter {
                public String greet() {
                    return "hello";
                }
            }
            """);

    private static final Map<String, String> SHOP_TESTS = Map.of(
            "app/ServiceTest.java", """
            package app;

            public class ServiceTest {
                @org.junit.Test
                public void gets() {
                    new Service().get();
                }
            }
            """,
            "app/GreeterTest.java", """
            package app;

            public class GreeterTest {
                @org.junit.Test
                public void greets() {
                    new Greeter().greet();
                }
            }
            """);

    @TempDir
    Path root;

    private ClassIndex index;

    private void project(Map<String, String> main, Map<String, String> tests) throws IOException {
        index = new ClassIndex(new NioProjectFiles(), CompiledProject.of(root, main, tests));
    }

    private List<String> selected(TestFilters filters, String... changed) {
        return TestSelection.of(index, Set.of(changed), filters);
    }

    private List<String> selected(String... changed) {
        return selected(TestFilters.none(), changed);
    }

    @Test
    void selectsAChangedTestClassItself() throws IOException {
        project(SHOP, SHOP_TESTS);

        assertThat(selected("app.GreeterTest")).containsExactly("app.GreeterTest");
    }

    @Test
    void selectsTheTestsThatUseAChangedApplicationClassAndNoOther() throws IOException {
        project(SHOP, SHOP_TESTS);

        assertThat(selected("app.Greeter")).containsExactly("app.GreeterTest");
    }

    @Test
    void selectsTheTestsThatDependOnAChangedClassThroughOtherClasses() throws IOException {
        project(SHOP, SHOP_TESTS);

        assertThat(selected("app.Repository")).containsExactly("app.ServiceTest");
    }

    @Test
    void selectsNothingWhenNoTestDependsOnTheChangedClass() throws IOException {
        project(
                Map.of(
                        "app/Unused.java",
                        "package app; public class Unused {}",
                        "app/Greeter.java",
                        SHOP.get("app/Greeter.java")),
                Map.of("app/GreeterTest.java", SHOP_TESTS.get("app/GreeterTest.java")));

        assertThat(selected("app.Unused")).isEmpty();
    }

    @Test
    void doesNotFollowAnInterfaceToTheClassThatImplementsIt() throws IOException {
        project(Map.of("app/Shape.java", """
            package app;

            public interface Shape {
                int sides();
            }

            class Square implements Shape {
                public int sides() {
                    return 4;
                }
            }
            """, "app/Factory.java", """
            package app;

            public class Factory {
                public Shape square() {
                    return new Square();
                }
            }
            """), Map.of("app/ShapeTest.java", """
                package app;

                public class ShapeTest {
                    @org.junit.Test
                    public void hasSides() {
                        Shape shape = null;
                        shape.sides();
                    }
                }
                """));

        assertThat(selected("app.Square")).isEmpty();
        assertThat(selected("app.Shape")).containsExactly("app.ShapeTest");
    }

    @Test
    void selectsTheTestsThatReadAnInlinedConstantByTheNameOfItsClass() throws IOException {
        project(Map.of("app/Limits.java", """
            package app;

            public class Limits {
                public static final int MAX = 3;
            }
            """), Map.of("app/LimitsTest.java", """
                package app;

                public class LimitsTest {
                    @org.junit.Test
                    public void reads() {
                        System.out.println(Limits.MAX);
                    }
                }
                """));

        assertThat(selected("app.Limits")).containsExactly("app.LimitsTest");
    }

    @Test
    void selectsNothingWhenNothingChanged() throws IOException {
        project(SHOP, SHOP_TESTS);

        assertThat(selected()).isEmpty();
    }

    @Test
    void countsAClassAsUsedWhenOnlyAFieldOrAnAnnotationNamesIt() throws IOException {
        project(
                Map.of(
                        "app/Config.java", "package app; public class Config {}",
                        "app/Marker.java", "package app; public @interface Marker {}"),
                Map.of("app/FieldTest.java", """
                    package app;

                    public class FieldTest {
                        Config config;

                        @org.junit.Test
                        @Marker
                        public void checks() {}
                    }
                    """));

        assertThat(selected("app.Config")).containsExactly("app.FieldTest");
        assertThat(selected("app.Marker")).containsExactly("app.FieldTest");
    }

    @Test
    void selectsTheTestsOfTheSubclassesOfAChangedBaseTest() throws IOException {
        project(
                Map.of(),
                Map.of(
                        "app/BaseTest.java", """
                        package app;

                        public abstract class BaseTest {
                            @org.junit.Test
                            public void shared() {}
                        }
                        """,
                        "app/FirstTest.java", "package app; public class FirstTest extends BaseTest {}",
                        "app/SecondTest.java", "package app; public class SecondTest extends BaseTest {}",
                        "app/OtherTest.java", """
                        package app;

                        public class OtherTest {
                            @org.junit.Test
                            public void other() {}
                        }
                        """));

        assertThat(selected("app.BaseTest")).containsExactly("app.FirstTest", "app.SecondTest");
    }

    @Test
    void goesThroughAClassCycleOnce() throws IOException {
        project(
                Map.of(
                        "app/Ping.java", "package app; public class Ping { Pong pong; }",
                        "app/Pong.java", "package app; public class Pong { Ping ping; }"),
                Map.of("app/PingTest.java", """
                    package app;

                    public class PingTest {
                        @org.junit.Test
                        public void pings() {
                            new Ping();
                        }
                    }
                    """));

        assertThat(selected("app.Pong")).containsExactly("app.PingTest");
    }

    @Test
    void selectsTheNestedTestClassesThatUseTheChangedClass() throws IOException {
        project(Map.of("app/Greeter.java", SHOP.get("app/Greeter.java")), Map.of("app/OuterTest.java", """
            package app;

            class OuterTest {
                class GreeterTest {
                    @org.junit.jupiter.api.Test
                    void greets() {
                        new Greeter().greet();
                    }
                }
            }
            """));

        assertThat(selected("app.Greeter")).containsExactly("app.OuterTest$GreeterTest");
    }

    @Test
    void leavesOutAChangedClassThatIsNoTest() throws IOException {
        project(SHOP, SHOP_TESTS);

        assertThat(selected("app.Greeter", "app.Service")).doesNotContain("app.Greeter", "app.Service");
    }

    @Test
    void givesEachTestOnceInTheOrderOfItsName() throws IOException {
        project(SHOP, SHOP_TESTS);

        assertThat(selected("app.Service", "app.Repository", "app.Greeter", "app.GreeterTest"))
                .containsExactly("app.GreeterTest", "app.ServiceTest");
    }

    @Test
    void leavesOutTheTestsTheFiltersExclude() throws IOException {
        project(
                SHOP,
                Map.of("app/ServiceIT.java", """
                    package app;

                    public class ServiceIT {
                        @org.junit.Test
                        public void gets() {
                            new Service().get();
                        }
                    }
                    """, "app/ServiceTest.java", SHOP_TESTS.get("app/ServiceTest.java")));
        var filters = TestFilters.parse(Path.of("infinitest.filters"), "exclude .*IT\n");

        assertThat(selected(filters, "app.Service")).containsExactly("app.ServiceTest");
        assertThat(selected(filters, "app.ServiceIT")).isEmpty();
    }

    @Test
    void alwaysSelectsTheTestsThatLoadTheirClassesByThemselvesOnceAnythingChanged() throws IOException {
        project(
                SHOP,
                Map.of(
                        "app/ArchitectureTest.java",
                        """
                        package app;

                        class ArchitectureTest {
                            @com.tngtech.archunit.junit.ArchTest
                            static final int RULE = 1;
                        }
                        """,
                        "app/GreeterTest.java",
                        SHOP_TESTS.get("app/GreeterTest.java")));

        assertThat(selected("app.Repository")).containsExactly("app.ArchitectureTest");
        assertThat(selected("app.Greeter")).containsExactly("app.ArchitectureTest", "app.GreeterTest");
        assertThat(selected()).isEmpty();
    }

    @Test
    void leavesOutADynamicTestTheFiltersExclude() throws IOException {
        project(SHOP, Map.of("app/ArchitectureIT.java", """
            package app;

            class ArchitectureIT {
                @com.tngtech.archunit.junit.ArchTest
                static final int RULE = 1;
            }
            """));
        var filters = TestFilters.parse(Path.of("infinitest.filters"), "exclude .*IT\n");

        assertThat(selected(filters, "app.Greeter")).isEmpty();
    }
}
