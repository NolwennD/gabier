package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.editing.application.MoveTypeToFile;
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
 * The method {@code move-type-to-file} through the handler, against the fake language server scripted by the scenarios
 * and the real files of a temporary project: what is written, what is answered, and that a refusal writes nothing.
 */
class MoveTypeToFileMethodTest {

    private static final Path SHAPES = Path.of("src/main/java/demo/Shapes.java");
    private static final Path CIRCLE = Path.of("src/main/java/demo/Circle.java");

    @TempDir
    Path project;

    private Path shapesSource;

    private RequestHandler handler(Scenario scenario) throws IOException {
        scenario.writeProject(project);
        var move = new MoveTypeToFile(new FakeJavaLanguageServer(project).scripted(scenario), new NioFileStore());
        shapesSource = project.resolve(SHAPES);

        return new RequestHandler(List.of(new MoveTypeToFileMethod(move)));
    }

    private static String move(Path file, String line, String column, String dryRun) {
        return """
            {"jsonrpc":"2.0","id":1,"method":"move-type-to-file","params":\
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
        var method =
                new MoveTypeToFileMethod(new MoveTypeToFile(new FakeJavaLanguageServer(project), new NioFileStore()));

        assertThat(method.name()).isEqualTo("move-type-to-file");
        assertThat(method.parameters()).containsExactlyInAnyOrder("file", "line", "column", "dryRun");
    }

    @Test
    void movesTheTypeOnDiskAndAnswersWhatWasCreatedAndChangedAndTheDiff() throws IOException {
        var handler = handler(Scenarios.MOVE_A_STATIC_NESTED_TYPE);
        var circle = project.resolve(CIRCLE);

        var answer = changed(handler.handle(move(shapesSource, "5", "24", "false")));

        assertThat(circle).content().startsWith("package demo;\n\npublic class Circle {");
        assertThat(Files.readString(shapesSource)).doesNotContain("class Circle");
        assertThat(answer.created()).containsExactly(circle);
        assertThat(answer.modified()).contains(shapesSource, project.resolve("src/main/java/demo/app/Drawing.java"));
        assertThat(answer.renamed()).isEmpty();
        assertThat(answer.deleted()).isEmpty();
        assertThat(answer.diff())
                .contains("--- " + shapesSource, "+++ " + circle)
                .contains("+public class Circle {");
    }

    @Test
    void aDryRunAnswersWhatWouldChangeAndWritesNothing() throws IOException {
        var handler = handler(Scenarios.MOVE_A_STATIC_NESTED_TYPE);
        var shapes = Files.readString(shapesSource);

        var answer = changed(handler.handle(move(shapesSource, "5", "24", "true")));

        assertThat(answer.created()).containsExactly(project.resolve(CIRCLE));
        assertThat(answer.diff()).contains("+public class Circle {");
        assertThat(Files.readString(shapesSource)).isEqualTo(shapes);
        assertThat(project.resolve(CIRCLE)).doesNotExist();
    }

    @Test
    void aTypeThatCannotBeMovedIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.MOVE_A_TOP_LEVEL_TYPE);
        var shapes = Files.readString(shapesSource);

        var failure = refused(handler.handle(move(shapesSource, "2", "13", "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-not-movable");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(failure.problem().parameters())
                .containsEntry("file", shapesSource.toString())
                .containsEntry("line", "2")
                .containsEntry("column", "13");
        assertThat(Files.readString(shapesSource)).isEqualTo(shapes);
    }

    @Test
    void aFileOutsideTheProjectIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.MOVE_A_TYPE_OUTSIDE_THE_PROJECT);
        var outside = project.resolve("../outside/Elsewhere.java").normalize();

        var failure = refused(handler.handle(move(outside, "0", "0", "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-file-outside-project");
        assertThat(failure.problem().parameters()).containsEntry("file", outside.toString());
    }

    @Test
    void aPositionThatIsNotANumberIsRefusedNamingTheParameter() throws IOException {
        var handler = handler(Scenarios.MOVE_A_STATIC_NESTED_TYPE);

        var failure = refused(handler.handle(move(shapesSource, "five", "24", "false")));

        assertThat(failure.problem().key()).isEqualTo("invalid-parameter");
        assertThat(failure.problem().parameters()).containsEntry("parameter", "line");
    }

    @Test
    void aRequestWithoutThePositionIsRefusedBeforeTheMethodIsCalled() throws IOException {
        var handler = handler(Scenarios.MOVE_A_STATIC_NESTED_TYPE);

        var failure = refused(handler.handle("""
            {"jsonrpc":"2.0","id":1,"method":"move-type-to-file","params":\
            {"file":"%s","line":"5","dryRun":"false"}}\
            """.formatted(shapesSource)));

        assertThat(failure.problem().key()).isEqualTo("missing-parameter");
        assertThat(failure.problem().parameters()).containsEntry("parameter", "column");
    }
}
