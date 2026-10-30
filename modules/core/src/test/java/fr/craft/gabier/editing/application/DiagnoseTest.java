package fr.craft.gabier.editing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.craft.gabier.editing.domain.Diagnostic;
import fr.craft.gabier.editing.domain.LanguageServerErrorKey;
import fr.craft.gabier.editing.domain.LanguageServerException;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.editing.fixtures.FakeJavaLanguageServer;
import fr.craft.gabier.editing.fixtures.Scenario;
import fr.craft.gabier.editing.fixtures.Scenario.Request;
import fr.craft.gabier.editing.fixtures.Scenarios;
import fr.craft.gabier.shared.error.domain.MissingMandatoryValueException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiagnoseTest {

    private static final Path ROOT = Path.of("/project");

    private final FakeJavaLanguageServer server = new FakeJavaLanguageServer(ROOT);
    private final Diagnose diagnose = new Diagnose(server);

    private static Path fileOf(Scenario scenario) {
        return ROOT.resolve(scenario.request().file()).normalize();
    }

    @Test
    void givesNothingForACleanFile() {
        server.scripted(Scenarios.CLEAN_FILE);

        assertThat(diagnose.diagnose(fileOf(Scenarios.CLEAN_FILE))).isEmpty();
    }

    @Test
    void givesTheDiagnosticsOfTheServerAsTheyAre() {
        server.scripted(Scenarios.FILE_WITH_AN_ERROR);

        List<Diagnostic> diagnostics = diagnose.diagnose(fileOf(Scenarios.FILE_WITH_AN_ERROR));

        assertThat(diagnostics)
                .containsExactly(new Diagnostic(
                        Diagnostic.Severity.ERROR,
                        new Position(4, 15),
                        "Type mismatch: cannot convert from String to int"));
    }

    @Test
    void asksTheServerOnceAboutTheFile() {
        server.scripted(Scenarios.CLEAN_FILE);

        diagnose.diagnose(fileOf(Scenarios.CLEAN_FILE));

        assertThat(server.asked()).containsExactly(new Request.Diagnostics(fileOf(Scenarios.CLEAN_FILE)));
    }

    @Test
    void reportsAServerThatIsNotReadyWithItsMessage() {
        server.scripted(Scenarios.DIAGNOSTICS_WHILE_THE_SERVER_IMPORTS);

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> diagnose.diagnose(fileOf(Scenarios.DIAGNOSTICS_WHILE_THE_SERVER_IMPORTS)))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NOT_READY))
                .withMessage("The language server is still importing the project: try again later");
    }

    @Test
    void reportsAFileOutsideTheProjectWithItsMessage() {
        server.scripted(Scenarios.DIAGNOSTICS_OUTSIDE_THE_PROJECT);
        Path outside = fileOf(Scenarios.DIAGNOSTICS_OUTSIDE_THE_PROJECT);

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> diagnose.diagnose(outside))
                .satisfies(
                        exception -> assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT))
                .withMessage("The file /outside/Elsewhere.java is not in the project of the language server");
    }

    @Test
    void rejectsAMissingServer() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Diagnose(null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("server"));
    }

    @Test
    void rejectsAMissingFile() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> diagnose.diagnose(null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("file"));
    }
}
