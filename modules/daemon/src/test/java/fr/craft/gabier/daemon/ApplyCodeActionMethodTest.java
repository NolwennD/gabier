package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.editing.application.ApplyCodeAction;
import fr.craft.gabier.editing.fixtures.CodeActionScenarios;
import fr.craft.gabier.editing.fixtures.FakeJavaLanguageServer;
import fr.craft.gabier.editing.fixtures.Scenario;
import fr.craft.gabier.editing.infrastructure.secondary.NioFileStore;
import fr.craft.gabier.protocol.ChangedFiles;
import fr.craft.gabier.protocol.RpcResponse;
import fr.craft.gabier.shared.error.domain.ErrorStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The method {@code apply-action} through the handler, against the fake language server scripted by the scenarios
 * and the real files of a temporary project: what is written, what is answered, and that a refusal writes nothing.
 */
class ApplyCodeActionMethodTest {

    private static final String IMPORT = "Import 'List' (java.util)";

    @TempDir
    Path project;

    private Path offers;

    private RequestHandler handler(Scenario... scenarios) throws IOException {
        var server = new FakeJavaLanguageServer(project);
        for (Scenario scenario : scenarios) {
            scenario.writeProject(project);
            server.scripted(scenario);
        }
        offers = project.resolve("src/main/java/demo/Offers.java");

        return new RequestHandler(List.of(new ApplyCodeActionMethod(new ApplyCodeAction(server, new NioFileStore()))));
    }

    private RequestHandler handlerOfTheImport() throws IOException {
        return handler(
                CodeActionScenarios.CODE_ACTIONS_ON_A_TYPE_THAT_IS_NOT_IMPORTED,
                CodeActionScenarios.APPLY_THE_IMPORT_OF_A_TYPE);
    }

    private static String request(Path file, String endLine, String title, String dryRun) {
        return """
            {"jsonrpc":"2.0","id":1,"method":"apply-action","params":\
            {"file":"%s","startLine":"3","startColumn":"4","endLine":"%s","endColumn":"8",\
            "title":"%s","dryRun":"%s"}}\
            """.formatted(file, endLine, title, dryRun);
    }

    private static ChangedFiles changed(RequestHandler.Reply reply) {
        return ChangedFiles.of(((RpcResponse.Success) RpcResponse.parse(reply.line())).result());
    }

    private static RpcResponse.Failure refused(RequestHandler.Reply reply) {
        return (RpcResponse.Failure) RpcResponse.parse(reply.line());
    }

    @Test
    void isNamedAndTakesTheFileTheRangeTheTitleAndTheDryRun() {
        var method =
                new ApplyCodeActionMethod(new ApplyCodeAction(new FakeJavaLanguageServer(project), new NioFileStore()));

        assertThat(method.name()).isEqualTo("apply-action");
        assertThat(method.parameters())
                .containsExactlyInAnyOrder(
                        "file", "startLine", "startColumn", "endLine", "endColumn", "title", "dryRun");
    }

    @Test
    void appliesTheActionOnDiskAndAnswersWhatWasChangedAndTheDiff() throws IOException {
        var handler = handlerOfTheImport();

        var answer = changed(handler.handle(request(offers, "3", IMPORT, "false")));

        assertThat(Files.readString(offers)).contains("import java.util.List;");
        assertThat(answer.modified()).containsExactly(offers);
        assertThat(answer.created()).isEmpty();
        assertThat(answer.renamed()).isEmpty();
        assertThat(answer.deleted()).isEmpty();
        assertThat(answer.diff()).contains("--- " + offers, "+import java.util.List;");
    }

    @Test
    void aDryRunAnswersWhatWouldChangeAndWritesNothing() throws IOException {
        var handler = handlerOfTheImport();
        var before = Files.readString(offers);

        var answer = changed(handler.handle(request(offers, "3", IMPORT, "true")));

        assertThat(answer.modified()).containsExactly(offers);
        assertThat(answer.diff()).contains("+import java.util.List;");
        assertThat(Files.readString(offers)).isEqualTo(before);
    }

    @Test
    void aTitleTheRangeDoesNotOfferIsRefusedWithItsProblemObjectAndNothingIsWritten() throws IOException {
        var handler = handlerOfTheImport();
        var before = Files.readString(offers);

        var failure = refused(handler.handle(request(offers, "3", "Nope", "false")));

        assertThat(failure.problem().key()).isEqualTo("editing-no-such-code-action");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(failure.problem().parameters())
                .containsEntry("title", "Nope")
                .containsEntry("choices", "quickfix: " + IMPORT);
        assertThat(Files.readString(offers)).isEqualTo(before);
    }

    @Test
    void anActionThatIsACommandForAnEditorIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(
                CodeActionScenarios.CODE_ACTIONS_ON_THE_NAME_OF_A_METHOD,
                CodeActionScenarios.APPLY_AN_ACTION_THAT_IS_A_COMMAND);

        var failure = refused(handler.handle("""
            {"jsonrpc":"2.0","id":1,"method":"apply-action","params":\
            {"file":"%s","startLine":"3","startColumn":"17","endLine":"3","endColumn":"22",\
            "title":"Change signature for 'names'","dryRun":"false"}}\
            """.formatted(offers)));

        assertThat(failure.problem().key()).isEqualTo("language-server-not-applicable");
        assertThat(failure.problem().parameters()).containsEntry("title", "Change signature for 'names'");
    }

    @Test
    void aBlankTitleIsRefusedNamingTheParameter() throws IOException {
        var handler = handlerOfTheImport();

        var failure = refused(handler.handle(request(offers, "3", " ", "false")));

        assertThat(failure.problem().key()).isEqualTo("invalid-parameter");
        assertThat(failure.problem().parameters()).containsEntry("parameter", "title");
    }

    @Test
    void aRequestWithoutTheTitleIsRefusedBeforeTheMethodIsCalled() throws IOException {
        var handler = handlerOfTheImport();

        var failure = refused(handler.handle("""
            {"jsonrpc":"2.0","id":1,"method":"apply-action","params":\
            {"file":"%s","startLine":"3","startColumn":"4","endLine":"3","endColumn":"8","dryRun":"false"}}\
            """.formatted(offers)));

        assertThat(failure.problem().key()).isEqualTo("missing-parameter");
        assertThat(failure.problem().parameters()).containsEntry("parameter", "title");
    }
}
