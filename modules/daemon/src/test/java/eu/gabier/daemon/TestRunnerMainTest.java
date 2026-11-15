package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.tuple;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The program of the process of the tests, run in this JVM: the JUnit Platform it uses is the one of this test's class
 * path, reached through the context loader, and the tests are the compiled project of {@link TestedProject}.
 */
class TestRunnerMainTest {

    @TempDir
    Path folder;

    private Path classes;
    private ClassLoader original;
    private URLClassLoader project;

    @BeforeEach
    void compileTheProjectAndLoadItWithTheClassPath() throws IOException {
        classes = TestedProject.compiledIn(folder);
        original = Thread.currentThread().getContextClassLoader();
        project = new URLClassLoader(new URL[] {classes.toUri().toURL()}, original);
        Thread.currentThread().setContextClassLoader(project);
    }

    @AfterEach
    void putTheLoaderBack() throws IOException {
        Thread.currentThread().setContextClassLoader(original);
        project.close();
    }

    private RunnerReport run(String... names) {
        return TestRunnerMain.run(List.of(names), List.of(classes));
    }

    @Test
    void countsWhatPassedWhatWasSkippedAndWhatFailedWithItsClassMethodAndPlace() {
        var report = run("app.MixedTest");

        assertThat(report.passed()).isEqualTo(2);
        assertThat(report.skipped()).isEqualTo(2);
        assertThat(report.failures())
                .containsExactly(new RunnerReport.Failure(
                        "app.MixedTest",
                        "fails",
                        "java.lang.IllegalStateException",
                        "exploded\tin the helper",
                        "app.Helper.explode(Helper.java:5)"));
    }

    @Test
    void tellsAClassThatFailedBeforeItsTestsCouldRun() {
        var report = run("app.BrokenSetupTest");

        assertThat(report.failures()).hasSize(1);
        assertThat(report.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.testClass()).isEqualTo("app.BrokenSetupTest");
            assertThat(failure.method()).isEmpty();
            assertThat(failure.message()).isEmpty();
        });
    }

    @Test
    void givesTheFirstFrameWhenNoneIsInTheProjectAndNoneWhenThereIsNoStack() {
        var failures = run("app.OddStacksTest").failures();

        assertThat(failures)
                .extracting(RunnerReport.Failure::method, RunnerReport.Failure::where)
                .containsExactlyInAnyOrder(
                        tuple("stackOfTheJdkOnly", "java.lang.Thread.run(Unknown Source)"), tuple("noStack", ""));
    }

    @Test
    void locatesTheFailureOfADynamicTestByTheMethodThatMadeIt() {
        var failures = run("app.DynamicFailureTest").failures();

        assertThat(failures).singleElement().satisfies(failure -> {
            assertThat(failure.testClass()).isEqualTo("app.DynamicFailureTest");
            assertThat(failure.method()).isEqualTo("tests");
        });
    }

    @Test
    void locatesTheFailureOfATestThatHasNoClassSourceByTheMethodAboveIt() {
        var failures = run("app.DynamicUriTest").failures();

        assertThat(failures).singleElement().satisfies(failure -> {
            assertThat(failure.testClass()).isEqualTo("app.DynamicUriTest");
            assertThat(failure.method()).isEqualTo("tests");
        });
    }

    @Test
    void countsAsSkippedEveryTestOfAClassThatIsDisabled() {
        var report = run("app.DisabledClassTest");

        assertThat(report.skipped()).isEqualTo(2);
        assertThat(report.passed()).isZero();
        assertThat(report.failures()).isEmpty();
    }

    @Test
    void refusesToRunAnythingWhenAClassIsNoTestForAnyEngine() {
        var report = run("app.MixedTest", "app.NotATest");

        assertThat(report.unrunnable()).containsExactly("app.NotATest");
        assertThat(report.passed()).isZero();
    }

    @Test
    void saysSoWhenTheClassPathHasNoLauncher() {
        Thread.currentThread()
                .setContextClassLoader(new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader()));

        assertThat(run("app.MixedTest").launcherMissing()).isTrue();
    }

    @Test
    void saysEveryClassIsUnrunnableWhenTheClassPathHasNoEngine() {
        Thread.currentThread().setContextClassLoader(new Hiding(project, "org.junit.jupiter.engine", true));

        assertThat(run("app.MixedTest").unrunnable()).containsExactly("app.MixedTest");
    }

    @Test
    void writesTheReportToTheFileAndEndsWithZero() throws IOException {
        Path file = folder.resolve("report");

        int code =
                TestRunnerMain.execute(new String[] {file.toString(), File.pathSeparator + classes, "app.MixedTest"});

        assertThat(code).isZero();
        assertThat(RunnerReport.parse(Files.readAllLines(file)).failures().size())
                .isEqualTo(1);
    }

    @Test
    void failsWhenTheLauncherOfTheClassPathIsBroken() {
        Thread.currentThread()
                .setContextClassLoader(
                        new Hiding(project, "org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder", false));

        assertThatIllegalStateException().isThrownBy(() -> run("app.MixedTest"));
    }

    /**
     * A loader that does not see the classes that start with a name, and, when told, finds no test engine to load by
     * name: the class path of a project with a broken launcher, or with no engine.
     */
    private static final class Hiding extends ClassLoader {

        private final String hidden;
        private final boolean noEngineServices;

        private Hiding(ClassLoader parent, String hidden, boolean noEngineServices) {
            super(parent);
            this.hidden = hidden;
            this.noEngineServices = noEngineServices;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(hidden)) {
                throw new ClassNotFoundException(name);
            }

            return super.loadClass(name, resolve);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return noEngineServices && name.startsWith("META-INF/services/org.junit.platform.engine.TestEngine")
                    ? Collections.emptyEnumeration()
                    : super.getResources(name);
        }
    }
}
