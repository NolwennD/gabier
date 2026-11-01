package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.editing.application.Inline;
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
 * The method {@code inline} through the handler, against the fake language server scripted by the scenarios and the
 * real files of a temporary project: what is written, what is answered, and that a refusal writes nothing.
 */
class InlineMethodTest {

    private static final Path PRICES = Path.of("src/main/java/demo/Prices.java");
    private static final Path CHECKOUT = Path.of("src/main/java/demo/Checkout.java");

    @TempDir
    Path project;

    private Path pricesSource;

    private RequestHandler handler(Scenario scenario) throws IOException {
        scenario.writeProject(project);
        var inline = new Inline(new FakeJavaLanguageServer(project).scripted(scenario), new NioFileStore());
        pricesSource = project.resolve(PRICES);

        return new RequestHandler(List.of(new InlineMethod(inline)));
    }

    private static String inline(Path file, String line, String column, String dryRun) {
        return """
            {"jsonrpc":"2.0","id":1,"method":"inline","params":\
            {"file":"%s","line":"%s","column":"%s","dryRun":"%s"}}\
            """.formatted(file, line, column, dryRun);
    }

    private static ChangedFiles changed(RequestHandler.Reply reply) {
        return ChangedFiles.of(((RpcResponse.Success) RpcResponse.parse(reply.line())).result());
    }

    private static RpcResponse.Failure refused(RequestHandler.Reply reply) {
        return (RpcResponse.Failure) RpcResponse.parse(reply.line());
    }

    @Test
    void isNamedAndTakesTheFileThePositionAndTheDryRun() {
        var method = new InlineMethod(new Inline(new FakeJavaLanguageServer(project), new NioFileStore()));

        assertThat(method.name()).isEqualTo("inline");
        assertThat(method.parameters()).containsExactlyInAnyOrder("file", "line", "column", "dryRun");
    }

    @Test
    void inlinesTheConstantOnDiskAndAnswersWhatWasChangedAndTheDiff() throws IOException {
        var handler = handler(Scenarios.INLINE_A_CONSTANT);
        var checkout = project.resolve(CHECKOUT);

        var answer = changed(handler.handle(inline(pricesSource, "3", "21", "false")));

        assertThat(Files.readString(pricesSource)).doesNotContain("VAT");
        assertThat(Files.readString(checkout)).doesNotContain("VAT");
        assertThat(answer.modified()).containsExactlyInAnyOrder(pricesSource, checkout);
        assertThat(answer.created()).isEmpty();
        assertThat(answer.renamed()).isEmpty();
        assertThat(answer.deleted()).isEmpty();
        assertThat(answer.diff()).contains("--- " + pricesSource, "+++ " + checkout, "-    static final int VAT = 20;");
    }

    @Test
    void aDryRunAnswersWhatWouldChangeAndWritesNothing() throws IOException {
        var handler = handler(Scenarios.INLINE_A_CONSTANT);
        var prices = Files.readString(pricesSource);

        var answer = changed(handler.handle(inline(pricesSource, "3", "21", "true")));

        assertThat(answer.modified()).contains(pricesSource);
        assertThat(answer.diff()).contains("-    static final int VAT = 20;");
        assertThat(Files.readString(pricesSource)).isEqualTo(prices);
    }

    @Test
    void aPositionThatCannotBeInlinedIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.INLINE_A_VARIABLE_ASSIGNED_TWICE);
        var prices = Files.readString(pricesSource);

        var failure = refused(handler.handle(inline(pricesSource, "20", "12", "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-not-inlinable");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(failure.problem().parameters())
                .containsEntry("file", pricesSource.toString())
                .containsEntry("line", "20")
                .containsEntry("column", "12");
        assertThat(Files.readString(pricesSource)).isEqualTo(prices);
    }

    @Test
    void aFileOutsideTheProjectIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.INLINE_OUTSIDE_THE_PROJECT);
        var outside = project.resolve("../outside/Elsewhere.java").normalize();

        var failure = refused(handler.handle(inline(outside, "0", "0", "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-file-outside-project");
        assertThat(failure.problem().parameters()).containsEntry("file", outside.toString());
    }

    @Test
    void aPositionThatIsNotANumberIsRefusedNamingTheParameter() throws IOException {
        var handler = handler(Scenarios.INLINE_A_CONSTANT);

        var failure = refused(handler.handle(inline(pricesSource, "three", "21", "false")));

        assertThat(failure.problem().key()).isEqualTo("invalid-parameter");
        assertThat(failure.problem().parameters()).containsEntry("parameter", "line");
    }

    @Test
    void aRequestWithoutThePositionIsRefusedBeforeTheMethodIsCalled() throws IOException {
        var handler = handler(Scenarios.INLINE_A_CONSTANT);

        var failure = refused(handler.handle("""
            {"jsonrpc":"2.0","id":1,"method":"inline","params":\
            {"file":"%s","line":"3","dryRun":"false"}}\
            """.formatted(pricesSource)));

        assertThat(failure.problem().key()).isEqualTo("missing-parameter");
        assertThat(failure.problem().parameters()).containsEntry("parameter", "column");
    }
}
