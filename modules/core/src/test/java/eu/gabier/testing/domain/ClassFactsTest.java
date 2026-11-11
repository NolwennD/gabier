package eu.gabier.testing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.testing.fixtures.CompiledSources;
import eu.gabier.testing.fixtures.TestFrameworkStubs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassFactsTest {

    @TempDir
    Path classes;

    private ClassFacts compiled(String name, Map<String, String> sources) throws IOException {
        Map<String, String> all = new HashMap<>(sources);
        all.putAll(TestFrameworkStubs.JUNIT_4_AND_3);
        all.putAll(TestFrameworkStubs.ARCHUNIT);
        all.put("app/Marker.java", """
            package app;

            @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
            public @interface Marker {}
            """);
        CompiledSources.compile(all, classes, List.of());
        Path file = classes.resolve(name.replace('.', '/') + ".class");

        return ClassFacts.read(file, Files.readAllBytes(file));
    }

    @Test
    void namesTheClassItsSourceFileItsSuperclassAndItsInterfaces() throws IOException {
        var facts = compiled("app.Child", Map.of("app/Child.java", """
            package app;

            public class Child extends Base implements Runnable {
                public void run() {}
            }

            abstract class Base {}
            """));

        assertThat(facts.name()).isEqualTo("app.Child");
        assertThat(facts.sourceFile()).contains("Child.java");
        assertThat(facts.superclass()).contains("app.Base");
        assertThat(facts.interfaces()).containsExactly("java.lang.Runnable");
        assertThat(facts.abstractType()).isFalse();
    }

    @Test
    void namesANestedClassByItsBinaryNameAndKeepsTheFileOfItsOuterClass() throws IOException {
        var facts = compiled("app.Outer$Inner", Map.of("app/Outer.java", """
            package app;

            public class Outer {
                public static class Inner {}
            }
            """));

        assertThat(facts.name()).isEqualTo("app.Outer$Inner");
        assertThat(facts.sourceFile()).contains("Outer.java");
    }

    @Test
    void takesAnInterfaceAndAnAbstractClassForAbstract() throws IOException {
        assertThat(compiled("app.Shape", Map.of("app/Shape.java", "package app; public interface Shape {}"))
                        .abstractType())
                .isTrue();
        assertThat(compiled("app.Base", Map.of("app/Base.java", "package app; public abstract class Base {}"))
                        .abstractType())
                .isTrue();
    }

    @Test
    void givesObjectAsTheSuperclassOfAnInterface() throws IOException {
        var facts = compiled("app.Shape", Map.of("app/Shape.java", "package app; public interface Shape {}"));

        assertThat(facts.superclass()).contains("java.lang.Object");
    }

    @Test
    void dependsOnTheClassesItNamesInItsBytecodeItsFieldsAndItsAnnotations() throws IOException {
        var facts = compiled("app.Uses", Map.of("app/Uses.java", """
            package app;

            @Marker
            public class Uses extends Parent {
                Declared declared;
                Item[][] items;
                int count;
                @Marker Annotated annotated;

                public void run(@Marker Parameter parameter) {
                    Called.call();
                }
            }

            class Parent {}
            class Declared {}
            class Item {}
            class Annotated {}
            class Parameter {}
            class Called { static void call() {} }
            """));

        assertThat(facts.dependencies())
                .contains(
                        "app.Uses",
                        "app.Parent",
                        "app.Declared",
                        "app.Item",
                        "app.Annotated",
                        "app.Called",
                        "app.Marker")
                .noneMatch(name -> name.startsWith("[") || name.equals("int"));
    }

    @Test
    void dependsOnTheTypeOfAnAnnotationOfAParameter() throws IOException {
        var facts = compiled("app.Annotating", Map.of("app/Annotating.java", """
            package app;

            public class Annotating {
                public void run(@Marker String parameter) {}
            }
            """));

        assertThat(facts.dependencies()).contains("app.Marker");
    }

    @Test
    void dependsOnTheTypeOfAnAnnotationOfAParameterThatTheCompilerKeepsOutOfTheRuntime() throws IOException {
        var facts = compiled("app.Annotating", Map.of("app/Annotating.java", """
            package app;

            @interface Quiet {}

            public class Annotating {
                public void run(@Quiet String parameter) {}
            }
            """));

        assertThat(facts.dependencies()).contains("app.Quiet");
    }

    @Test
    void dependsOnTheElementOfAnArrayThatTheBytecodeNamesAsAClass() throws IOException {
        var facts = compiled("app.Casting", Map.of("app/Casting.java", """
            package app;

            public class Casting {
                public Object run(Object value) {
                    return (Slot[]) value;
                }
            }

            class Slot {}
            """));

        assertThat(facts.dependencies()).contains("app.Slot").noneMatch(name -> name.startsWith("["));
    }

    @Test
    void listsTheAnnotationsOfTheClassAndOfItsMethodsThatAreNotPrivate() throws IOException {
        var facts = compiled("app.Annotated", Map.of("app/Annotated.java", """
            package app;

            @Marker
            public class Annotated {
                @Marker public void run() {}
                void quiet() {}
                @org.junit.Test private void hidden() {}
            }
            """));

        assertThat(facts.annotations()).containsExactly("app.Marker");
        assertThat(facts.methods())
                .containsExactlyInAnyOrder(
                        new ClassFacts.MethodFacts("run", java.util.Set.of("app.Marker")),
                        new ClassFacts.MethodFacts("quiet", java.util.Set.of()));
    }

    @Test
    void listsTheConstructorsThatAreNotPrivateWithTheirParameterTypes() throws IOException {
        var facts = compiled("app.Built", Map.of("app/Built.java", """
            package app;

            public class Built {
                public Built() {}
                public Built(String name) {}
                Built(int count, String[] names) {}
                private Built(long id) {}
            }
            """));

        assertThat(facts.constructors())
                .containsExactlyInAnyOrder(
                        new ClassFacts.ConstructorFacts(true, List.of()),
                        new ClassFacts.ConstructorFacts(true, List.of("Ljava/lang/String;")),
                        new ClassFacts.ConstructorFacts(false, List.of("I", "[Ljava/lang/String;")));
    }

    @Test
    void collectsTheAnnotationsOfTheFieldsAndOfEveryMethodIncludingThePrivateOnes() throws IOException {
        var facts = compiled("app.Members", Map.of("app/Members.java", """
            package app;

            public class Members {
                @Marker int field;
                @org.junit.Test private void hidden() {}
            }
            """));

        assertThat(facts.memberAnnotations()).containsExactlyInAnyOrder("app.Marker", "org.junit.Test");
    }

    @Test
    void seesAnArchUnitTestOnAFieldAndOnAMethod() throws IOException {
        assertThat(compiled("app.OnField", Map.of("app/OnField.java", """
            package app;

            public class OnField {
                @com.tngtech.archunit.junit.ArchTest int rule;
            }
            """)).archUnit())
                .isTrue();
        assertThat(compiled("app.OnMethod", Map.of("app/OnMethod.java", """
            package app;

            public class OnMethod {
                @com.tngtech.archunit.junit.ArchTest void rule() {}
            }
            """)).archUnit())
                .isTrue();
    }

    @Test
    void seesNoArchUnitTestInAClassThatIsOnlyAnnotatedToAnalyseClasses() throws IOException {
        var facts = compiled("app.Analysing", Map.of("app/Analysing.java", """
            package app;

            @com.tngtech.archunit.junit.AnalyzeClasses
            public class Analysing {}
            """));

        assertThat(facts.archUnit()).isFalse();
    }

    @Test
    void hasNoSourceFileWhenTheClassFileSaysNone() {
        // a class file made by hand: the smallest class, with no SourceFile attribute
        byte[] bytes =
                java.lang.classfile.ClassFile.of().build(java.lang.constant.ClassDesc.of("app.Bare"), builder -> {});

        assertThat(ClassFacts.read(Path.of("Bare.class"), bytes).sourceFile()).isEqualTo(Optional.empty());
    }

    @Test
    void refusesAClassFileOfAJavaNewerThanTheOneThatReadsIt() {
        byte[] bytes =
                java.lang.classfile.ClassFile.of().build(java.lang.constant.ClassDesc.of("app.Bare"), builder -> {});
        bytes[6] = 0x7f;
        Path file = Path.of("/project/bin/Newer.class");

        assertThatExceptionOfType(TestingException.class)
                .isThrownBy(() -> ClassFacts.read(file, bytes))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(TestingErrorKey.READ_FAILED));
    }

    @Test
    void refusesBytesThatAreNoClassFileNamingTheFile() {
        Path file = Path.of("/project/bin/Broken.class");

        assertThatExceptionOfType(TestingException.class)
                .isThrownBy(() -> ClassFacts.read(file, new byte[] {1, 2, 3, 4}))
                .satisfies(exception -> {
                    assertThat(exception.key()).isEqualTo(TestingErrorKey.READ_FAILED);
                    assertThat(exception.parameters()).containsEntry("file", file.toString());
                });
    }
}
