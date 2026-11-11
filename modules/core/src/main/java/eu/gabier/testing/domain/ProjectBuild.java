package eu.gabier.testing.domain;

/**
 * The build of the project, as the language server that compiles it gives it: the adapter of a real server lives at the
 * edge, and the tests of the core compile with javac and hand the result over.
 */
public interface ProjectBuild {

    /**
     * What the build gives once it has compiled the files that changed on disk: the server is told of them, waits for
     * its build to end and reports the classes it wrote, and the errors it found.
     *
     * @throws eu.gabier.shared.error.domain.GeneratorException if the server is not ready
     */
    BuiltProject built();
}
