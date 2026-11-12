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

    /**
     * What {@link #built()} gives, with the Java files that changed on disk since the changes were last answered for
     * (the files the server was told of, by this or any other request, that were created or edited and still exist).
     * The server is told of the changes and its build waited for as in {@link #built()}. The changes stay until {@link
     * ChangedBuild#answered()}: a call that ends in a refusal does not use them up, and a request of another kind does not
     * either.
     *
     * @throws eu.gabier.shared.error.domain.GeneratorException if the server is not ready
     */
    ChangedBuild builtWithChanges();
}
