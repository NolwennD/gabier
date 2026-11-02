package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.editing.application.Rename;
import eu.gabier.editing.fixtures.FakeJavaLanguageServer;
import eu.gabier.editing.fixtures.Scenario;
import eu.gabier.editing.fixtures.Scenarios;
import eu.gabier.editing.infrastructure.secondary.NioFileStore;
import eu.gabier.protocol.ChangedFiles;
import eu.gabier.protocol.RpcResponse;
import eu.gabier.shared.error.domain.ErrorStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The methods {@code rename} (the symbol at a position) and {@code rename-token} (the symbol an identifier names)
 * through the handler, against the fake language server scripted by the scenarios and the real files of a temporary
 * project: what is written, what is answered, and that a refusal writes nothing.
 */
class RenameMethodsTest {

    private static final Path GREETER = Path.of("src/main/java/demo/Greeter.java");
    private static final Path MAIN = Path.of("src/main/java/demo/Main.java");
    private static final Path WELCOMER = Path.of("src/main/java/demo/Welcomer.java");

    @TempDir
    Path project;

    private Path greeterSource;
    private Path mainSource;

    private RequestHandler handler(Scenario scenario) throws IOException {
        scenario.writeProject(project);
        var rename = new Rename(new FakeJavaLanguageServer(project).scripted(scenario), new NioFileStore());
        greeterSource = project.resolve(GREETER);
        mainSource = project.resolve(MAIN);

        return new RequestHandler(List.of(new RenameMethod(rename), new RenameTokenMethod(rename)));
    }

    private static String line(String method, String params) {
        return """
            {"jsonrpc":"2.0","id":1,"method":"%s","params":%s}\
            """.formatted(method, params);
    }

    /** The request of {@code RENAME_METHOD}: {@code greet}, at the line 5, column 18 (from 0) of the Greeter. */
    private String renameGreet(String newName, String dryRun) {
        return line("rename", """
            {"file":"%s","line":"5","column":"18","newName":"%s","dryRun":"%s"}\
            """.formatted(project.resolve(GREETER), newName, dryRun));
    }

    private String renameGreetByToken(String newName, String dryRun) {
        return line("rename-token", """
            {"file":"%s","token":"greet","newName":"%s","dryRun":"%s"}\
            """.formatted(project.resolve(GREETER), newName, dryRun));
    }

    private static ChangedFiles changed(RequestHandler.Reply reply) {
        return ChangedFiles.of(((RpcResponse.Success) RpcResponse.parse(reply.line())).result());
    }

    private static RpcResponse.Failure refused(RequestHandler.Reply reply) {
        return (RpcResponse.Failure) RpcResponse.parse(reply.line());
    }

    @Test
    void renameIsNamedAndTakesTheFileThePositionTheNewNameAndTheDryRun() throws IOException {
        var method = new RenameMethod(new Rename(new FakeJavaLanguageServer(project), new NioFileStore()));

        assertThat(method.name()).isEqualTo("rename");
        assertThat(method.parameters()).containsExactlyInAnyOrder("file", "line", "column", "newName", "dryRun");
    }

    @Test
    void renameTokenIsNamedAndTakesTheFileTheTokenTheNewNameAndTheDryRun() {
        var method = new RenameTokenMethod(new Rename(new FakeJavaLanguageServer(project), new NioFileStore()));

        assertThat(method.name()).isEqualTo("rename-token");
        assertThat(method.parameters()).containsExactlyInAnyOrder("file", "token", "newName", "dryRun");
    }

    @Test
    void renamesTheSymbolAtAPositionInEveryFileThatUsesItAndAnswersWhatChangedAndTheDiff() throws IOException {
        var handler = handler(Scenarios.RENAME_METHOD);

        var answer = changed(handler.handle(renameGreet("welcome", "false")));

        assertThat(Files.readString(greeterSource)).contains("public String welcome(String name)");
        assertThat(Files.readString(mainSource)).contains("greeter.welcome(\"world\")");
        assertThat(answer.modified()).containsExactly(greeterSource, mainSource);
        assertThat(answer.created()).isEmpty();
        assertThat(answer.deleted()).isEmpty();
        assertThat(answer.renamed()).isEmpty();
        assertThat(answer.diff())
                .contains("--- " + greeterSource, "+++ " + greeterSource)
                .contains("-    public String greet(String name) {", "+    public String welcome(String name) {")
                .contains("-        System.out.println(greeter.greet(\"world\"));")
                .contains("+        System.out.println(greeter.welcome(\"world\"));");
    }

