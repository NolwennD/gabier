package eu.gabier.editing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.fixtures.FakeJavaLanguageServer;
import eu.gabier.editing.fixtures.ProjectDiagnosticsScenarios;
import eu.gabier.editing.fixtures.Scenario;
import eu.gabier.editing.fixtures.Scenario.Request;
import eu.gabier.editing.fixtures.Scenarios;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class FollowProblemsTest {

    private static final Path ROOT = Path.of("/project");

    private final FakeJavaLanguageServer server = new FakeJavaLanguageServer(ROOT);
    private final FollowProblems follow = new FollowProblems(server);

    private static Path fileOf(Scenario scenario) {
        return ROOT.resolve(scenario.request().file()).normalize();
    }

    @Test
    void startsFromTheFilesOfTheProjectThatHaveAProblem() {
        server.scripted(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT);

        var following = follow.start();

        assertThat(following.told())
                .extracting(DiagnosedFile::file)
                .containsExactly(
                        ROOT.resolve("src/main/java/demo/Broken.java"),
                        ROOT.resolve("src/main/java/demo/shop/Sloppy.java"),
                        ROOT.resolve("src/test/java/demo/BrokenTest.java"));
        assertThat(server.asked()).containsExactly(new Request.ProjectDiagnostics().resolvedIn(ROOT));
    }

    @Test
    void startsFromEachFileGivenCleanOnesIncludedInTheOrderGiven() {
        server.scripted(Scenarios.CLEAN_FILE).scripted(Scenarios.FILE_WITH_AN_ERROR);
        var clean = fileOf(Scenarios.CLEAN_FILE);
        var broken = fileOf(Scenarios.FILE_WITH_AN_ERROR);

        var following = follow.start(List.of(broken, clean));

        assertThat(following.told()).extracting(DiagnosedFile::file).containsExactly(broken, clean);
        assertThat(following.told().get(0).diagnostics()).hasSize(1);
        assertThat(following.told().get(1).diagnostics()).isEmpty();
    }

    @Test
    void followsOnlyTheFilesGivenWhenTheProjectChanges() {
        server.scripted(Scenarios.CLEAN_FILE).scripted(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT);
        var following = follow.start(List.of(fileOf(Scenarios.CLEAN_FILE)));

        assertThat(following.watch().changesIn(follow.read())).isEmpty();
    }

    @Test
    void tellsNothingWhenTheProjectIsReadAgainAsItWasWhenTheClientStarted() {
        server.scripted(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT);
        var following = follow.start();

        assertThat(following.watch().changesIn(follow.read())).isEmpty();
    }

    @Test
    void readsTheProblemsOfTheProjectAsTheyAreNow() {
        server.scripted(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT);

        assertThat(follow.read()).hasSize(3);
    }

    @Test
    void refusesToStartOnAFileOutsideTheProjectAsTheServerDoes() {
        server.scripted(Scenarios.DIAGNOSTICS_OUTSIDE_THE_PROJECT);
        var outside = List.of(fileOf(Scenarios.DIAGNOSTICS_OUTSIDE_THE_PROJECT));

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> follow.start(outside))
                .satisfies(exception ->
                        assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));
    }

    @Test
    void refusesToStartWhileTheServerImports() {
        server.scripted(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT_WHILE_THE_SERVER_IMPORTS);

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(follow::start)
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
    }

    @Test
    void refusesNoFilesAndNoServer() {
        assertThatExceptionOfType(MissingMandatoryValueException.class).isThrownBy(() -> follow.start(List.of()));
        assertThatExceptionOfType(MissingMandatoryValueException.class).isThrownBy(() -> new FollowProblems(null));
    }
}
