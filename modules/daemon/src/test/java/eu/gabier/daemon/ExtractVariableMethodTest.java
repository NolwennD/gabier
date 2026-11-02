package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.editing.application.ExtractVariable;
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
 * The method {@code extract-variable} through the handler, against the fake language server scripted by the scenarios
 * and the real files of a temporary project: what is written, what is answered, and that a refusal writes nothing.
 */
class ExtractVariableMethodTest {

    private static final Path GREETER = Path.of("src/main/java/demo/Greeter.java");

    @TempDir
    Path project;

    private Path greeterSource;

    private RequestHandler handler(Scenario scenario) throws IOException {
        scenario.writeProject(project);
        var extract = new ExtractVariable(new FakeJavaLanguageServer(project).scripted(scenario), new NioFileStore());
        greeterSource = project.resolve(GREETER);

        return new RequestHandler(List.of(new ExtractVariableMethod(extract)));
    }

    private static String line(String params) {
        return """
            {"jsonrpc":"2.0","id":1,"method":"extract-variable","params":%s}\
            """.formatted(params);
    }

    private static String extract(Path file, String start, String end, String name, String dryRun) {
        return line("""
            {"file":"%s","startLine":"%s","startColumn":"%s","endLine":"%s","endColumn":"%s","name":"%s","dryRun":"%s"}\
            """.formatted(
                file, start.split(":")[0], start.split(":")[1], end.split(":")[0], end.split(":")[1], name, dryRun));
    }

    private String joined(String dryRun) {
        return extract(greeterSource, "6:25", "6:47", "joined", dryRun);
    }

    private static ChangedFiles changed(RequestHandler.Reply reply) {
        return ChangedFiles.of(((RpcResponse.Success) RpcResponse.parse(reply.line())).result());
    }

    private static RpcResponse.Failure refused(RequestHandler.Reply reply) {
        return (RpcResponse.Failure) RpcResponse.parse(reply.line());
    }

    @Test
    void isNamedAndTakesTheFileTheSelectionTheNameAndTheDryRun() throws IOException {
        var method =
                new ExtractVariableMethod(new ExtractVariable(new FakeJavaLanguageServer(project), new NioFileStore()));

        assertThat(method.name()).isEqualTo("extract-variable");
        assertThat(method.parameters())
                .containsExactlyInAnyOrder(
                        "file", "startLine", "startColumn", "endLine", "endColumn", "name", "dryRun");
    }

    @Test
    void extractsTheVariableOnDiskAndAnswersWhatChangedAndTheDiff() throws IOException {
        var handler = handler(Scenarios.EXTRACT_VARIABLE);

        var answer = changed(handler.handle(joined("false")));

        assertThat(Files.readString(greeterSource))
                .contains("        String joined = greeting + \", \" + name;\n        String message = joined;\n");
        assertThat(answer.modified()).containsExactly(greeterSource);
        assertThat(answer.created()).isEmpty();
        assertThat(answer.renamed()).isEmpty();
        assertThat(answer.deleted()).isEmpty();
        assertThat(answer.diff())
                .contains("--- " + greeterSource, "+++ " + greeterSource)
                .contains("+        String joined = greeting + \", \" + name;", "+        String message = joined;");
    }

    @Test
    void aDryRunAnswersWhatWouldChangeAndWritesNothing() throws IOException {
        var handler = handler(Scenarios.EXTRACT_VARIABLE);
        var greeter = Files.readString(greeterSource);

        var answer = changed(handler.handle(joined("true")));

        assertThat(answer.modified()).containsExactly(greeterSource);
        assertThat(answer.diff()).contains("+        String message = joined;");
        assertThat(Files.readString(greeterSource)).isEqualTo(greeter);
    }