    @Test
    void aDryRunAnswersWhatWouldChangeAndWritesNothing() throws IOException {
        var handler = handler(Scenarios.RENAME_METHOD);
        var greeter = Files.readString(greeterSource);
        var main = Files.readString(mainSource);

        var answer = changed(handler.handle(renameGreet("welcome", "true")));

        assertThat(answer.modified()).containsExactly(greeterSource, mainSource);
        assertThat(answer.diff()).contains("+    public String welcome(String name) {");
        assertThat(Files.readString(greeterSource)).isEqualTo(greeter);
        assertThat(Files.readString(mainSource)).isEqualTo(main);
    }

    @Test
    void renamesTheSymbolNamedByTheFirstOccurrenceOfAnIdentifier() throws IOException {
        var handler = handler(Scenarios.RENAME_METHOD);

        var answer = changed(handler.handle(renameGreetByToken("welcome", "false")));

        assertThat(answer.modified()).containsExactly(greeterSource, mainSource);
        assertThat(Files.readString(greeterSource)).contains("public String welcome(String name)");
        assertThat(Files.readString(mainSource)).contains("greeter.welcome(\"world\")");
    }

    @Test
    void aDryRunByTokenWritesNothing() throws IOException {
        var handler = handler(Scenarios.RENAME_METHOD);
        var greeter = Files.readString(greeterSource);

        var answer = changed(handler.handle(renameGreetByToken("welcome", "true")));

        assertThat(answer.modified()).containsExactly(greeterSource, mainSource);
        assertThat(Files.readString(greeterSource)).isEqualTo(greeter);
    }

    @Test
    void aTypeRenameMovesItsFileAndSaysSo() throws IOException {
        var handler = handler(Scenarios.RENAME_TYPE);

        var answer = handler.handle(line("rename", """
            {"file":"%s","line":"2","column":"13","newName":"Welcomer","dryRun":"false"}\
            """.formatted(greeterSource)));

        var changed = changed(answer);
        assertThat(changed.renamed()).containsExactly(new ChangedFiles.Moved(greeterSource, project.resolve(WELCOMER)));
        assertThat(changed.modified()).containsExactly(greeterSource, mainSource);
        assertThat(greeterSource).doesNotExist();
        assertThat(project.resolve(WELCOMER)).exists();
        assertThat(Files.readString(project.resolve(WELCOMER))).contains("public class Welcomer");
    }

    @Test
    void whereThereIsNoSymbolItIsRefusedWithItsProblemObjectAndNothingIsWritten() throws IOException {
        var handler = handler(Scenarios.RENAME_WHERE_THERE_IS_NO_SYMBOL);
        var greeter = Files.readString(greeterSource);

        var failure = refused(handler.handle(line("rename", """
            {"file":"%s","line":"4","column":"0","newName":"anything","dryRun":"false"}\
            """.formatted(greeterSource))));

        assertThat(failure.problem().key()).isEqualTo("language-server-no-such-symbol");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(Files.readString(greeterSource)).isEqualTo(greeter);
    }

    @Test
    void anInvalidNameIsRefusedWithItsProblemObjectAndNothingIsWritten() throws IOException {
        var handler = handler(Scenarios.RENAME_TO_AN_INVALID_NAME);
        var greeter = Files.readString(greeterSource);

        var failure = refused(handler.handle(line("rename", """
            {"file":"%s","line":"6","column":"15","newName":"1st","dryRun":"false"}\
            """.formatted(greeterSource))));

        assertThat(failure.problem().key()).isEqualTo("language-server-invalid-name");
        assertThat(Files.readString(greeterSource)).isEqualTo(greeter);
    }

