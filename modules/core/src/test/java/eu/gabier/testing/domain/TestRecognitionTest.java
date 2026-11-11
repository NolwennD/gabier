package eu.gabier.testing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.testing.fixtures.CompiledProject;
import eu.gabier.testing.infrastructure.secondary.NioProjectFiles;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestRecognitionTest {

    @TempDir
    Path root;

    private ClassIndex index;
    private TestRecognition recognition;

    private void project(Map<String, String> sources) throws IOException {
        index = new ClassIndex(new NioProjectFiles(), CompiledProject.of(root, Map.of(), sources));
        recognition = new TestRecognition(index);
    }

    private boolean isATest(String name) {
        return recognition.isATest(index.find(name).orElseThrow());
    }

    @Nested
    class JUnit4 {

        @Test
        void isATestWhenAPublicMethodIsAnnotatedAndTheClassHasAPublicConstructorWithNoParameter() throws IOException {
            project(Map.of("app/GreeterTest.java", """
                package app;

                public class GreeterTest {
                    @org.junit.Test
                    public void greets() {}
                }
                """));

            assertThat(isATest("app.GreeterTest")).isTrue();
        }

        @Test
        void isATestWhenItsPublicConstructorTakesTheNameOfTheTest() throws IOException {
            project(Map.of("app/NamedTest.java", """
                package app;

                public class NamedTest {
                    public NamedTest(String name) {}

                    @org.junit.Test
                    public void greets() {}
                }
                """));

            assertThat(isATest("app.NamedTest")).isTrue();
        }

        @Test
        void isNotATestWhenNoConstructorCanBeUsedByTheRunner() throws IOException {
            project(Map.of("app/UnbuildableTest.java", """
                package app;

                public class UnbuildableTest {
                    public UnbuildableTest(int count) {}

                    @org.junit.Test
                    public void greets() {}
                }
                """));

            assertThat(isATest("app.UnbuildableTest")).isFalse();
        }

        @Test
        void isNotATestWhenTheAnnotatedMethodIsPrivate() throws IOException {
            project(Map.of("app/HiddenTest.java", """
                package app;

                public class HiddenTest {
                    @org.junit.Test
                    private void greets() {}
                }
                """));

            assertThat(isATest("app.HiddenTest")).isFalse();
        }

        @Test
        void isATestThroughTheAnnotatedMethodOfItsSuperclassButNotTheAbstractSuperclass() throws IOException {
            project(Map.of("app/Tests.java", """
                package app;

                public class Tests {}

                abstract class BaseTest {
                    @org.junit.Test
                    public void greets() {}
                }

                class ConcreteTest extends BaseTest {
                    public ConcreteTest() {}
                }
                """));

            assertThat(isATest("app.BaseTest")).isFalse();
            assertThat(isATest("app.ConcreteTest")).isTrue();
        }

        @Test
        void isNotATestForAnInterface() throws IOException {
            project(Map.of("app/Contract.java", """
                package app;

                public interface Contract {
                    @org.junit.Test
                    default void holds() {}
                }
                """));

            assertThat(isATest("app.Contract")).isFalse();
        }

        @Test
        void isATestWhenARunnerIsNamedOnTheClassWhateverItsMethodsAndConstructors() throws IOException {
            project(Map.of("app/RunTest.java", """
                package app;

                @org.junit.runner.RunWith(Object.class)
                class RunTest {
                    RunTest(int count) {}
                }

                class SubRunTest extends RunTest {
                    SubRunTest() {
                        super(1);
                    }
                }
                """));

            assertThat(isATest("app.RunTest")).isTrue();
            assertThat(isATest("app.SubRunTest")).isTrue();
        }
    }

    @Nested
    class CorruptedClasses {

        @Test
        void isNotATestAndDoesNotLoopWhenTheSuperclassesLoopBackOnTheClass() throws IOException {
            BuiltProject built = CompiledProject.of(
                    root, Map.of(), Map.of("app/Left.java", "package app; class Left {} class Right extends Left {}"));
            Files.write(
                    root.resolve("test-bin/app/Left.class"),
                    ClassFile.of()
                            .build(
                                    ClassDesc.of("app.Left"),
                                    builder -> builder.withSuperclass(ClassDesc.of("app.Right"))));
            index = new ClassIndex(new NioProjectFiles(), built);
            recognition = new TestRecognition(index);

            assertThat(isATest("app.Right")).isFalse();
        }
    }

    @Nested
    class JUnit3 {

        @Test
        void isATestWhenItExtendsTestCaseAndHasAMethodNamedTest() throws IOException {
            project(Map.of("app/OldTest.java", """
                package app;

                public class OldTest extends junit.framework.TestCase {
                    public void testGreets() {}
                }
                """));

            assertThat(isATest("app.OldTest")).isTrue();
        }

        @Test
        void isNotATestWhenNoMethodIsNamedTest() throws IOException {
            project(Map.of("app/HelperTest.java", """
                package app;

                public class HelperTest extends junit.framework.TestCase {
                    public void helper() {}
                }
                """));

            assertThat(isATest("app.HelperTest")).isFalse();
        }

        @Test
        void isNotATestWhenAMethodIsNamedTestButTheClassIsNoTestCase() throws IOException {
            project(Map.of("app/Plain.java", """
                package app;

                public class Plain {
                    public void testGreets() {}
                }
                """));

            assertThat(isATest("app.Plain")).isFalse();
        }
    }

    @Nested
    class TestNg {

        @Test
        void isATestWhenTheClassIsAnnotated() throws IOException {
            project(Map.of("app/NgTest.java", """
                package app;

                @org.testng.annotations.Test
                public class NgTest {}
                """));

            assertThat(isATest("app.NgTest")).isTrue();
        }

        @Test
        void isATestThroughTheAnnotatedMethodOfAnInterfaceThatItsClassAndItsSuperclassImplement() throws IOException {
            project(Map.of("app/Contract.java", """
                package app;

                public interface Contract {
                    @org.testng.annotations.Test
                    default void holds() {}
                }

                class Base implements Contract {}

                class ContractTest extends Base implements Contract {
                    public ContractTest() {}
                }
                """));

            assertThat(isATest("app.ContractTest")).isTrue();
        }

        @Test
        void isATestWhenAMethodIsAnnotated() throws IOException {
            project(Map.of("app/NgMethodTest.java", """
                package app;

                public class NgMethodTest {
                    @org.testng.annotations.Test
                    public void greets() {}
                }
                """));

            assertThat(isATest("app.NgMethodTest")).isTrue();
        }
    }

    @Nested
    class JUnit5 {

        @Test
        void isATestWhenAMethodIsAnnotatedEvenWhenTheClassAndTheMethodArePackagePrivate() throws IOException {
            project(Map.of("app/GreeterTest.java", """
                package app;

                import org.junit.jupiter.api.Test;

                class GreeterTest {
                    @Test
                    void greets() {}
                }
                """));

            assertThat(isATest("app.GreeterTest")).isTrue();
        }

        @Test
        void isATestWhenTheAnnotationIsItselfMarkedAsATestTemplate() throws IOException {
            project(Map.of("app/ParameterizedGreeterTest.java", """
                package app;

                import org.junit.jupiter.params.ParameterizedTest;

                class ParameterizedGreeterTest {
                    @ParameterizedTest
                    void greets() {}
                }
                """));

            assertThat(isATest("app.ParameterizedGreeterTest")).isTrue();
        }

        @Test
        void isATestWhenTheAnnotationIsOneOfTheProjectThatIsMarkedAsATest() throws IOException {
            project(Map.of("app/SlowGreeterTest.java", """
                package app;

                @org.junit.jupiter.api.Test
                @interface Slow {}

                class SlowGreeterTest {
                    @Slow
                    void greets() {}
                }
                """));

            assertThat(isATest("app.SlowGreeterTest")).isTrue();
        }

        @Test
        void isATestWhenAnAnnotationOnTheClassItselfIsMarkedAsATest() throws IOException {
            project(Map.of("app/MarkedTest.java", """
                package app;

                @org.junit.platform.commons.annotation.Testable
                @interface Marked {}

                @Marked
                class MarkedTest {}
                """));

            assertThat(isATest("app.MarkedTest")).isTrue();
        }

        @Test
        void followsTheAnnotationsOfAnAnnotationOnceWhenTheyLoopBackOnThemselves() throws IOException {
            project(Map.of("app/LoopingTest.java", """
                package app;

                @Second
                @interface First {}

                @First
                @interface Second {}

                @First
                class LoopingTest {}
                """));

            assertThat(isATest("app.LoopingTest")).isFalse();
        }

        @Test
        void isATestForTheNestedClassThatHoldsTheTestsAndNotForItsOuterClass() throws IOException {
            project(Map.of("app/OuterTest.java", """
                package app;

                class OuterTest {
                    class InnerTest {
                        @org.junit.jupiter.api.Test
                        void greets() {}
                    }
                }
                """));

            assertThat(isATest("app.OuterTest$InnerTest")).isTrue();
            assertThat(isATest("app.OuterTest")).isFalse();
        }

        @Test
        void isATestThroughTheAnnotatedMethodOfItsAbstractSuperclass() throws IOException {
            project(Map.of("app/BaseTest.java", """
                package app;

                abstract class BaseTest {
                    @org.junit.jupiter.api.Test
                    void greets() {}
                }

                class ConcreteTest extends BaseTest {}
                """));

            assertThat(isATest("app.BaseTest")).isFalse();
            assertThat(isATest("app.ConcreteTest")).isTrue();
        }

        @Test
        void isNotATestWhenNoAnnotationOnItIsAboutTests() throws IOException {
            project(Map.of("app/Greeter.java", """
                package app;

                @Deprecated
                class Greeter {
                    @Deprecated
                    void greet() {}
                }
                """));

            assertThat(isATest("app.Greeter")).isFalse();
        }
    }

    @Nested
    class Dynamic {

        @Test
        void isATestThatLoadsClassesByItselfWhenAFieldIsAnArchUnitTest() throws IOException {
            project(Map.of("app/ArchitectureTest.java", """
                package app;

                class ArchitectureTest {
                    @com.tngtech.archunit.junit.ArchTest
                    static final int RULE = 1;
                }
                """));

            var facts = index.find("app.ArchitectureTest").orElseThrow();

            assertThat(recognition.isATest(facts)).isTrue();
            assertThat(recognition.loadsClassesByItself(facts)).isTrue();
        }

        @Test
        void isNotOneForAnOrdinaryTest() throws IOException {
            project(Map.of("app/GreeterTest.java", """
                package app;

                public class GreeterTest {
                    @org.junit.Test
                    public void greets() {}
                }
                """));

            assertThat(recognition.loadsClassesByItself(
                            index.find("app.GreeterTest").orElseThrow()))
                    .isFalse();
        }

        @Test
        void isNotOneForAClassThatIsNoTest() throws IOException {
            project(Map.of("app/Helper.java", """
                package app;

                class Helper {
                    @com.tngtech.archunit.junit.AnalyzeClasses
                    void rule() {}
                }
                """));

            assertThat(recognition.loadsClassesByItself(index.find("app.Helper").orElseThrow()))
                    .isFalse();
        }
    }
}
