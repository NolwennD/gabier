package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.editing.application.CreateType;
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
 * The method {@code create} through the handler, against the fake language server scripted by the scenarios and the real
 * files of a temporary project: what is written, what is answered, and that a refusal writes nothing.
 */
class CreateTypeMethodTest {

    @TempDir
    Path project;

    private Path widget;

    private RequestHandler handler(Scenario scenario) throws IOException {
        scenario.writeProject(project);
        var create = new CreateType(new FakeJavaLanguageServer(project).scripted(scenario), new NioFileStore());
        widget = project.resolve("src/main/java/demo/Widget.java");

        return new RequestHandler(List.of(new CreateTypeMethod(create)));
    }

    private static String create(Path file, String kind, String dryRun) {
        return """
            {"jsonrpc":"2.0","id":1,"method":"create","params":\
            {"file":"%s","kind":"%s","dryRun":"%s"}}\
            """.formatted(file, kind, dryRun);
    }

    private static ChangedFiles changed(RequestHandler.Reply reply) {
        return ChangedFiles.of(((RpcResponse.Success) RpcResponse.parse(reply.line())).result());
    }

    private static RpcResponse.Failure refused(RequestHandler.Reply reply) {
        return (RpcResponse.Failure) RpcResponse.parse(reply.line());
    }

    @Test
    void isNamedAndTakesTheFileTheKindAndTheDryRun() {
        var method = new CreateTypeMethod(new CreateType(new FakeJavaLanguageServer(project), new NioFileStore()));

        assertThat(method.name()).isEqualTo("create");
        assertThat(method.parameters()).containsExactlyInAnyOrder("file", "kind", "dryRun");
    }

    @Test
    void createsTheFileOnDiskAndAnswersWhatWasCreatedAndTheDiff() throws IOException {
        var handler = handler(Scenarios.CREATE_A_CLASS);

        var answer = changed(handler.handle(create(widget, "class", "false")));

        assertThat(widget).content().isEqualTo("package demo;\n\npublic class Widget {\n\n}\n");
        assertThat(answer.created()).containsExactly(widget);
        assertThat(answer.modified()).isEmpty();
        assertThat(answer.renamed()).isEmpty();
        assertThat(answer.deleted()).isEmpty();
        assertThat(answer.diff()).contains("--- /dev/null\n+++ " + widget).contains("+public class Widget {");
    }

    @Test
    void aDryRunAnswersWhatWouldBeCreatedAndWritesNothing() throws IOException {
        var handler = handler(Scenarios.CREATE_A_CLASS);

        var answer = changed(handler.handle(create(widget, "class", "true")));

        assertThat(answer.created()).containsExactly(widget);
        assertThat(answer.diff()).contains("+public class Widget {");
        assertThat(widget).doesNotExist();
    }

    @Test
    void aKindThatIsNotOneOfTheFourIsRefusedNamingTheParameter() throws IOException {
        var handler = handler(Scenarios.CREATE_A_CLASS);

        for (String kind : List.of("annotation", "Class", "")) {
            var failure = refused(handler.handle(create(widget, kind, "false")));

            assertThat(failure.problem().key()).as(kind).isEqualTo("invalid-parameter");
            assertThat(failure.problem().parameters()).containsEntry("parameter", "kind");
        }
        assertThat(widget).doesNotExist();
    }

    @Test
    void aRequestWithoutTheKindIsRefusedBeforeTheMethodIsCalled() throws IOException {
        var handler = handler(Scenarios.CREATE_A_CLASS);

        var failure = refused(handler.handle("""
            {"jsonrpc":"2.0","id":1,"method":"create","params":{"file":"%s","dryRun":"false"}}\
            """.formatted(widget)));

        assertThat(failure.problem().key()).isEqualTo("missing-parameter");
        assertThat(failure.problem().parameters()).containsEntry("parameter", "kind");
    }

    @Test
    void aFileThatExistsIsRefusedAndLeftAsItWas() throws IOException {
        var handler = handler(Scenarios.CREATE_A_CLASS);
        Files.writeString(widget, "package demo;\n\nclass Widget { int size; }\n");

        var failure = refused(handler.handle(create(widget, "class", "false")));

        assertThat(failure.problem().key()).isEqualTo("editing-file-exists");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(failure.problem().parameters()).containsEntry("file", widget.toString());
        assertThat(widget).content().isEqualTo("package demo;\n\nclass Widget { int size; }\n");
    }

    @Test
    void aFileWhoseNameIsNotATypeNameIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.CREATE_A_CLASS);
        var badName = project.resolve("src/main/java/demo/widget-impl.java");

        var failure = refused(handler.handle(create(badName, "class", "false")));

        assertThat(failure.problem().key()).isEqualTo("editing-not-a-type-name");
        assertThat(failure.problem().parameters()).containsEntry("file", badName.toString());
        assertThat(badName).doesNotExist();
    }

    @Test
    void aFileOutsideTheProjectIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.CREATE_A_TYPE_OUTSIDE_THE_PROJECT);
        var outside = project.resolve("../outside/Elsewhere.java").normalize();

        var failure = refused(handler.handle(create(outside, "class", "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-file-outside-project");
        assertThat(failure.problem().parameters()).containsEntry("file", outside.toString());
    }

    @Test
    void aFileOutsideTheSourceFoldersIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(Scenarios.CREATE_A_TYPE_OUTSIDE_THE_SOURCE_FOLDERS);
        var unsourced = project.resolve("Unsourced.java");

        var failure = refused(handler.handle(create(unsourced, "class", "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-not-creatable");
        assertThat(failure.problem().parameters())
                .containsEntry("file", unsourced.toString())
                .containsEntry("kind", "class");
        assertThat(unsourced).doesNotExist();
    }

    @Test
    void aFolderThatDoesNotExistIsRefusedAndNotCreated() throws IOException {
        var handler = handler(Scenarios.CREATE_A_TYPE_IN_A_NEW_PACKAGE);
        var gadget = project.resolve("src/main/java/fresh/Gadget.java");

        var failure = refused(handler.handle(create(gadget, "class", "false")));

        assertThat(failure.problem().key()).isEqualTo("editing-folder-missing");
        assertThat(gadget.getParent()).doesNotExist();
    }
}
