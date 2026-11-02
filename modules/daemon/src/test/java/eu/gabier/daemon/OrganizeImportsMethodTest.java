package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.editing.application.OrganizeImports;
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
 * The method {@code organize-imports} through the handler, against the fake language server scripted by the scenarios
 * and the real files of a temporary project: what is written, what is answered, and that a refusal writes nothing.
 */
class OrganizeImportsMethodTest {

    private static final Path UNTIDY = Path.of("src/main/java/demo/Untidy.java");
    private static final Path TIDY = Path.of("src/main/java/demo/Tidy.java");

    @TempDir
    Path project;

    private Path untidySource;

    private RequestHandler handler(Scenario scenario) throws IOException {
        scenario.writeProject(project);
        var organize = new OrganizeImports(new FakeJavaLanguageServer(project).scripted(scenario), new NioFileStore());
        untidySource = project.resolve(UNTIDY);

        return new RequestHandler(List.of(new OrganizeImportsMethod(organize)));
    }

    private static String line(String params) {
        return """
            {"jsonrpc":"2.0","id":1,"method":"organize-imports","params":%s}\
            """.formatted(params);
    }

    private String organize(Path file, String dryRun) {
        return line("""
            {"file":"%s","dryRun":"%s"}\
            """.formatted(file, dryRun));
    }

    private static ChangedFiles changed(RequestHandler.Reply reply) {
        return ChangedFiles.of(((RpcResponse.Success) RpcResponse.parse(reply.line())).result());
    }

    private static RpcResponse.Failure refused(RequestHandler.Reply reply) {
        return (RpcResponse.Failure) RpcResponse.parse(reply.line());
    }

    @Test
    void isNamedAndTakesTheFileAndTheDryRun() throws IOException {
        var method =
                new OrganizeImportsMethod(new OrganizeImports(new FakeJavaLanguageServer(project), new NioFileStore()));

        assertThat(method.name()).isEqualTo("organize-imports");
        assertThat(method.parameters()).containsExactlyInAnyOrder("file", "dryRun");
    }

    @Test
    void organizesTheImportsOnDiskAndAnswersWhatChangedAndTheDiff() throws IOException {
        var handler = handler(Scenarios.ORGANIZE_IMPORTS);

        var answer = changed(handler.handle(organize(untidySource, "false")));

        assertThat(Files.readString(untidySource))
                .contains("import java.util.ArrayList;")
                .doesNotContain("import java.util.Set;");
        assertThat(answer.modified()).containsExactly(untidySource);
        assertThat(answer.created()).isEmpty();
        assertThat(answer.renamed()).isEmpty();
        assertThat(answer.deleted()).isEmpty();
        assertThat(answer.diff())
                .contains("--- " + untidySource, "+++ " + untidySource)
                .contains("-import java.util.Set;", "+import java.util.ArrayList;", "+import java.util.Collections;");
    }

    @Test
    void aDryRunAnswersWhatWouldChangeAndWritesNothing() throws IOException {
        var handler = handler(Scenarios.ORGANIZE_IMPORTS);
        var untidy = Files.readString(untidySource);

        var answer = changed(handler.handle(organize(untidySource, "true")));

        assertThat(answer.modified()).containsExactly(untidySource);
        assertThat(answer.diff()).contains("+import java.util.ArrayList;");
        assertThat(Files.readString(untidySource)).isEqualTo(untidy);
    }

    @Test
    void aFileWhoseImportsAreInOrderAnswersNoChangeAndNoDiff() throws IOException {
        var handler = handler(Scenarios.ORGANIZE_IMPORTS_IN_ORDER);
        var tidy = project.resolve(TIDY);
        var text = Files.readString(tidy);

        var answer = changed(handler.handle(organize(tidy, "false")));

        assertThat(answer).isEqualTo(new ChangedFiles(List.of(), List.of(), List.of(), List.of(), ""));
        assertThat(Files.readString(tidy)).isEqualTo(text);
    }

    @Test
    void aServerThatIsNotReadyIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.ORGANIZE_IMPORTS_WHILE_THE_SERVER_IMPORTS);

        var failure = refused(handler.handle(organize(untidySource, "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-not-ready");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
    }

    @Test
    void aFileOutsideTheProjectIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.ORGANIZE_IMPORTS_OUTSIDE_THE_PROJECT);
        var outside = project.resolve("../outside/Elsewhere.java").normalize();

        var failure = refused(handler.handle(organize(outside, "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-file-outside-project");
        assertThat(failure.problem().parameters()).containsEntry("file", outside.toString());
    }

    @Test
    void aChangeSetThatCannotBeAppliedIsRefusedWithItsProblemObjectAndNothingIsWritten() throws IOException {
        var handler = handler(Scenarios.ORGANIZE_IMPORTS);
        // The server answers for the text it knew: the imports are no more where its edit says.
        Files.writeString(untidySource, "class Untidy {}\n");

        var failure = refused(handler.handle(organize(untidySource, "false")));

        assertThat(failure.problem().key()).isEqualTo("editing-stale-edit");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(Files.readString(untidySource)).isEqualTo("class Untidy {}\n");
    }

    @Test
    void aRequestWithoutTheDryRunIsRefusedBeforeTheMethodIsCalled() throws IOException {
        var handler = handler(Scenarios.ORGANIZE_IMPORTS);

        var failure = refused(handler.handle(line("""
            {"file":"%s"}\
            """.formatted(untidySource))));

        assertThat(failure.problem().key()).isEqualTo("missing-parameter");
        assertThat(failure.problem().parameters()).containsEntry("parameter", "dryRun");
    }

    @Test
    void aValueThatMeansNothingIsRefusedAsAnInvalidParameterNamingIt() throws IOException {
        var handler = handler(Scenarios.ORGANIZE_IMPORTS);
        List<String[]> requests = List.of(
                new String[] {"dryRun", "{\"file\":\"%s\",\"dryRun\":\"maybe\"}".formatted(untidySource)},
                new String[] {"file", "{\"file\":\"a\\u0000b\",\"dryRun\":\"false\"}"});

        for (var request : requests) {
            var reply = handler.handle(line(request[1]));

            var failure = refused(reply);
            assertThat(reply.line()).as(request[1]).contains("\"code\":-32602");
            assertThat(failure.problem().key()).as(request[1]).isEqualTo("invalid-parameter");
            assertThat(failure.problem().parameters()).as(request[1]).containsEntry("parameter", request[0]);
        }
    }
}