    @Test
    void aFileOutsideTheProjectIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.RENAME_OUTSIDE_THE_PROJECT);
        var outside = project.resolve("../outside/Elsewhere.java").normalize();

        var failure = refused(handler.handle(line("rename", """
            {"file":"%s","line":"0","column":"0","newName":"anything","dryRun":"false"}\
            """.formatted(outside))));

        assertThat(failure.problem().key()).isEqualTo("language-server-file-outside-project");
        assertThat(failure.problem().parameters()).containsEntry("file", outside.toString());
    }

    @Test
    void aServerThatIsNotReadyIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.RENAME_WHILE_THE_SERVER_IMPORTS);

        var failure = refused(handler.handle(line("rename", """
            {"file":"%s","line":"6","column":"15","newName":"text","dryRun":"false"}\
            """.formatted(greeterSource))));

        assertThat(failure.problem().key()).isEqualTo("language-server-not-ready");
    }

    @Test
    void aChangeSetThatCannotBeAppliedIsRefusedWithItsProblemObjectAndNoFileIsWritten() throws IOException {
        var handler = handler(Scenarios.RENAME_METHOD);
        // The server answers for the text it knew: the use in Main is no more where its edit says.
        Files.writeString(mainSource, "class Main {}\n");
        var greeter = Files.readString(greeterSource);

        var failure = refused(handler.handle(renameGreet("welcome", "false")));

        assertThat(failure.problem().key()).isEqualTo("editing-stale-edit");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(Files.readString(greeterSource)).isEqualTo(greeter);
        assertThat(Files.readString(mainSource)).isEqualTo("class Main {}\n");
    }

    @Test
    void anIdentifierThatIsNotInTheFileIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.RENAME_METHOD);

        var failure = refused(handler.handle(line("rename-token", """
            {"file":"%s","token":"missing","newName":"welcome","dryRun":"false"}\
            """.formatted(greeterSource))));

        assertThat(failure.problem().key()).isEqualTo("editing-token-not-found");
        assertThat(failure.problem().parameters()).containsEntry("token", "missing");
    }

    @Test
    void aRequestWithoutTheNewNameIsRefusedBeforeTheMethodIsCalled() throws IOException {
        var handler = handler(Scenarios.RENAME_METHOD);

        var failure = refused(handler.handle(line("rename", """
            {"file":"%s","line":"5","column":"18","dryRun":"false"}\
            """.formatted(greeterSource))));

        assertThat(failure.problem().key()).isEqualTo("missing-parameter");
        assertThat(failure.problem().parameters()).containsEntry("parameter", "newName");
    }

    @Test
    void aValueThatMeansNothingIsRefusedAsAnInvalidParameterNamingIt() throws IOException {
        var handler = handler(Scenarios.RENAME_METHOD);
        var file = project.resolve(GREETER);
        List<String[]> requests = List.of(
                new String[] {
                    "line",
                    "rename",
                    "{\"file\":\"%s\",\"line\":\"five\",\"column\":\"18\",\"newName\":\"w\",\"dryRun\":\"false\"}"
                },
                new String[] {
                    "line",
                    "rename",
                    "{\"file\":\"%s\",\"line\":\"-1\",\"column\":\"18\",\"newName\":\"w\",\"dryRun\":\"false\"}"
                },
                new String[] {
                    "column",
                    "rename",
                    "{\"file\":\"%s\",\"line\":\"5\",\"column\":\"1.5\",\"newName\":\"w\",\"dryRun\":\"false\"}"
                },
                new String[] {
                    "column",
                    "rename",
                    "{\"file\":\"%s\",\"line\":\"5\",\"column\":\"-3\",\"newName\":\"w\",\"dryRun\":\"false\"}"
                },
                new String[] {
                    "dryRun",
                    "rename",
                    "{\"file\":\"%s\",\"line\":\"5\",\"column\":\"18\",\"newName\":\"w\",\"dryRun\":\"maybe\"}"
                },
                new String[] {
                    "dryRun",
                    "rename-token",
                    "{\"file\":\"%s\",\"token\":\"greet\",\"newName\":\"w\",\"dryRun\":\"yes\"}"
                },
                new String[] {
                    "token", "rename-token", "{\"file\":\"%s\",\"token\":\" \",\"newName\":\"w\",\"dryRun\":\"false\"}"
                },
                new String[] {
                    "file",
                    "rename-token",
                    "{\"file\":\"a\\u0000b\",\"token\":\"greet\",\"newName\":\"w\",\"dryRun\":\"false\"}"
                });

        for (var request : requests) {
            var reply = handler.handle(line(request[1], request[2].formatted(file)));

            var failure = refused(reply);
            assertThat(reply.line()).as(request[2]).contains("\"code\":-32602");
            assertThat(failure.problem().key()).as(request[2]).isEqualTo("invalid-parameter");
            assertThat(failure.problem().parameters()).as(request[2]).containsEntry("parameter", request[0]);
        }
    }
}
