package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.editing.application.ListCodeActions;
import fr.craft.gabier.editing.fixtures.CodeActionScenarios;
import fr.craft.gabier.editing.fixtures.FakeJavaLanguageServer;
import fr.craft.gabier.editing.fixtures.Scenario;
import fr.craft.gabier.protocol.CodeActions;
import fr.craft.gabier.protocol.RpcResponse;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The method {@code actions} through the handler, against the fake language server scripted by the scenarios: what
 * is answered, and what is refused.
 */
class CodeActionsMethodTest {

    @TempDir
    Path project;

    private RequestHandler handler(Scenario... scenarios) {
        var server = new FakeJavaLanguageServer(project);
        for (Scenario scenario : scenarios) {
            server.scripted(scenario);
        }

        return new RequestHandler(List.of(new CodeActionsMethod(new ListCodeActions(server))));
    }

    private Path offers() {
        return project.resolve("src/main/java/demo/Offers.java");
    }

    private static String request(Path file, String startLine, String startColumn, String endLine, String endColumn) {
        return """
            {"jsonrpc":"2.0","id":1,"method":"actions","params":\
            {"file":"%s","startLine":"%s","startColumn":"%s","endLine":"%s","endColumn":"%s"}}\
            """.formatted(file, startLine, startColumn, endLine, endColumn);
    }

    private static CodeActions offered(RequestHandler.Reply reply) {
        return CodeActions.of(((RpcResponse.Success) RpcResponse.parse(reply.line())).result());
    }

    private static RpcResponse.Failure refused(RequestHandler.Reply reply) {
        return (RpcResponse.Failure) RpcResponse.parse(reply.line());
    }

    @Test
    void isNamedAndTakesTheFileAndTheRange() {
        var method = new CodeActionsMethod(new ListCodeActions(new FakeJavaLanguageServer(project)));

        assertThat(method.name()).isEqualTo("actions");
        assertThat(method.parameters())
                .containsExactlyInAnyOrder("file", "startLine", "startColumn", "endLine", "endColumn");
    }

    @Test
    void answersTheKindAndTheTitleOfEachActionTheRangeOffers() {
        var handler = handler(CodeActionScenarios.CODE_ACTIONS_ON_A_TYPE_THAT_IS_NOT_IMPORTED);

        var answer = offered(handler.handle(request(offers(), "3", "4", "3", "8")));

        assertThat(answer.actions()).containsExactly(new CodeActions.Action("quickfix", "Import 'List' (java.util)"));
    }

    @Test
    void answersAnEmptyListWhereNothingIsOffered() {
        var handler = handler(CodeActionScenarios.CODE_ACTIONS_WHERE_NOTHING_IS_OFFERED);

        assertThat(offered(handler.handle(request(offers(), "6", "0", "6", "0")))
                        .actions())
                .isEmpty();
    }

    @Test
    void aFileOutsideTheProjectIsRefusedWithItsProblemObject() {
        var handler = handler(CodeActionScenarios.CODE_ACTIONS_OUTSIDE_THE_PROJECT);
        var outside = project.resolve("../outside/Elsewhere.java").normalize();

        var failure = refused(handler.handle(request(outside, "3", "4", "3", "8")));

        assertThat(failure.problem().key()).isEqualTo("language-server-file-outside-project");
        assertThat(failure.problem().parameters()).containsEntry("file", outside.toString());
    }

    @Test
    void aRangeThatEndsBeforeItStartsIsRefusedWithItsProblemObject() {
        var handler = handler();

        var failure = refused(handler.handle(request(offers(), "3", "8", "3", "4")));

        assertThat(failure.problem().key()).isEqualTo("editing-invalid-range");
    }

    @Test
    void aNumberThatIsNotOneIsRefusedNamingTheParameter() {
        var handler = handler();

        var failure = refused(handler.handle(request(offers(), "3", "4", "three", "8")));

        assertThat(failure.problem().key()).isEqualTo("invalid-parameter");
        assertThat(failure.problem().parameters()).containsEntry("parameter", "endLine");
    }

    @Test
    void aRequestWithoutTheEndOfTheRangeIsRefusedBeforeTheMethodIsCalled() {
        var handler = handler();

        var failure = refused(handler.handle("""
            {"jsonrpc":"2.0","id":1,"method":"actions","params":\
            {"file":"%s","startLine":"3","startColumn":"4","endLine":"3"}}\
            """.formatted(offers())));

        assertThat(failure.problem().key()).isEqualTo("missing-parameter");
        assertThat(failure.problem().parameters()).containsEntry("parameter", "endColumn");
    }
}
