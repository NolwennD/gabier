package eu.gabier.testing.domain;

import eu.gabier.shared.error.domain.GeneratorException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Every refusal of the testing context. Its {@link #key()} says which one, its {@link #parameters()} give the values a
 * report shows.
 */
public final class TestingException extends GeneratorException {

    private TestingException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    static TestingException invalidTestFilter(Path file, int line, String pattern, String reason) {
        return new TestingException(GeneratorException.badRequest(TestingErrorKey.INVALID_TEST_FILTER)
                .message("The pattern " + pattern + " of " + file + ", line " + line + ", is not a regular expression: "
                        + reason)
                .addParameter("file", file.toString())
                .addParameter("line", String.valueOf(line))
                .addParameter("pattern", pattern));
    }

    static TestingException fileMissing(Path file) {
        return new TestingException(GeneratorException.badRequest(TestingErrorKey.FILE_MISSING)
                .message("The file " + file + " does not exist")
                .addParameter("file", file.toString()));
    }

    static TestingException notInASourceFolder(Path file) {
        return new TestingException(GeneratorException.badRequest(TestingErrorKey.NOT_IN_A_SOURCE_FOLDER)
                .message("The file " + file + " is not under a source folder of the project: no class of the build"
                        + " comes from it")
                .addParameter("file", file.toString()));
    }

    /**
     * The files that were given and have an error: the compiler writes a class for them all the same, with the bad
     * methods replaced, so the dependencies read in it would be wrong.
     */
    static TestingException compilationFailed(List<Path> files) {
        String names = files.stream().map(Path::toString).collect(Collectors.joining(", "));

        return new TestingException(GeneratorException.badRequest(TestingErrorKey.COMPILATION_FAILED)
                .message("These files do not compile: " + names + ". The class the compiler wrote for a file with an"
                        + " error is not the class of its text, so the tests it affects cannot be told: fix the errors"
                        + " first")
                .addParameter("files", names));
    }

    /**
     * The files of the test classes to run have an error: the classes the build wrote for them are stubs, and a test
     * that ran on them would fail for what the compiler said, not for what the test checks.
     */
    public static TestingException notRunnableBecauseOfErrors(List<Path> files) {
        String names = files.stream().map(Path::toString).collect(Collectors.joining(", "));

        return new TestingException(GeneratorException.badRequest(TestingErrorKey.COMPILATION_FAILED)
                .message("These files do not compile: " + names + ". The classes the compiler wrote for them are not"
                        + " the classes of their text, so the tests would not run on the code you wrote: fix the"
                        + " errors first (gabier diagnostics tells them)")
                .addParameter("files", names));
    }

    public static TestingException noSuchTestClass(String className) {
        return new TestingException(GeneratorException.badRequest(TestingErrorKey.NO_SUCH_TEST_CLASS)
                .message("The build has no class " + className + ": give the binary name of a class of the project"
                        + " (app.GreeterTest, app.Outer$Inner)")
                .addParameter("class", className));
    }

    /**
     * No test engine of the class path recognises these classes as tests.
     *
     * @param classes the binary names of the classes
     */
    public static TestingException noTestEngine(List<String> classes) {
        String names = String.join(", ", classes);

        return new TestingException(GeneratorException.badRequest(TestingErrorKey.NO_TEST_ENGINE)
                .message("No test engine of the class path recognises " + names + " as tests: a class that holds no"
                        + " test, or tests whose engine (JUnit Jupiter, JUnit Vintage, ...) the class path does not"
                        + " have. Nothing was run")
                .addParameter("classes", names));
    }

    /**
     * The class path of the tests has no JUnit Platform launcher: gabier runs the tests with the libraries of the
     * project, and adds none of its own.
     */
    public static TestingException noTestLauncher() {
        return new TestingException(GeneratorException.badRequest(TestingErrorKey.NO_TEST_LAUNCHER)
                .message("The class path of the tests has no JUnit Platform launcher (the class"
                        + " org.junit.platform.launcher.core.LauncherFactory), which runs the tests: add"
                        + " org.junit.platform:junit-platform-launcher to the test dependencies of the project, at the"
                        + " version of its JUnit. Nothing was run"));
    }

    /**
     * The tests did not end within the time limit and were stopped.
     *
     * @param timeLimit what was allowed
     */
    public static TestingException timedOut(Duration timeLimit) {
        return new TestingException(GeneratorException.badRequest(TestingErrorKey.TEST_TIMEOUT)
                .message("The tests did not end within " + timeLimit.toSeconds() + " seconds and were stopped: a"
                        + " test that never ends, or a time limit that is too short for them")
                .addParameter("seconds", String.valueOf(timeLimit.toSeconds())));
    }

    /**
     * The process of the tests ended with no report: a test stopped the JVM, ran it out of memory or crashed it.
     *
     * @param output the end of what the process wrote, which says why
     */
    public static TestingException endedWithoutReport(String output) {
        return new TestingException(GeneratorException.badRequest(TestingErrorKey.RUN_FAILED)
                .message("The process of the tests ended with no report: a test stopped the JVM, ran out of memory or"
                        + " crashed it. What it wrote last: " + output)
                .addParameter("output", output));
    }

    /**
     * The process of the tests could not be started.
     *
     * @param cause what failed
     */
    public static TestingException notStarted(Exception cause) {
        return new TestingException(GeneratorException.internalServerError(TestingErrorKey.RUN_FAILED)
                .message("The process of the tests could not be started: " + cause.getMessage())
                .cause(cause));
    }

    static TestingException readFailed(Path file, Exception cause) {
        return new TestingException(GeneratorException.internalServerError(TestingErrorKey.READ_FAILED)
                .message("The file " + file + " could not be read: " + cause.getMessage())
                .cause(cause)
                .addParameter("file", file.toString()));
    }
}
