package eu.gabier.testing.fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.testing.domain.TestRun;
import eu.gabier.testing.domain.TestingErrorKey;
import eu.gabier.testing.domain.TestingException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestRunScenariosTest {

    @TempDir
    Path root;

    private static TestRun run() {
        return new TestRun(List.of("app.CounterTest"), List.of(), Path.of("/project"), Duration.ofSeconds(1));
    }

    @Test
    void writesTheFilesOfTheProjectOfEveryScenario() throws IOException {
        for (TestRunScenario scenario : TestRunScenarios.all()) {
            scenario.writeProject(root);

            for (Path file : scenario.files().keySet()) {
                assertThat(root.resolve(file)).as(scenario.name()).isRegularFile();
            }
        }
        assertThat(Files.readString(root.resolve("src/main/java/app/Counter.java")))
                .contains("not started");
    }

    @Test
    void saysWhatEveryScenarioExpectsAReportOrARefusal() {
        for (TestRunScenario scenario : TestRunScenarios.all()) {
            assertThat(scenario.expected().isPresent() != scenario.refusal().isPresent())
                    .as(scenario.name())
                    .isTrue();
        }
        assertThat(TestRunScenarios.ONE_PASSING_AND_ONE_FAILING_TEST.report().failed())
                .isEqualTo(1);
        assertThatExceptionOfType(AssertionError.class).isThrownBy(TestRunScenarios.A_TEST_THAT_NEVER_ENDS::report);
    }

    @Test
    void scriptsTheFakeRunnerWithTheReportOfAScenario() {
        var runner = new FakeTestRunner().reporting(TestRunScenarios.ONE_PASSING_AND_ONE_FAILING_TEST);

        assertThat(runner.run(run())).isEqualTo(TestRunScenarios.ONE_PASSING_AND_ONE_FAILING_TEST.report());
        assertThat(runner.runs()).hasSize(1);
    }

    @Test
    void failsWhenTheFakeRunnerWasNotScripted() {
        assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> new FakeTestRunner().run(run()));
    }

    @Test
    void refusesWithTheRefusalItWasScriptedWith() {
        var runner = new FakeTestRunner().refusing(TestingException.timedOut(Duration.ofSeconds(1)));

        assertThatExceptionOfType(TestingException.class)
                .isThrownBy(() -> runner.run(run()))
                .satisfies(refusal -> assertThat(refusal.key()).isEqualTo(TestingErrorKey.TEST_TIMEOUT));
    }
}
