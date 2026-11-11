package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.editing.application.DiagnoseProject;
import eu.gabier.editing.fixtures.FakeJavaLanguageServer;
import eu.gabier.editing.fixtures.ProjectDiagnosticsScenarios;
import eu.gabier.editing.fixtures.Scenario;
import eu.gabier.protocol.DiagnosedFiles;
import eu.gabier.protocol.FileDiagnostics;
import eu.gabier.protocol.FileDiagnostics.Diagnosis;
import eu.gabier.protocol.FileDiagnostics.Severity;
import eu.gabier.protocol.RpcResponse;
import eu.gabier.shared.error.domain.ErrorStatus;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The method {@code diagnostics-all} through the handler, against the fake language server scripted by a scenario. */
class DiagnosticsAllMethodTest {

    private static final String REQUEST = """
        {"jsonrpc":"2.0","id":1,"method":"diagnostics-all"}\
        """;

    @TempDir
    Path project;

    private RequestHandler handler(Scenario scenario) {
        var server = new FakeJavaLanguageServer(project).scripted(scenario);

        return new RequestHandler(List.of(new DiagnosticsAllMethod(new DiagnoseProject(server))));
    }

    private DiagnosedFiles diagnosedBy(String line) {
        return DiagnosedFiles.of(((RpcResponse.Success) RpcResponse.parse(line)).result());
    }

    @Test
    void isNamedDiagnosticsAllAndTakesNoParameter() {
        var method = new DiagnosticsAllMethod(new DiagnoseProject(new FakeJavaLanguageServer(project)));

        assertThat(method.name()).isEqualTo("diagnostics-all");
        assertThat(method.parameters()).isEmpty();
    }

    @Test
    void answersEveryFileOfTheProjectByItsAbsolutePathWithItsProblemsFromZero() {
        var line = handler(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT)
                .handle(REQUEST)
                .line();

        var diagnosed = diagnosedBy(line);

        assertThat(diagnosed.files())
                .extracting(FileDiagnostics::file)
                .containsExactly(
                        project.resolve("src/main/java/demo/Broken.java").toString(),
                        project.resolve("src/main/java/demo/shop/Sloppy.java").toString(),
                        project.resolve("src/test/java/demo/BrokenTest.java").toString());
        assertThat(diagnosed.files().get(0).diagnostics()).containsExactly(new Diagnosis(Severity.ERROR, 4, 15, ""));
        assertThat(diagnosed.files().get(1).diagnostics()).containsExactly(new Diagnosis(Severity.WARNING, 4, 12, ""));
        assertThat(diagnosed.files().get(2).diagnostics()).containsExactly(new Diagnosis(Severity.ERROR, 4, 15, ""));
    }

    @Test
    void doesNothingAfterTheReply() {
        handler(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT)
                .handle(REQUEST)
                .afterReply()
                .run();
    }

    @Test
    void aServerThatIsNotReadyIsRefusedWithItsProblemObject() {
        var line = handler(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT_WHILE_THE_SERVER_IMPORTS)
                .handle(REQUEST)
                .line();

        var failure = (RpcResponse.Failure) RpcResponse.parse(line);
        assertThat(failure.problem().key()).isEqualTo("language-server-not-ready");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
    }

    @Test
    void aParameterIsRefusedBeforeTheMethodIsCalled() {
        var line = handler(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT)
                .handle("""
                    {"jsonrpc":"2.0","id":1,"method":"diagnostics-all","params":{"files":"a"}}\
                    """)
                .line();

        var failure = (RpcResponse.Failure) RpcResponse.parse(line);
        assertThat(failure.problem().key()).isEqualTo("unknown-parameter");
        assertThat(failure.problem().parameters()).containsEntry("parameter", "files");
    }
}
