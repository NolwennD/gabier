package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.editing.application.Diagnose;
import eu.gabier.editing.fixtures.FakeJavaLanguageServer;
import eu.gabier.editing.fixtures.Scenario;
import eu.gabier.editing.fixtures.Scenarios;
import eu.gabier.protocol.FileDiagnostics;
import eu.gabier.protocol.FileDiagnostics.Diagnosis;
import eu.gabier.protocol.FileDiagnostics.Severity;
import eu.gabier.protocol.RpcResponse;
import eu.gabier.shared.error.domain.ErrorStatus;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The method {@code diagnostics} through the handler, against the fake language server scripted by the scenarios. */
class DiagnosticsMethodTest {

    @TempDir
    Path project;

    private RequestHandler handler(Scenario... scenarios) {
        var server = new FakeJavaLanguageServer(project);
        for (var scenario : scenarios) {
            server.scripted(scenario);
        }

        return new RequestHandler(List.of(new DiagnosticsMethod(new Diagnose(server))));
    }

    private String ask(RequestHandler handler, Path file) {
        return handler.handle("""
            {"jsonrpc":"2.0","id":1,"method":"diagnostics","params":{"file":"%s"}}\
            """.formatted(file)).line();
    }

    private FileDiagnostics diagnosticsOf(Scenario scenario) {
        var line = ask(handler(scenario), project.resolve(scenario.request().file()));

        return FileDiagnostics.of(((RpcResponse.Success) RpcResponse.parse(line)).result());
    }

    @Test
    void isNamedDiagnosticsAndTakesTheFile() {
        var method = new DiagnosticsMethod(new Diagnose(new FakeJavaLanguageServer(project)));

        assertThat(method.name()).isEqualTo("diagnostics");
        assertThat(method.parameters()).containsExactly("file");
    }

    @Test
    void aFileWithAnErrorGetsItsErrorWithTheZeroBasedPosition() {
        assertThat(diagnosticsOf(Scenarios.FILE_WITH_AN_ERROR).diagnostics())
                .containsExactly(
                        new Diagnosis(Severity.ERROR, 4, 15, "Type mismatch: cannot convert from String to int"));
    }

    @Test
    void aFileWithAWarningGetsItsWarning() {
        assertThat(diagnosticsOf(Scenarios.FILE_WITH_A_WARNING).diagnostics())
                .containsExactly(
                        new Diagnosis(Severity.WARNING, 4, 12, "The value of the local variable unused is not used"));
    }

    @Test
    void aCleanFileGetsAnEmptyList() {
        var line = ask(
                handler(Scenarios.CLEAN_FILE),
                project.resolve(Scenarios.CLEAN_FILE.request().file()));

        assertThat(line).isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"diagnostics\":[]}}");
    }

    @Test
    void doesNothingAfterTheReply() {
        var handler = handler(Scenarios.CLEAN_FILE);

        handler.handle("""
            {"jsonrpc":"2.0","id":1,"method":"diagnostics","params":{"file":"%s"}}\
            """.formatted(
                                project.resolve(Scenarios.CLEAN_FILE.request().file())))
                .afterReply()
                .run();
    }

    @Test
    void aFileOutsideTheProjectIsRefusedWithItsProblemObject() {
        var outside = project.resolve(
                Scenarios.DIAGNOSTICS_OUTSIDE_THE_PROJECT.request().file());

        var line = ask(handler(Scenarios.DIAGNOSTICS_OUTSIDE_THE_PROJECT), outside);

        var failure = (RpcResponse.Failure) RpcResponse.parse(line);
        assertThat(failure.problem().key()).isEqualTo("language-server-file-outside-project");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(failure.problem().parameters())
                .containsEntry("file", outside.normalize().toString());
    }

    @Test
    void aServerThatIsNotReadyIsRefusedWithItsProblemObject() {
        var line = ask(
                handler(Scenarios.DIAGNOSTICS_WHILE_THE_SERVER_IMPORTS),
                project.resolve(
                        Scenarios.DIAGNOSTICS_WHILE_THE_SERVER_IMPORTS.request().file()));

        var failure = (RpcResponse.Failure) RpcResponse.parse(line);
        assertThat(failure.problem().key()).isEqualTo("language-server-not-ready");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
    }

    @Test
    void aRequestWithoutTheFileIsRefusedBeforeTheMethodIsCalled() {
        var line = handler(Scenarios.CLEAN_FILE)
                .handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"diagnostics\"}")
                .line();

        var failure = (RpcResponse.Failure) RpcResponse.parse(line);
        assertThat(failure.problem().key()).isEqualTo("missing-parameter");
        assertThat(failure.problem().parameters()).containsEntry("parameter", "file");
    }

    @Test
    void aFileThatIsNotAPathIsRefusedAsABadRequest() {
        var line = handler(Scenarios.CLEAN_FILE).handle("""
            {"jsonrpc":"2.0","id":1,"method":"diagnostics","params":{"file":"a\\u0000b"}}\
            """).line();

        var failure = (RpcResponse.Failure) RpcResponse.parse(line);
        assertThat(line).contains("\"code\":-32602");
        assertThat(failure.problem().key()).isEqualTo("invalid-parameter");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(failure.problem().parameters()).containsEntry("parameter", "file");
    }
}
