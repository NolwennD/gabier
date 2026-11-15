package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.testing.domain.TestRun;
import eu.gabier.testing.domain.TestingErrorKey;
import eu.gabier.testing.domain.TestingException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The process of the tests, really started: a JVM of its own on the compiled project of {@link TestedProject}. */
class ProcessTestRunnerTest {

    private static final Duration LIMIT = Duration.ofSeconds(60);

    @TempDir
    Path folder;

    private Path classes;
    private ProcessTestRunner runner;

    @BeforeEach
    void compileTheProject() throws IOException {
        classes = TestedProject.compiledIn(folder);
        runner = ProcessTestRunner.ofThisDaemon();
    }

    private TestRun run(Duration limit, List<Path> libraries, String... names) {
        List<Path> classpath = new ArrayList<>(List.of(classes));
        classpath.addAll(libraries);

        return new TestRun(List.of(names), classpath, folder, limit);
    }

    private TestRun run(String... names) {
        return run(LIMIT, TestedProject.junit(), names);
    }

    private static TestingErrorKey refusedWith(Runnable run) {
        var refusal = new AtomicReference<TestingErrorKey>();
        assertThatExceptionOfType(TestingException.class)
                .isThrownBy(run::run)
                .satisfies(exception -> refusal.set((TestingErrorKey) exception.key()));

        return refusal.get();
    }

    @Test
    void runsTheTestsInAJvmOfTheirOwnAndGivesWhatPassedWhatWasSkippedAndWhatFailed() {
        var report = runner.run(run("app.MixedTest"));

        assertThat(report.passed()).isEqualTo(2);
        assertThat(report.skipped()).isEqualTo(2);
        assertThat(report.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.testClass()).isEqualTo("app.MixedTest");
            assertThat(failure.method()).contains("fails");
            assertThat(failure.message()).isEqualTo("exploded\tin the helper");
            assertThat(failure.where()).contains("app.Helper.explode(Helper.java:5)");
        });
    }

    @Test
    void givesAClassThatFailedBeforeItsTestsWithNoMethod() {
        var report = runner.run(run("app.BrokenSetupTest"));

        assertThat(report.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.testClass()).isEqualTo("app.BrokenSetupTest");
            assertThat(failure.method()).isEmpty();
        });
    }

    @Test
    void refusesAClassNoEngineRecognisesAndRunsNothing() {
        assertThat(refusedWith(() -> runner.run(run("app.MixedTest", "app.NotATest"))))
                .isEqualTo(TestingErrorKey.NO_TEST_ENGINE);
    }

    @Test
    void refusesWhenTheClassPathHasNoLauncher() {
        var withoutLauncher = TestedProject.junit().stream()
                .filter(jar -> !jar.getFileName().toString().contains("launcher"))
                .toList();

        assertThat(refusedWith(() -> runner.run(run(LIMIT, withoutLauncher, "app.MixedTest"))))
                .isEqualTo(TestingErrorKey.NO_TEST_LAUNCHER);
    }

    @Test
    void stopsATestThatNeverEndsAfterTheTimeLimit() {
        long start = System.nanoTime();

        var key = refusedWith(() -> runner.run(run(Duration.ofSeconds(3), TestedProject.junit(), "app.SleepTest")));

        assertThat(key).isEqualTo(TestingErrorKey.TEST_TIMEOUT);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(30));
    }

    @Test
    void saysWhatTheJvmWroteWhenItEndsWithNoReport() {
        assertThatExceptionOfType(TestingException.class)
                .isThrownBy(() -> runner.run(run("app.ExitTest")))
                .satisfies(exception -> {
                    assertThat(exception.key()).isEqualTo(TestingErrorKey.RUN_FAILED);
                    assertThat(exception.getMessage()).contains("goodbye from the test");
                });
    }

    @Test
    void refusesWhenTheJavaCannotBeStarted() {
        var broken = new ProcessTestRunner(folder.resolve("no-java"), classes);

        assertThat(refusedWith(() -> broken.run(run("app.MixedTest")))).isEqualTo(TestingErrorKey.RUN_FAILED);
    }

    @Test
    void killsTheJvmAndEndsWhenTheCallerIsInterrupted() throws InterruptedException {
        var refusal = new AtomicReference<RuntimeException>();
        var caller = new Thread(() -> {
            try {
                runner.run(run("app.SleepTest"));
            } catch (RuntimeException failure) {
                refusal.set(failure);
            }
        });
        caller.start();
        Thread.sleep(1500);

        caller.interrupt();
        caller.join(Duration.ofSeconds(30));

        assertThat(refusal.get()).isInstanceOf(TestingException.class).hasMessageContaining("interrupted");
    }
}
