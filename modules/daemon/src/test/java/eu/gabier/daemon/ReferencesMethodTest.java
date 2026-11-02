package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.editing.application.FindReferences;
import eu.gabier.editing.fixtures.FakeJavaLanguageServer;
import eu.gabier.editing.fixtures.Scenario;
import eu.gabier.editing.fixtures.Scenarios;
import eu.gabier.editing.infrastructure.secondary.NioFileStore;
import eu.gabier.protocol.FoundReferences;
import eu.gabier.protocol.FoundReferences.Place;
import eu.gabier.protocol.RpcResponse;
import eu.gabier.shared.error.domain.ErrorStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The method {@code references} through the handler, against the fake language server scripted by the scenarios and
 * the real files of a temporary project: what is answered, with the lines of source, and that nothing is written.
 */
class ReferencesMethodTest {

    @TempDir
    Path project;

    private RequestHandler handler(Scenario... scenarios) throws IOException {
        var server = new FakeJavaLanguageServer(project);
        for (var scenario : scenarios) {
            scenario.writeProject(project);
            server.scripted(scenario);
        }

        return new RequestHandler(List.of(new ReferencesMethod(new FindReferences(server, new NioFileStore()))));
    }

    private String request(String file, String line, String column, String includeDeclaration) {
        return """
            {"jsonrpc":"2.0","id":1,"method":"references","params":\
            {"file":"%s","line":"%s","column":"%s","includeDeclaration":"%s"}}\
            """.formatted(project.resolve(file), line, column, includeDeclaration);
    }

    private static FoundReferences found(RequestHandler.Reply reply) {
        return FoundReferences.of(((RpcResponse.Success) RpcResponse.parse(reply.line())).result());
    }

    private static RpcResponse.Failure refused(RequestHandler.Reply reply) {
        return (RpcResponse.Failure) RpcResponse.parse(reply.line());
    }

    private Place place(String file, int line, int column, String source) {
        return new Place(project.resolve(file).toString(), line, column, source);
    }

    @Test
    void isNamedReferencesAndTakesTheFileThePositionAndTheDeclarationFlag() throws IOException {
        var method = new ReferencesMethod(new FindReferences(new FakeJavaLanguageServer(project), new NioFileStore()));

        assertThat(method.name()).isEqualTo("references");
        assertThat(method.parameters()).containsExactlyInAnyOrder("file", "line", "column", "includeDeclaration");
    }

    @Test
    void answersTheUsesOfATypeWithTheZeroBasedPositionsAndTheLinesOfSource() throws IOException {
        var handler = handler(Scenarios.REFERENCES_OF_A_TYPE);

        var answer = found(handler.handle(request("src/main/java/demo/Ledger.java", "2", "13", "false")));

        assertThat(answer.declarations()).isEmpty();
        assertThat(answer.references())
                .containsExactly(
                        place("src/main/java/demo/app/Bank.java", 2, 7, "import demo.Ledger;"),
                        place("src/main/java/demo/app/Bank.java", 6, 8, "        Ledger ledger = new Ledger();"),
                        place("src/main/java/demo/app/Bank.java", 6, 28, "        Ledger ledger = new Ledger();"),
                        place("src/test/java/demo/LedgerTest.java", 4, 8, "        Ledger ledger = new Ledger();"),
                        place("src/test/java/demo/LedgerTest.java", 4, 28, "        Ledger ledger = new Ledger();"));
    }

    @Test
    void answersTheDeclarationApartWhenItIsAskedFor() throws IOException {
        var handler = handler(Scenarios.REFERENCES_OF_A_METHOD_WITH_ITS_DECLARATION);

        var answer = found(handler.handle(request("src/main/java/demo/app/Bank.java", "7", "15", "true")));

        assertThat(answer.declarations())
                .containsExactly(
                        place("src/main/java/demo/Ledger.java", 9, 16, "    public void deposit(int amount) {"));
        assertThat(answer.references()).hasSize(3);
    }

