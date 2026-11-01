package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.editing.application.ExtractField;
import fr.craft.gabier.editing.fixtures.FakeJavaLanguageServer;
import fr.craft.gabier.editing.fixtures.Scenario;
import fr.craft.gabier.editing.fixtures.Scenarios;
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
 * The method {@code extract-field} through the handler, against the fake language server scripted by the scenarios
 * and the real files of a temporary project: what is written, what is answered, and that a refusal writes nothing.
 */
class ExtractFieldMethodTest {

    private static final Path TOTALS = Path.of("src/main/java/demo/Totals.java");

    @TempDir
    Path project;

    private Path totalsSource;

    private RequestHandler handler(Scenario scenario) throws IOException {
        scenario.writeProject(project);
        var extract = new ExtractField(new FakeJavaLanguageServer(project).scripted(scenario), new NioFileStore());
        totalsSource = project.resolve(TOTALS);

        return new RequestHandler(List.of(new ExtractFieldMethod(extract)));
    }

    private static String extract(Path file, String start, String end, String name, String dryRun) {
        return """
            {"jsonrpc":"2.0","id":1,"method":"extract-field","params":\
            {"file":"%s","startLine":"%s","startColumn":"%s","endLine":"%s","endColumn":"%s","name":"%s","dryRun":"%s"}}\
            """.formatted(
                file, start.split(":")[0], start.split(":")[1], end.split(":")[0], end.split(":")[1], name, dryRun);
    }

    private String label(String dryRun) {
        return extract(totalsSource, "5:27", "5:36", "label", dryRun);
    }

    private static ChangedFiles changed(RequestHandler.Reply reply) {
        return ChangedFiles.of(((RpcResponse.Success) RpcResponse.parse(reply.line())).result());
    }

    private static RpcResponse.Failure refused(RequestHandler.Reply reply) {
        return (RpcResponse.Failure) RpcResponse.parse(reply.line());
    }

    @Test
    void isNamedAndTakesTheFileTheSelectionTheNameAndTheDryRun() {
        var method = new ExtractFieldMethod(new ExtractField(new FakeJavaLanguageServer(project), new NioFileStore()));

        assertThat(method.name()).isEqualTo("extract-field");
        assertThat(method.parameters())
                .containsExactlyInAnyOrder(
                        "file", "startLine", "startColumn", "endLine", "endColumn", "name", "dryRun");
    }

    @Test
    void extractsTheConstantOnDiskAndAnswersWhatChangedAndTheDiff() throws IOException {
        var handler = handler(Scenarios.EXTRACT_FIELD);

        var answer = changed(handler.handle(label("false")));

        assertThat(Files.readString(totalsSource))
                .contains("    private String label;\n")
                .contains("        label = \"count: \";\n");
        assertThat(answer.modified()).containsExactly(totalsSource);
        assertThat(answer.created()).isEmpty();
        assertThat(answer.renamed()).isEmpty();
        assertThat(answer.deleted()).isEmpty();
        assertThat(answer.diff())
                .contains("--- " + totalsSource, "+++ " + totalsSource)
                .contains("+    private String label;");
    }

    @Test
    void aDryRunAnswersWhatWouldChangeAndWritesNothing() throws IOException {
        var handler = handler(Scenarios.EXTRACT_FIELD);
        var totals = Files.readString(totalsSource);

        var answer = changed(handler.handle(label("true")));

        assertThat(answer.modified()).containsExactly(totalsSource);
        assertThat(answer.diff()).contains("+    private String label;");
        assertThat(Files.readString(totalsSource)).isEqualTo(totals);
    }

    @Test
    void aKeywordIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.EXTRACT_FIELD_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION);
        var greeter = project.resolve("src/main/java/demo/Greeter.java");
        var text = Files.readString(greeter);

        var failure = refused(handler.handle(extract(greeter, "7:8", "7:14", "label", "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-not-extractable");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(failure.problem().parameters())
                .containsEntry("line", "7")
                .containsEntry("column", "8")
                .containsEntry("endLine", "7")
                .containsEntry("endColumn", "14");
        assertThat(Files.readString(greeter)).isEqualTo(text);
    }

    @Test
    void aNameThatIsNotAnIdentifierIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.EXTRACT_FIELD_TO_AN_INVALID_NAME);
        var totals = Files.readString(totalsSource);

        var failure = refused(handler.handle(extract(totalsSource, "5:27", "5:36", "1st", "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-invalid-name");
        assertThat(failure.problem().parameters()).containsEntry("name", "1st");
        assertThat(Files.readString(totalsSource)).isEqualTo(totals);
    }

    @Test
    void aFileOutsideTheProjectIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.EXTRACT_FIELD_OUTSIDE_THE_PROJECT);
        var outside = project.resolve("../outside/Elsewhere.java").normalize();

        var failure = refused(handler.handle(extract(outside, "0:0", "0:1", "anything", "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-file-outside-project");
        assertThat(failure.problem().parameters()).containsEntry("file", outside.toString());
    }

    @Test
    void aRequestWithoutTheNameIsRefusedBeforeTheMethodIsCalled() throws IOException {
        var handler = handler(Scenarios.EXTRACT_FIELD);

        var failure = refused(handler.handle("""
            {"jsonrpc":"2.0","id":1,"method":"extract-field","params":\
            {"file":"%s","startLine":"5","startColumn":"27","endLine":"5","endColumn":"36","dryRun":"false"}}\
            """.formatted(totalsSource)));

        assertThat(failure.problem().key()).isEqualTo("missing-parameter");
        assertThat(failure.problem().parameters()).containsEntry("parameter", "name");
    }
}