    @Test
    void aSelectionThatIsNotAnExpressionIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.EXTRACT_VARIABLE_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION);
        var greeter = Files.readString(greeterSource);

        var failure = refused(handler.handle(extract(greeterSource, "7:8", "7:14", "joined", "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-not-extractable");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(failure.problem().parameters())
                .containsEntry("line", "7")
                .containsEntry("column", "8")
                .containsEntry("endLine", "7")
                .containsEntry("endColumn", "14");
        assertThat(Files.readString(greeterSource)).isEqualTo(greeter);
    }

    @Test
    void aNameThatIsNotAnIdentifierIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.EXTRACT_VARIABLE_TO_AN_INVALID_NAME);
        var greeter = Files.readString(greeterSource);

        var failure = refused(handler.handle(extract(greeterSource, "6:25", "6:47", "1st", "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-invalid-name");
        assertThat(failure.problem().parameters()).containsEntry("name", "1st");
        assertThat(Files.readString(greeterSource)).isEqualTo(greeter);
    }

    @Test
    void aServerThatIsNotReadyIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.EXTRACT_VARIABLE_WHILE_THE_SERVER_IMPORTS);

        var failure = refused(handler.handle(joined("false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-not-ready");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
    }

    @Test
    void aFileOutsideTheProjectIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.EXTRACT_VARIABLE_OUTSIDE_THE_PROJECT);
        var outside = project.resolve("../outside/Elsewhere.java").normalize();

        var failure = refused(handler.handle(extract(outside, "0:0", "0:1", "joined", "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-file-outside-project");
        assertThat(failure.problem().parameters()).containsEntry("file", outside.toString());
    }

    @Test
    void aChangeSetThatCannotBeAppliedIsRefusedWithItsProblemObjectAndNothingIsWritten() throws IOException {
        var handler = handler(Scenarios.EXTRACT_VARIABLE);
        // The server answers for the text it knew: the statement is no more where its edit says.
        Files.writeString(greeterSource, "class Greeter {}\n");

        var failure = refused(handler.handle(joined("false")));

        assertThat(failure.problem().key()).isEqualTo("editing-stale-edit");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(Files.readString(greeterSource)).isEqualTo("class Greeter {}\n");
    }

    @Test
    void aSelectionThatEndsBeforeItStartsIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.EXTRACT_VARIABLE);

        var failure = refused(handler.handle(extract(greeterSource, "6:47", "6:25", "joined", "false")));

        assertThat(failure.problem().key()).isEqualTo("editing-invalid-range");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
    }

    @Test
    void aRequestWithoutTheNameIsRefusedBeforeTheMethodIsCalled() throws IOException {
        var handler = handler(Scenarios.EXTRACT_VARIABLE);

        var failure = refused(handler.handle(line("""
            {"file":"%s","startLine":"6","startColumn":"25","endLine":"6","endColumn":"47","dryRun":"false"}\
            """.formatted(greeterSource))));

        assertThat(failure.problem().key()).isEqualTo("missing-parameter");
        assertThat(failure.problem().parameters()).containsEntry("parameter", "name");
    }

    @Test
    void aValueThatMeansNothingIsRefusedAsAnInvalidParameterNamingIt() throws IOException {
        var handler = handler(Scenarios.EXTRACT_VARIABLE);
        String all = "\"startLine\":\"6\",\"startColumn\":\"25\",\"endLine\":\"6\",\"endColumn\":\"47\"";
        List<String[]> requests = List.of(
                new String[] {
                    "dryRun",
                    "{\"file\":\"%s\",%s,\"name\":\"joined\",\"dryRun\":\"maybe\"}".formatted(greeterSource, all)
                },
                new String[] {
                    "file", "{\"file\":\"a\\u0000b\",%s,\"name\":\"joined\",\"dryRun\":\"false\"}".formatted(all)
                },
                new String[] {
                    "name", "{\"file\":\"%s\",%s,\"name\":\" \",\"dryRun\":\"false\"}".formatted(greeterSource, all)
                },
                new String[] {
                    "startLine",
                    "{\"file\":\"%s\",\"startLine\":\"-1\",\"startColumn\":\"25\",\"endLine\":\"6\",\"endColumn\":\"47\",\"name\":\"joined\",\"dryRun\":\"false\"}"
                            .formatted(greeterSource)
                },
                new String[] {
                    "endColumn",
                    "{\"file\":\"%s\",\"startLine\":\"6\",\"startColumn\":\"25\",\"endLine\":\"6\",\"endColumn\":\"x\",\"name\":\"joined\",\"dryRun\":\"false\"}"
                            .formatted(greeterSource)
                });

        for (var request : requests) {
            var reply = handler.handle(line(request[1]));

            var failure = refused(reply);
            assertThat(reply.line()).as(request[1]).contains("\"code\":-32602");
            assertThat(failure.problem().key()).as(request[1]).isEqualTo("invalid-parameter");
            assertThat(failure.problem().parameters()).as(request[1]).containsEntry("parameter", request[0]);
        }
    }
}
