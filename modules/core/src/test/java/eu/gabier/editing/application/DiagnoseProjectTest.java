package eu.gabier.editing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.fixtures.FakeJavaLanguageServer;
import eu.gabier.editing.fixtures.ProjectDiagnosticsScenarios;
import eu.gabier.editing.fixtures.Scenario.Request;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiagnoseProjectTest {

    private static final Path ROOT = Path.of("/project");

    private final FakeJavaLanguageServer server = new FakeJavaLanguageServer(ROOT);
    private final DiagnoseProject diagnose = new DiagnoseProject(server);

    @Test
    void givesEveryFileOfTheProjectThatHasAProblemTheTestsIncludedAndNotTheCleanOne() {
        server.scripted(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT);

        List<DiagnosedFile> files = diagnose.diagnose();

        assertThat(files)
                .extracting(DiagnosedFile::file)
                .containsExactly(
                        ROOT.resolve("src/main/java/demo/Broken.java"),
                        ROOT.resolve("src/main/java/demo/shop/Sloppy.java"),
                        ROOT.resolve("src/test/java/demo/BrokenTest.java"));
        assertThat(files.get(0).diagnostics())
                .extracting(Diagnostic::severity, Diagnostic::position)
                .containsExactly(tuple(Diagnostic.Severity.ERROR, new Position(4, 15)));
        assertThat(files.get(1).diagnostics())
                .extracting(Diagnostic::severity)
                .containsExactly(Diagnostic.Severity.WARNING);
        assertThat(files.get(2).diagnostics())
                .extracting(Diagnostic::severity)
                .containsExactly(Diagnostic.Severity.ERROR);
    }

    @Test
    void asksTheServerOnce() {
        server.scripted(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT);

        diagnose.diagnose();

        assertThat(server.asked()).containsExactly(new Request.ProjectDiagnostics());
    }

    @Test
    void reportsAServerThatIsNotReadyWithItsMessage() {
        server.scripted(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT_WHILE_THE_SERVER_IMPORTS);

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(diagnose::diagnose)
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NOT_READY))
                .withMessage("The language server is still importing the project: try again later");
    }

    @Test
    void rejectsAMissingServer() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new DiagnoseProject(null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("server"));
    }
}
