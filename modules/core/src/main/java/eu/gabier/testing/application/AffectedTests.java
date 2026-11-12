package eu.gabier.testing.application;

import eu.gabier.shared.error.domain.Assert;
import eu.gabier.testing.domain.ChangedBuild;
import eu.gabier.testing.domain.ProjectBuild;
import eu.gabier.testing.domain.ProjectFiles;
import eu.gabier.testing.domain.TestSelector;
import java.nio.file.Path;
import java.util.List;

/** The tests that a change to some Java files affects, from what the build of the project wrote. */
public final class AffectedTests {

    private final ProjectBuild build;
    private final TestSelector selector;

    /**
     * Creates the use case on a project.
     *
     * @param build the build of the project
     * @param files where the classes, the sources and the filters are
     * @param root the root of the project, which holds the {@code infinitest.filters} file
     */
    public AffectedTests(ProjectBuild build, ProjectFiles files, Path root) {
        Assert.notNull("build", build);

        this.build = build;
        this.selector = new TestSelector(files, root);
    }

    /**
     * Waits for the build to have compiled the files, then selects the tests they affect.
     *
     * @param sources the files that changed, as absolute paths; the files that are not Java sources are left out
     * @return the binary names of the tests to run again, in the order of the names, none when no test is affected
     * @throws eu.gabier.shared.error.domain.GeneratorException if the build is not ready, a Java file does not exist,
     *     is under no source folder or does not compile, a class or the filters cannot be read, or a filter is not a
     *     regular expression
     */
    public List<String> affectedBy(List<Path> sources) {
        Assert.field("sources", sources).notNull().notEmpty().noNullElement();

        return selector.affectedBy(build.built(), sources);
    }

    /**
     * Waits for the build to have compiled what changed on disk since the last answer, then selects the tests that
     * change affects. The changes are forgotten once the tests are answered, and not when the call is refused.
     *
     * @return the binary names of the tests to run again, in the order of the names, none when nothing changed or no
     *     test is affected
     * @throws eu.gabier.shared.error.domain.GeneratorException if the build is not ready, a changed Java file does not
     *     compile, a class or the filters cannot be read, or a filter is not a regular expression
     */
    public List<String> affectedByChanges() {
        ChangedBuild changed = build.builtWithChanges();
        List<String> tests = selector.affectedByChanges(changed.built(), changed.sources());
        changed.answered();

        return tests;
    }
}
