package eu.gabier.testing.application;

import eu.gabier.shared.error.domain.Assert;
import eu.gabier.testing.domain.BuiltProject;
import eu.gabier.testing.domain.ClassFolder;
import eu.gabier.testing.domain.ProjectBuild;
import eu.gabier.testing.domain.ProjectFiles;
import eu.gabier.testing.domain.TestReport;
import eu.gabier.testing.domain.TestRun;
import eu.gabier.testing.domain.TestRunner;
import eu.gabier.testing.domain.TestSelector;
import eu.gabier.testing.domain.TestingException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

/**
 * Runs tests in a process of their own, on the classes the build of the project wrote: the tests a change affects, or
 * test classes that are given. No build tool is called: the class path is the one the build gives the tests.
 */
public final class RunTests {

    private final ProjectBuild build;
    private final ProjectFiles files;
    private final Path root;
    private final TestRunner runner;
    private final TestSelector selector;

    /**
     * Creates the use case on a project.
     *
     * @param build the build of the project
     * @param files where the classes, the sources and the filters are
     * @param root the root of the project, which holds the {@code infinitest.filters} file and where the tests run
     * @param runner what runs the tests
     */
    public RunTests(ProjectBuild build, ProjectFiles files, Path root, TestRunner runner) {
        Assert.notNull("build", build);
        Assert.notNull("files", files);
        Assert.notNull("root", root);
        Assert.notNull("runner", runner);

        this.build = build;
        this.files = files;
        this.root = root;
        this.runner = runner;
        this.selector = new TestSelector(files, root);
    }

    /**
     * Runs the given test classes.
     *
     * @param classes the binary names of the test classes, at least one
     * @param timeLimit how long the tests may take in all
     * @return what the run gave: the tests that failed are in it, they are not a refusal
     * @throws eu.gabier.shared.error.domain.GeneratorException if the build is not ready, a Java file of the project
     *     does not compile, a class is not one of the build, no test engine recognises a class, the tests take longer
     *     than the time limit or the process of the tests ends with no report
     */
    public TestReport run(List<String> classes, Duration timeLimit) {
        Assert.field("classes", classes).notNull().notEmpty().noNullElement();
        Assert.notNull("timeLimit", timeLimit);

        BuiltProject built = build.built();
        classes.forEach(name -> refuseWhenNotInTheBuild(built, name));
        refuseWhenATestDoesNotCompile(built, classes);

        return runner.run(new TestRun(classes.stream().distinct().toList(), classpathOf(built), root, timeLimit));
    }

    /**
     * Waits for the build to have compiled what changed on disk since the last answer, selects the tests that change
     * affects ({@link AffectedTests#affectedByChanges}) and runs them. The changes are forgotten once the tests have
     * run, whether they passed or not; a refusal keeps them.
     *
     * @param timeLimit how long the tests may take in all
     * @return what the run gave, nothing ran when no test is affected
     * @throws eu.gabier.shared.error.domain.GeneratorException as {@link #run} does, and as {@link
     *     AffectedTests#affectedByChanges} does for the selection
     */
    public TestReport runAffectedByChanges(Duration timeLimit) {
        Assert.notNull("timeLimit", timeLimit);

        var changed = build.builtWithChanges();
        List<String> tests = selector.affectedByChanges(changed.built(), changed.sources());
        TestReport report;
        if (tests.isEmpty()) {
            report = TestReport.nothingRan();
        } else {
            refuseWhenATestDoesNotCompile(changed.built(), tests);
            report = runner.run(new TestRun(tests, classpathOf(changed.built()), root, timeLimit));
        }
        changed.answered();

        return report;
    }

    /**
     * The file of a test class that has an error is the one case the run is refused for: the class the build wrote for it
     * is a stub, and the run would test the compiler's message, not the code. Another file with an error is not a reason
     * to refuse: a project of many modules has files the language server cannot compile whatever the tests do, and a test
     * that reaches a stub fails with the message of the compiler, which the report gives.
     */
    private static void refuseWhenATestDoesNotCompile(BuiltProject built, List<String> classes) {
        List<Path> sources = classes.stream().map(RunTests::sourceFileOf).toList();
        List<Path> broken = built.filesWithErrors().stream()
                .filter(file -> sources.stream().anyMatch(file::endsWith))
                .toList();
        if (!broken.isEmpty()) {
            throw TestingException.notRunnableBecauseOfErrors(broken);
        }
    }

    /** The path a top-level class is declared in, under its source folder: {@code app/GreeterTest.java}. */
    private static Path sourceFileOf(String className) {
        int nested = className.indexOf('$');
        String topLevel = nested < 0 ? className : className.substring(0, nested);

        return Path.of(topLevel.replace('.', '/') + ".java");
    }

    private void refuseWhenNotInTheBuild(BuiltProject built, String className) {
        Path classFile = Path.of(className.replace('.', '/') + ".class");
        if (built.folders().stream()
                .map(ClassFolder::classes)
                .noneMatch(folder -> files.isFile(folder.resolve(classFile)))) {
            throw TestingException.noSuchTestClass(className);
        }
    }

    /** The class folders of the build, then the libraries it compiled them against, each once. */
    private static List<Path> classpathOf(BuiltProject built) {
        return Stream.concat(built.folders().stream().map(ClassFolder::classes), built.libraries().stream())
                .distinct()
                .toList();
    }
}