    @Test
    void aSymbolNothingUsesGetsTwoEmptyLists() throws IOException {
        var handler = handler(Scenarios.REFERENCES_OF_A_SYMBOL_NOTHING_USES);

        var line = handler.handle(request("src/main/java/demo/Ledger.java", "18", "8", "false"))
                .line();

        assertThat(line).isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"declarations\":[],\"references\":[]}}");
    }

    @Test
    void writesNothingAndDoesNothingAfterTheReply() throws IOException {
        var handler = handler(Scenarios.REFERENCES_OF_A_TYPE);
        var ledger = project.resolve("src/main/java/demo/Ledger.java");
        var before = Files.readString(ledger);

        handler.handle(request("src/main/java/demo/Ledger.java", "2", "13", "false"))
                .afterReply()
                .run();

        assertThat(ledger).content().isEqualTo(before);
    }

    @Test
    void aPositionOnNothingIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.REFERENCES_WHERE_THERE_IS_NO_SYMBOL);

        var failure = refused(handler.handle(request("src/main/java/demo/Ledger.java", "4", "0", "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-no-such-symbol");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(failure.problem().parameters()).containsEntry("line", "4").containsEntry("column", "0");
    }

    @Test
    void aFileOutsideTheProjectIsRefused() throws IOException {
        var handler = handler(Scenarios.REFERENCES_OF_A_FILE_OUTSIDE_THE_PROJECT);

        var failure = refused(handler.handle(request("../outside/Elsewhere.java", "0", "0", "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-file-outside-project");
    }

    @Test
    void aServerThatIsNotReadyIsRefused() throws IOException {
        var handler = handler(Scenarios.REFERENCES_WHILE_THE_SERVER_IMPORTS);

        var failure = refused(handler.handle(request("src/main/java/demo/Ledger.java", "2", "13", "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-not-ready");
    }

    @Test
    void aFileThatChangedSinceTheServerAnsweredIsRefusedAsStale() throws IOException {
        var handler = handler(Scenarios.REFERENCES_OF_A_TYPE);
        Files.writeString(project.resolve("src/main/java/demo/app/Bank.java"), "package demo.app;\n");

        var failure = refused(handler.handle(request("src/main/java/demo/Ledger.java", "2", "13", "false")));

        assertThat(failure.problem().key()).isEqualTo("editing-stale-edit");
    }

    @Test
    void aRequestWithoutTheDeclarationFlagIsRefusedBeforeTheMethodIsCalled() throws IOException {
        var handler = handler(Scenarios.REFERENCES_OF_A_TYPE);

        var failure = refused(handler.handle("""
            {"jsonrpc":"2.0","id":1,"method":"references","params":{"file":"%s","line":"2","column":"13"}}\
            """.formatted(project.resolve("src/main/java/demo/Ledger.java"))));

        assertThat(failure.problem().key()).isEqualTo("missing-parameter");
        assertThat(failure.problem().parameters()).containsEntry("parameter", "includeDeclaration");
    }

    @Test
    void aFlagThatIsNeitherTrueNorFalseANegativeNumberAndAnythingThatIsNotAPathAreRefusedAsInvalid()
            throws IOException {
        var handler = handler(Scenarios.REFERENCES_OF_A_TYPE);

        assertThat(refused(handler.handle(request("src/main/java/demo/Ledger.java", "2", "13", "yes")))
                        .problem()
                        .parameters())
                .containsEntry("parameter", "includeDeclaration");
        assertThat(refused(handler.handle(request("src/main/java/demo/Ledger.java", "-1", "13", "false")))
                        .problem()
                        .parameters())
                .containsEntry("parameter", "line");
        assertThat(refused(handler.handle(request("src/main/java/demo/Ledger.java", "2", "x", "false")))
                        .problem()
                        .parameters())
                .containsEntry("parameter", "column");
        assertThat(refused(handler.handle("""
            {"jsonrpc":"2.0","id":1,"method":"references","params":\
            {"file":"a\\u0000b","line":"2","column":"13","includeDeclaration":"false"}}\
            """)).problem().parameters()).containsEntry("parameter", "file");
    }
}
