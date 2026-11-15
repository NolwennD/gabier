package eu.gabier.testing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.shared.error.domain.AssertionException;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import eu.gabier.testing.domain.BuiltProject;
import eu.gabier.testing.domain.TestReport;
import eu.gabier.testing.domain.TestingErrorKey;
import eu.gabier.testing.domain.TestingException;
import eu.gabier.testing.fixtures.BuildScenario;
import eu.gabier.testing.fixtures.CompiledProject;
import eu.gabier.testing.fixtures.FakeProjectBuild;
import eu.gabier.testing.fixtures.FakeTestRunner;
import eu.gabier.testing.fixtures.TestRunScenarios;
import eu.gabier.testing.infrastructure.secondary.NioProjectFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunTestsTest {

    private static final Duration LIMIT = Duration.ofSeconds(30);
    private static final Path GREETER = Path.of("src/main/java/app/Greeter.java");

    @TempDir
    Path root;

    private final FakeTestRunner runner =
            new FakeTestRunner().reporting(TestRunScenarios.ONE_PASSING_AND_ONE_FAILING_TEST);

    private FakeProjectBuild build;

    private BuiltProject project() throws IOException {
        return CompiledProject.of(
                root,
                Map.of("app/Greeter.java", "package app; public class Greeter {}"),
                Map.of("app/GreeterTest.java", """
                    package app;

                    public class GreeterTest {
                        @org.junit.Test
                        public void greets() {
                            new Greeter();
                        }

                        static class Inner {}
                    }
                    """));
    }

    private RunTests runTests(BuiltProject built) {
        build = new FakeProjectBuild(root).scripted(new BuildScenario("compiled-by-javac", Map.of(), built));

        return new RunTests(build, new NioProjectFiles(), root, runner);
    }

    @Test
    void runsTheGivenClassesOnTheClassPathOfTheBuildAndGivesTheReport() throws IOException {
        var built = project();
        var runTests = runTests(built);

        var report = runTests.run(List.of("app.GreeterTest"), LIMIT);

        assertThat(report).isEqualTo(TestRunScenarios.ONE_PASSING_AND_ONE_FAILING_TEST.report());
        assertThat(runner.runs()).singleElement().satisfies(run -> {
            assertThat(run.classes()).containsExactly("app.GreeterTest");
            assertThat(run.classpath())
                    .containsExactly(
                            root.resolve("bin"), root.resolve("test-bin"), root.resolve("lib/test-frameworks.jar"));
            assertThat(run.workingDirectory()).isEqualTo(root);
            assertThat(run.timeLimit()).isEqualTo(LIMIT);
        });
    }

    @Test
    void runsAClassGivenTwiceOnce() throws IOException {
        var runTests = runTests(project());

        runTests.run(List.of("app.GreeterTest", "app.GreeterTest"), LIMIT);

        assertThat(runner.runs())
                .singleElement()
                .satisfies(run -> assertThat(run.classes()).containsExactly("app.GreeterTest"));
    }

    @Test
    void refusesAClassThatTheBuildDoesNotHold() throws IOException {
        var runTests = runTests(project());

        assertThatExceptionOfType(TestingException.class)
                .isThrownBy(() -> runTests.run(List.of("app.GreeterTest", "app.Gone"), LIMIT))
                .satisfies(exception -> {
                    assertThat(exception.key()).isEqualTo(TestingErrorKey.NO_SUCH_TEST_CLASS);
                    assertThat(exception.parameters()).containsEntry("class", "app.Gone");
                });
        assertThat(runner.runs()).isEmpty();
    }

    @Test
    void refusesToRunWhenTheFileOfATestClassDoesNotCompileWhateverItsNestedClassIsCalled() throws IOException {
        BuiltProject compiling = project();
        Path test = root.resolve("src/test/java/app/GreeterTest.java");
        var runTests = runTests(new BuiltProject(compiling.folders(), compiling.libraries(), List.of(test)));

        assertThatExceptionOfType(TestingException.class)
                .isThrownBy(() -> runTests.run(List.of("app.GreeterTest$Inner"), LIMIT))
                .satisfies(exception -> {
                    assertThat(exception.key()).isEqualTo(TestingErrorKey.COMPILATION_FAILED);
                    assertThat(exception.parameters().get("files")).isEqualTo(test.toString());
                });
        assertThat(runner.runs()).isEmpty();
    }

    @Test
    void runsWhenAFileThatIsNoTestDoesNotCompileBecauseTheReportTellsWhatTheStubDid() throws IOException {
        BuiltProject compiling = project();
        var runTests =
                runTests(new BuiltProject(compiling.folders(), compiling.libraries(), List.of(root.resolve(GREETER))));

        var report = runTests.run(List.of("app.GreeterTest"), LIMIT);

        assertThat(report.failed()).isEqualTo(1);
        assertThat(runner.runs()).hasSize(1);
    }

    @Test
    void relaysTheRefusalOfTheRunner() throws IOException {
        runner.refusing(TestingException.timedOut(LIMIT));
        var runTests = runTests(project());

        assertThatExceptionOfType(TestingException.class)
                .isThrownBy(() -> runTests.run(List.of("app.GreeterTest"), LIMIT))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(TestingErrorKey.TEST_TIMEOUT));
    }

    @Test
    void runsTheTestsThatWhatChangedOnDiskAffectsAndForgetsTheChanges() throws IOException {
        var runTests = runTests(project());
        build.edited(GREETER);

        var report = runTests.runAffectedByChanges(LIMIT);

        assertThat(report.failed()).isEqualTo(1);
        assertThat(runner.runs())
                .singleElement()
                .satisfies(run -> assertThat(run.classes()).containsExactly("app.GreeterTest"));
        assertThat(runTests.runAffectedByChanges(LIMIT)).isEqualTo(TestReport.nothingRan());
        assertThat(runner.runs()).hasSize(1);
    }

    @Test
    void startsNoProcessWhenNoTestIsAffected() throws IOException {
        var runTests = runTests(project());

        assertThat(runTests.runAffectedByChanges(LIMIT)).isEqualTo(TestReport.nothingRan());
        assertThat(runner.runs()).isEmpty();
    }

    @Test
    void keepsTheChangesWhenTheRunIsRefused() throws IOException {
        var runTests = runTests(project());
        build.edited(GREETER);
        runner.refusing(TestingException.timedOut(LIMIT));

        assertThatExceptionOfType(TestingException.class).isThrownBy(() -> runTests.runAffectedByChanges(LIMIT));
        runner.reporting(TestRunScenarios.ONE_PASSING_AND_ONE_FAILING_TEST);

        assertThat(runTests.runAffectedByChanges(LIMIT).failed()).isEqualTo(1);
    }

    @Test
    void keepsTheChangesWhenAFileDoesNotCompileAndNamesTheFile() throws IOException {
        BuiltProject compiling = project();
        var runTests =
                runTests(new BuiltProject(compiling.folders(), compiling.libraries(), List.of(root.resolve(GREETER))));
        build.edited(GREETER);

        assertThatExceptionOfType(TestingException.class)
                .isThrownBy(() -> runTests.runAffectedByChanges(LIMIT))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(TestingErrorKey.COMPILATION_FAILED));
        assertThat(runner.runs()).isEmpty();
    }

    @Test
    void refusesToRunTheTestsOfWhatChangedWhenTheFileOfOneOfThemDoesNotCompile() throws IOException {
        BuiltProject compiling = project();
        Path test = root.resolve("src/test/java/app/GreeterTest.java");
        var runTests = runTests(new BuiltProject(compiling.folders(), compiling.libraries(), List.of(test)));
        build.edited(GREETER);

        assertThatExceptionOfType(TestingException.class)
                .isThrownBy(() -> runTests.runAffectedByChanges(LIMIT))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(TestingErrorKey.COMPILATION_FAILED));
        assertThat(runner.runs()).isEmpty();
    }

    @Test
    void rejectsWhatIsMissingOrEmpty() throws IOException {
        var runTests = runTests(project());

        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new RunTests(null, new NioProjectFiles(), root, runner));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new RunTests(build, null, root, runner));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new RunTests(build, new NioProjectFiles(), null, runner));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new RunTests(build, new NioProjectFiles(), root, null));
        assertThatExceptionOfType(AssertionException.class).isThrownBy(() -> runTests.run(List.of(), LIMIT));
    }
}
