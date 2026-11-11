package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.testing.application.AffectedTests;
import eu.gabier.testing.fixtures.AffectedTestsCase;
import eu.gabier.testing.fixtures.AffectedTestsCases;
import eu.gabier.testing.fixtures.BuildScenario;
import eu.gabier.testing.fixtures.BuildScenarios;
import eu.gabier.testing.fixtures.CompiledProject;
import eu.gabier.testing.infrastructure.secondary.NioProjectFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Replays the contract scenarios of the selection of tests against a real jdtls: the build it reports for a small
 * project is the one the scenario says ({@link BuildScenarios}), and the tests selected from the classes it wrote are
 * those of every case ({@link AffectedTestsCases}), the one that edits a file on disk included. Not run by {@code check}:
 * {@code ./gradlew jdtlsProbe}, with {@code GABIER_JDTLS} set to the jdtls launcher.
 */
class AffectedTestsReplayTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    private static JdtlsLanguageServer started(Path root) throws Exception {
        var settings = JdtlsSettings.of(root, List.of(root), TIMEOUT);
        var server = JdtlsLanguageServer.start(RealJdtls.launcher(), settings, JdtlsLanguageServer.QUIET, line -> {});
        server.awaitStarted();

        return server;
    }

    private static void write(Path root, Path folder, Map<String, String> sources) throws IOException {
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = root.resolve(folder).resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
        }
    }

    @Test
    void reportsTheBuildEveryScenarioSays() throws Exception {
        for (BuildScenario scenario : BuildScenarios.all()) {
            Path root = Files.createTempDirectory("gabier-build-").toRealPath();
            scenario.writeProject(root);
            EclipseProject.describe(root);

            try (var server = started(root)) {
                var expected = scenario.inside(root).built();

                var built = server.built();

                assertThat(built).as(scenario.name()).isEqualTo(expected);
                for (var folder : expected.folders()) {
                    assertThat(folder.classes()).as(scenario.name()).isDirectory();
                }
            }
        }
    }

    @Test
    void selectsTheTestsOfEveryCaseFromTheClassesJdtlsWrote() throws Exception {
        Path root = Files.createTempDirectory("gabier-affected-").toRealPath();
        write(root, Path.of("src/main/java"), AffectedTestsCases.mainSources());
        write(root, Path.of("src/test/java"), AffectedTestsCases.testSources());
        for (Map.Entry<Path, String> other : AffectedTestsCases.otherFiles().entrySet()) {
            Files.writeString(root.resolve(other.getKey()), other.getValue());
        }
        EclipseProject.describe(root, List.of(CompiledProject.frameworks(root)));

        try (var server = started(root)) {
            var affectedTests = new AffectedTests(server, new NioProjectFiles(), root);
            for (AffectedTestsCase scenario : AffectedTestsCases.all()) {
                for (Map.Entry<Path, String> edit : scenario.edits().entrySet()) {
                    Files.writeString(root.resolve(edit.getKey()), edit.getValue());
                }

                var affected = affectedTests.affectedBy(
                        scenario.changed().stream().map(root::resolve).toList());

                assertThat(affected).as(scenario.name()).containsExactlyElementsOf(scenario.affected());
            }
        }
    }
}
