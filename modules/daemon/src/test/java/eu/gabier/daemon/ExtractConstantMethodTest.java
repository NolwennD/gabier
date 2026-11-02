package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.editing.application.ExtractConstant;
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
 * The method {@code extract-constant} through the handler, against the fake language server scripted by the scenarios
 * and the real files of a temporary project: what is written, what is answered, and that a refusal writes nothing.
 */
class ExtractConstantMethodTest {

    private static final Path TOTALS = Path.of("src/main/java/demo/Totals.java");

    @TempDir
    Path project;

    private Path totalsSource;

    private RequestHandler handler(Scenario scenario) throws IOException {
        scenario.writeProject(project);
        var extract = new ExtractConstant(new FakeJavaLanguageServer(project).scripted(scenario), new NioFileStore());
        totalsSource = project.resolve(TOTALS);

        return new RequestHandler(List.of(new ExtractConstantMethod(extract)));
    }

    private static String extract(Path file, String start, String end, String name, String dryRun) {
        return """
            {"jsonrpc":"2.0","id":1,"method":"extract-constant","params":\
            {"file":"%s","startLine":"%s","startColumn":"%s","endLine":"%s","endColumn":"%s","name":"%s","dryRun":"%s"}}\
            """.formatted(
                file, start.split(":")[0], start.split(":")[1], end.split(":")[0], end.split(":")[1], name, dryRun);
    }

    private String countLabel(String dryRun) {
        return extract(totalsSource, "5:27", "5:36", "COUNT_LABEL", dryRun);
    }

    private static ChangedFiles changed(RequestHandler.Reply reply) {
        return ChangedFiles.of(((RpcResponse.Success) RpcResponse.parse(reply.line())).result());
    }

    private static RpcResponse.Failure refused(RequestHandler.Reply reply) {
        return (RpcResponse.Failure) RpcResponse.parse(reply.line());
    }

    @Test
    void isNamedAndTakesTheFileTheSelectionTheNameAndTheDryRun() {
        var method =
                new ExtractConstantMethod(new ExtractConstant(new FakeJavaLanguageServer(project), new NioFileStore()));

        assertThat(method.name()).isEqualTo("extract-constant");
        assertThat(method.parameters())
                .containsExactlyInAnyOrder(
                        "file", "startLine", "startColumn", "endLine", "endColumn", "name", "dryRun");
    }

    @Test
    void extractsTheConstantOnDiskAndAnswersWhatChangedAndTheDiff() throws IOException {
        var handler = handler(Scenarios.EXTRACT_CONSTANT);

        var answer = changed(handler.handle(countLabel("false")));

        assertThat(Files.readString(totalsSource))
                .contains("    private static final String COUNT_LABEL = \"count: \";\n")
                .contains("        System.out.println(COUNT_LABEL + count);\n");
        assertThat(answer.modified()).containsExactly(totalsSource);
        assertThat(answer.created()).isEmpty();
        assertThat(answer.renamed()).isEmpty();
        assertThat(answer.deleted()).isEmpty();
        assertThat(answer.diff())
                .contains("--- " + totalsSource, "+++ " + totalsSource)
                .contains("+    private static final String COUNT_LABEL = \"count: \";");
    }

    @Test
    void aDryRunAnswersWhatWouldChangeAndWritesNothing() throws IOException {
        var handler = handler(Scenarios.EXTRACT_CONSTANT);
        var totals = Files.readString(totalsSource);

        var answer = changed(handler.handle(countLabel("true")));

        assertThat(answer.modified()).containsExactly(totalsSource);
        assertThat(answer.diff()).contains("+    private static final String COUNT_LABEL = \"count: \";");
        assertThat(Files.readString(totalsSource)).isEqualTo(totals);
    }

    @Test
    void anExpressionThatCannotBeAConstantIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.EXTRACT_CONSTANT_THAT_USES_A_LOCAL_VARIABLE);
        var totals = Files.readString(totalsSource);

        var failure = refused(handler.handle(extract(totalsSource, "5:27", "5:44", "COUNT_LABEL", "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-not-extractable");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(failure.problem().parameters())
                .containsEntry("line", "5")
                .containsEntry("column", "27")
                .containsEntry("endLine", "5")
                .containsEntry("endColumn", "44");
        assertThat(Files.readString(totalsSource)).isEqualTo(totals);
    }

    @Test
    void aNameThatIsNotAnIdentifierIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.EXTRACT_CONSTANT_TO_AN_INVALID_NAME);
        var totals = Files.readString(totalsSource);

        var failure = refused(handler.handle(extract(totalsSource, "5:27", "5:36", "1ST", "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-invalid-name");
        assertThat(failure.problem().parameters()).containsEntry("name", "1ST");
        assertThat(Files.readString(totalsSource)).isEqualTo(totals);
    }

    @Test
    void aFileOutsideTheProjectIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.EXTRACT_CONSTANT_OUTSIDE_THE_PROJECT);
        var outside = project.resolve("../outside/Elsewhere.java").normalize();

        var failure = refused(handler.handle(extract(outside, "0:0", "0:1", "ANYTHING", "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-file-outside-project");
        assertThat(failure.problem().parameters()).containsEntry("file", outside.toString());
    }

    @Test
    void aRequestWithoutTheNameIsRefusedBeforeTheMethodIsCalled() throws IOException {
        var handler = handler(Scenarios.EXTRACT_CONSTANT);

        var failure = refused(handler.handle("""
            {"jsonrpc":"2.0","id":1,"method":"extract-constant","params":\
            {"file":"%s","startLine":"5","startColumn":"27","endLine":"5","endColumn":"36","dryRun":"false"}}\
            """.formatted(totalsSource)));

        assertThat(failure.problem().key()).isEqualTo("missing-parameter");
        assertThat(failure.problem().parameters()).containsEntry("parameter", "name");
    }
}
