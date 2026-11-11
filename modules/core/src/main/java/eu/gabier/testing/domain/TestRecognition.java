package eu.gabier.testing.domain;

import eu.gabier.shared.error.domain.Assert;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Which classes are tests, as Infinitest decides it ({@code JavaAssistClass.isATest}), read in the bytecode: JUnit 3
 * (a method named {@code test...} in a subclass of {@code TestCase}), JUnit 4 ({@code @Test}, {@code @RunWith}), JUnit
 * 5 (a method, a field or the class annotated with {@code @Testable}, directly or through the annotations that carry
 * it: {@code @Test}, {@code @TestTemplate}, {@code @ParameterizedTest}) and TestNG ({@code @Test} on the class or on a
 * method).
 *
 * <p>A class is a test when it is not abstract and either it has test methods (its own, or inherited from its
 * superclasses and its interfaces) and a constructor the runner can use, or a JUnit 5 annotation says so. The
 * constructor must be public and take nothing or the name of the test, unless a runner is named on the class or on a
 * superclass.
 *
 * <p>Where this goes further than Infinitest: the annotated methods that make a JUnit 5 test are looked for in the
 * superclasses too, and an annotation whose class nothing holds is skipped, not taken for the end of the search.
 */
final class TestRecognition {

    private static final String JUNIT_3_TEST_CASE = "junit.framework.TestCase";
    private static final String JUNIT_4_TEST = "org.junit.Test";
    private static final String JUNIT_4_RUN_WITH = "org.junit.runner.RunWith";
    private static final String JUNIT_5_TEST = "org.junit.jupiter.api.Test";
    private static final String JUNIT_5_TEST_TEMPLATE = "org.junit.jupiter.api.TestTemplate";
    private static final String JUNIT_5_TESTABLE = "org.junit.platform.commons.annotation.Testable";
    private static final String TESTNG_TEST = "org.testng.annotations.Test";
    private static final String STRING = "Ljava/lang/String;";

    private final ClassIndex index;

    TestRecognition(ClassIndex index) {
        Assert.notNull("index", index);

        this.index = index;
    }

    boolean isATest(ClassFacts facts) {
        if (facts.abstractType()) {
            return false;
        }

        return (hasTests(facts) && canBeInstantiated(facts)) || isJUnit5Testable(facts);
    }

    /**
     * Whether the class is a test whose dependencies cannot be read: an ArchUnit test loads the classes it checks by
     * itself, so no change can be said not to affect it.
     */
    boolean loadsClassesByItself(ClassFacts facts) {
        return facts.archUnit() && isATest(facts);
    }

    private boolean hasTests(ClassFacts facts) {
        return hasJUnitTestMethods(facts) || usesARunner(facts) || hasTestNgTests(facts);
    }

    private boolean hasJUnitTestMethods(ClassFacts facts) {
        for (ClassFacts type : hierarchyOf(facts)) {
            for (ClassFacts.MethodFacts method : type.methods()) {
                if (isJUnit4Or5Test(method) || isJUnit3Test(type, method)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isJUnit4Or5Test(ClassFacts.MethodFacts method) {
        return method.annotations().stream().anyMatch(this::marksATest);
    }

    /** {@code @Test} of JUnit 4 or 5, or an annotation that is itself annotated with {@code @Test} or {@code @TestTemplate}. */
    private boolean marksATest(String annotation) {
        if (annotation.equals(JUNIT_4_TEST) || annotation.equals(JUNIT_5_TEST)) {
            return true;
        }

        return index.find(annotation)
                .map(type -> type.annotations().contains(JUNIT_5_TEST)
                        || type.annotations().contains(JUNIT_5_TEST_TEMPLATE))
                .orElse(false);
    }

    private boolean isJUnit3Test(ClassFacts declaring, ClassFacts.MethodFacts method) {
        return method.name().startsWith("test")
                && superclassesOf(declaring).stream()
                        .anyMatch(superclass -> superclass.name().equals(JUNIT_3_TEST_CASE));
    }

    private boolean usesARunner(ClassFacts facts) {
        return Set.copyOf(superclassChain(facts)).stream()
                .anyMatch(type -> type.annotations().contains(JUNIT_4_RUN_WITH));
    }

    private boolean hasTestNgTests(ClassFacts facts) {
        if (facts.annotations().contains(TESTNG_TEST)) {
            return true;
        }

        return hierarchyOf(facts).stream()
                .flatMap(type -> type.methods().stream())
                .anyMatch(method -> method.annotations().contains(TESTNG_TEST));
    }

    private boolean canBeInstantiated(ClassFacts facts) {
        return usesARunner(facts) || facts.constructors().stream().anyMatch(TestRecognition::isUsableByJUnit);
    }

    private static boolean isUsableByJUnit(ClassFacts.ConstructorFacts constructor) {
        return constructor.publicConstructor()
                && (constructor.parameterTypes().isEmpty()
                        || constructor.parameterTypes().equals(List.of(STRING)));
    }

    /** Whether the class, or one of its superclasses, holds something annotated as a JUnit 5 test. */
    private boolean isJUnit5Testable(ClassFacts facts) {
        for (ClassFacts type : superclassChain(facts)) {
            if (type.annotations().stream().anyMatch(annotation -> isTestable(annotation, new HashSet<>()))
                    || type.memberAnnotations().stream()
                            .anyMatch(annotation -> isTestable(annotation, new HashSet<>()))) {
                return true;
            }
        }

        return false;
    }

    /** {@code @Testable}, or an annotation annotated with something that is, however far: a cycle is not followed. */
    private boolean isTestable(String annotation, Set<String> visited) {
        if (annotation.equals(JUNIT_5_TESTABLE)) {
            return true;
        }
        if (!visited.add(annotation)) {
            return false;
        }
        Optional<ClassFacts> type = index.find(annotation);

        return type.isPresent() && type.get().annotations().stream().anyMatch(meta -> isTestable(meta, visited));
    }

    /** The class and its superclasses, from the nearest, as far as they are known. */
    private List<ClassFacts> superclassChain(ClassFacts facts) {
        List<ClassFacts> chain = new ArrayList<>(List.of(facts));
        chain.addAll(superclassesOf(facts));

        return chain;
    }

    private List<ClassFacts> superclassesOf(ClassFacts facts) {
        List<ClassFacts> superclasses = new ArrayList<>();
        Set<String> seen = new HashSet<>(Set.of(facts.name()));
        Optional<ClassFacts> current = facts.superclass().flatMap(index::find);
        while (current.isPresent() && seen.add(current.get().name())) {
            superclasses.add(current.get());
            current = current.get().superclass().flatMap(index::find);
        }

        return superclasses;
    }

    /** The class, its superclasses and every interface any of them implements, each once. */
    private List<ClassFacts> hierarchyOf(ClassFacts facts) {
        List<ClassFacts> hierarchy = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ClassFacts type : superclassChain(facts)) {
            collect(type, hierarchy, seen);
        }

        return hierarchy;
    }

    private void collect(ClassFacts type, List<ClassFacts> hierarchy, Set<String> seen) {
        if (!seen.add(type.name())) {
            return;
        }
        hierarchy.add(type);
        for (String name : type.interfaces()) {
            index.find(name).ifPresent(anInterface -> collect(anInterface, hierarchy, seen));
        }
    }
}
