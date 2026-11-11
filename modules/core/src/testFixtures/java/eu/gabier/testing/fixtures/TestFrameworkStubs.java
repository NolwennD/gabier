package eu.gabier.testing.fixtures;

import java.util.Map;

/**
 * The annotations and the base class of the test frameworks, as source, so that a small project uses them with no
 * library: only the names and the meta-annotation {@code Testable} matter to the selection of tests, which recognises a
 * framework by them. The sources of {@link #JUNIT_5} stand for the jar of the test class path that a build tool gives
 * ({@code @Test} of JUnit 5 is a test because it is annotated {@code @Testable}).
 */
public final class TestFrameworkStubs {

    /** {@code org.junit.platform.commons.annotation.Testable}, {@code @Test}, {@code @TestTemplate} and {@code @ParameterizedTest}. */
    public static final Map<String, String> JUNIT_5 = Map.of(
            "org/junit/platform/commons/annotation/Testable.java",
            annotation("org.junit.platform.commons.annotation", "Testable", ""),
            "org/junit/jupiter/api/Test.java",
            annotation("org.junit.jupiter.api", "Test", "@org.junit.platform.commons.annotation.Testable"),
            "org/junit/jupiter/api/TestTemplate.java",
            annotation("org.junit.jupiter.api", "TestTemplate", "@org.junit.platform.commons.annotation.Testable"),
            "org/junit/jupiter/params/ParameterizedTest.java",
            annotation("org.junit.jupiter.params", "ParameterizedTest", "@org.junit.jupiter.api.TestTemplate"));

    /** {@code org.junit.Test}, {@code org.junit.runner.RunWith} and {@code junit.framework.TestCase}. */
    public static final Map<String, String> JUNIT_4_AND_3 = Map.of(
            "org/junit/Test.java",
            annotation("org.junit", "Test", ""),
            "org/junit/runner/RunWith.java",
            """
            package org.junit.runner;

            @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
            public @interface RunWith {
                Class<?> value();
            }
            """,
            "junit/framework/TestCase.java",
            """
            package junit.framework;

            public abstract class TestCase {}
            """);

    /** {@code org.testng.annotations.Test}. */
    public static final Map<String, String> TESTNG =
            Map.of("org/testng/annotations/Test.java", annotation("org.testng.annotations", "Test", ""));

    /** {@code com.tngtech.archunit.junit.ArchTest} and {@code AnalyzeClasses}. */
    public static final Map<String, String> ARCHUNIT = Map.of(
            "com/tngtech/archunit/junit/ArchTest.java",
            annotation("com.tngtech.archunit.junit", "ArchTest", "@org.junit.platform.commons.annotation.Testable"),
            "com/tngtech/archunit/junit/AnalyzeClasses.java",
            annotation("com.tngtech.archunit.junit", "AnalyzeClasses", ""));

    private TestFrameworkStubs() {}

    private static String annotation(String packageName, String name, String metaAnnotation) {
        return """
            package %s;

            @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
            %s
            public @interface %s {}
            """.formatted(packageName, metaAnnotation, name);
    }
}
