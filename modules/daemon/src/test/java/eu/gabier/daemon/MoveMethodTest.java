package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.editing.application.MoveToPackage;
import eu.gabier.editing.fixtures.FakeJavaLanguageServer;
import eu.gabier.editing.fixtures.PackageMoveScenarios;
import eu.gabier.editing.fixtures.Scenario;
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
 * The method {@code move} through the handler, against the fake language server scripted by the scenarios and the real
 * files of a temporary project: what is written, what is answered, and that a refusal writes nothing.
 */
class MoveMethodTest {

    private static final Path INVOICE = Path.of("src/main/java/demo/Invoice.java");
    private static final Path PAYMENT = Path.of("src/main/java/demo/Payment.java");
    private static final Path ARCHIVED = Path.of("src/main/java/demo/archive/Invoice.java");

    @TempDir
    Path project;

    private RequestHandler handler(Scenario scenario) throws IOException {
        scenario.writeProject(project);
        var move = new MoveToPackage(new FakeJavaLanguageServer(project).scripted(scenario), new NioFileStore());

        return new RequestHandler(List.of(new MoveMethod(move)));
    }

    private String move(String files, String target, String dryRun) {
        return """
            {"jsonrpc":"2.0","id":1,"method":"move","params":\
            {"files":"%s","package":"%s","dryRun":"%s"}}\
            """.formatted(files.replace("\n", "\\n"), target, dryRun);
    }

    private static ChangedFiles changed(RequestHandler.Reply reply) {
        return ChangedFiles.of(((RpcResponse.Success) RpcResponse.parse(reply.line())).result());
    }

    private static RpcResponse.Failure refused(RequestHandler.Reply reply) {
        return (RpcResponse.Failure) RpcResponse.parse(reply.line());
    }

    @Test
    void isNamedAndTakesTheFilesThePackageAndTheDryRun() {
        var method = new MoveMethod(new MoveToPackage(new FakeJavaLanguageServer(project), new NioFileStore()));

        assertThat(method.name()).isEqualTo("move");
        assertThat(method.parameters()).containsExactlyInAnyOrder("files", "package", "dryRun");
    }

    @Test
    void movesTheFileToANewPackageOnDiskAndAnswersWhatWasMovedAndChangedAndTheDiff() throws IOException {
        var handler = handler(PackageMoveScenarios.MOVE_A_CLASS_TO_A_NEW_PACKAGE);
        var invoice = project.resolve(INVOICE);
        var archived = project.resolve(ARCHIVED);

        var answer = changed(handler.handle(move(invoice.toString(), "demo.archive", "false")));

        assertThat(invoice).doesNotExist();
        assertThat(archived).content().startsWith("package demo.archive;\n");
        assertThat(answer.renamed()).containsExactly(new ChangedFiles.Moved(invoice, archived));
        assertThat(answer.created()).isEmpty();
        assertThat(answer.modified()).contains(project.resolve("src/main/java/demo/app/Shop.java"));
        assertThat(answer.diff()).contains("+import demo.archive.Invoice;");
    }

    @Test
    void movesSeveralFilesGivenOnePerLine() throws IOException {
        var handler = handler(PackageMoveScenarios.MOVE_SEVERAL_CLASSES_TO_AN_EXISTING_PACKAGE);
        var invoice = project.resolve(INVOICE);
        var payment = project.resolve(PAYMENT);

        var answer = changed(handler.handle(move(invoice + "\n" + payment, "demo.billing", "false")));

        assertThat(answer.renamed())
                .containsExactlyInAnyOrder(
                        new ChangedFiles.Moved(invoice, project.resolve("src/main/java/demo/billing/Invoice.java")),
                        new ChangedFiles.Moved(payment, project.resolve("src/main/java/demo/billing/Payment.java")));
        assertThat(project.resolve("src/main/java/demo/billing/Payment.java")).exists();
    }

