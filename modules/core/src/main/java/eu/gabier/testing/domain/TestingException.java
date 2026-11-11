package eu.gabier.testing.domain;

import eu.gabier.shared.error.domain.GeneratorException;
import java.nio.file.Path;
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

    static TestingException readFailed(Path file, Exception cause) {
        return new TestingException(GeneratorException.internalServerError(TestingErrorKey.READ_FAILED)
                .message("The file " + file + " could not be read: " + cause.getMessage())
                .cause(cause)
                .addParameter("file", file.toString()));
    }
}
