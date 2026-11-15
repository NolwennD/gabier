package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.gabier.shared.error.domain.GeneratorException;
import eu.gabier.testing.application.RunTests;
import eu.gabier.testing.fixtures.TestRunScenario;
import eu.gabier.testing.fixtures.TestRunScenarios;
import eu.gabier.testing.infrastructure.secondary.NioProjectFiles;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Replays the contract scenarios of the run of tests against a real jdtls and the real runner: jdtls builds the small
 * project of the scenario with the JUnit jars of this test on its class path, the classes it wrote are run in a JVM of
 * their own, and the effect is what the scenario says (the counts and the failed tests, or the key of the refusal). Not
 * run by {@code check}: {@code ./gradlew jdtlsProbe}, with {@code GABIER_JDTLS} set to the jdtls launcher.
 */
class TestRunReplayTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    private static JdtlsLanguageServer started(Path root) throws Exception {
        var settings = JdtlsSettings.of(root, List.of(root), TIMEOUT);
        var server = JdtlsLanguageServer.start(RealJdtls.launcher(), settings, JdtlsLanguageServer.QUIET, line -> {});
        server.awaitStarted();

        return server;
    }

    /** The jars that hold the JUnit Platform, the Jupiter engine and its API: what the project depends on. */
    private static List<Path> junit() {
        return Stream.of(
                        "org.junit.platform.launcher.core.LauncherFactory",
                        "org.junit.jupiter.engine.JupiterTestEngine",
                        "org.junit.jupiter.api.Test",
                        "org.junit.platform.engine.TestEngine",
                        "org.junit.platform.commons.JUnitException",
                        "org.opentest4j.AssertionFailedError")
                .map(TestRunReplayTest::jarOf)
                .toList();
    }

    private static Path jarOf(String className) {
        try {
            return Path.of(URI.create(Class.forName(className)
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toString()));
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException(missing);
        }
    }

    private static Path project(TestRunScenario scenario) throws IOException {
        Path root = Files.createTempDirectory("gabier-test-run-").toRealPath();
        scenario.writeProject(root);
        EclipseProject.describe(root, junit());

        return root;
    }

    @Test
    void givesTheReportOfTheScenarioForTheClassesJdtlsWrote() throws Exception {
        var scenario = TestRunScenarios.ONE_PASSING_AND_ONE_FAILING_TEST;
        Path root = project(scenario);

        try (var server = started(root)) {
            var runTests = new RunTests(server, new NioProjectFiles(), root, ProcessTestRunner.ofThisDaemon());

            var report = runTests.run(scenario.classes(), scenario.timeLimit());

            assertThat(report).isEqualTo(scenario.report());
        }
    }

    @Test
    void refusesEveryScenarioThatIsRefusedWithTheKeyItSays() throws Exception {
        for (TestRunScenario scenario : TestRunScenarios.all()) {
            if (scenario.refusal().isEmpty()) {
                continue;
            }
            Path root = project(scenario);

            try (var server = started(root)) {
                var runTests = new RunTests(server, new NioProjectFiles(), root, ProcessTestRunner.ofThisDaemon());

                assertThatThrownBy(() -> runTests.run(scenario.classes(), scenario.timeLimit()))
                        .as(scenario.name())
                        .isInstanceOfSatisfying(
                                GeneratorException.class,
                                refused -> assertThat(refused.key().get())
                                        .isEqualTo(
                                                scenario.refusal().orElseThrow().get()));
            }
        }
    }
}