    @Test
    void aDryRunAnswersWhatWouldChangeAndWritesNothingNotEvenTheFolder() throws IOException {
        var handler = handler(PackageMoveScenarios.MOVE_A_CLASS_TO_A_NEW_PACKAGE);
        var invoice = project.resolve(INVOICE);

        var answer = changed(handler.handle(move(invoice.toString(), "demo.archive", "true")));

        assertThat(answer.renamed()).containsExactly(new ChangedFiles.Moved(invoice, project.resolve(ARCHIVED)));
        assertThat(invoice).exists();
        assertThat(project.resolve("src/main/java/demo/archive")).doesNotExist();
    }

    @Test
    void aNameThatIsNotAPackageIsRefusedWithItsProblemObjectAndNothingIsAsked() throws IOException {
        var handler = handler(PackageMoveScenarios.MOVE_A_CLASS_TO_A_NEW_PACKAGE);
        var invoice = project.resolve(INVOICE);

        var failure = refused(handler.handle(move(invoice.toString(), "demo..archive", "false")));

        assertThat(failure.problem().key()).isEqualTo("editing-not-a-package-name");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(failure.problem().parameters()).containsEntry("package", "demo..archive");
        assertThat(invoice).exists();
    }

    @Test
    void aMoveTheServerRefusesIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(PackageMoveScenarios.MOVE_A_CLASS_TO_THE_PACKAGE_IT_IS_IN);
        var invoice = project.resolve(INVOICE);

        var failure = refused(handler.handle(move(invoice.toString(), "demo", "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-not-movable-to-package");
        assertThat(failure.problem().parameters())
                .containsEntry("files", invoice.toString())
                .containsEntry("package", "demo")
                .containsEntry("reason", "already-in-package");
        assertThat(invoice).exists();
    }

    @Test
    void aFileOutsideTheProjectIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(PackageMoveScenarios.MOVE_A_FILE_OUTSIDE_THE_PROJECT);
        var outside = project.resolve("../outside/Elsewhere.java").normalize();

        var failure = refused(handler.handle(move(outside.toString(), "demo.billing", "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-file-outside-project");
        assertThat(failure.problem().parameters()).containsEntry("file", outside.toString());
    }

    @Test
    void aDryRunThatIsNotAFlagIsRefusedNamingTheParameter() throws IOException {
        var handler = handler(PackageMoveScenarios.MOVE_A_CLASS_TO_A_NEW_PACKAGE);

        var failure = refused(handler.handle(move(project.resolve(INVOICE).toString(), "demo.archive", "maybe")));

        assertThat(failure.problem().key()).isEqualTo("invalid-parameter");
        assertThat(failure.problem().parameters()).containsEntry("parameter", "dryRun");
    }

    @Test
    void aRequestWithoutThePackageIsRefusedBeforeTheMethodIsCalled() throws IOException {
        var handler = handler(PackageMoveScenarios.MOVE_A_CLASS_TO_A_NEW_PACKAGE);

        var failure = refused(handler.handle("""
            {"jsonrpc":"2.0","id":1,"method":"move","params":{"files":"/x/A.java","dryRun":"false"}}\
            """));

        assertThat(failure.problem().key()).isEqualTo("missing-parameter");
        assertThat(failure.problem().parameters()).containsEntry("parameter", "package");
    }

    @Test
    void aFileNameThatIsNotAPathIsRefusedNamingTheParameter() throws IOException {
        var handler = handler(PackageMoveScenarios.MOVE_A_CLASS_TO_A_NEW_PACKAGE);

        var failure = refused(handler.handle(move("/x/\\u0000A.java", "demo.archive", "false")));

        assertThat(failure.problem().key()).isEqualTo("invalid-parameter");
        assertThat(failure.problem().parameters()).containsEntry("parameter", "files");
        assertThat(Files.exists(project.resolve(INVOICE))).isTrue();
    }
}
