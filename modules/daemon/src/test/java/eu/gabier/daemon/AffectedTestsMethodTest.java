package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.protocol.RpcResponse;
import eu.gabier.protocol.SelectedTests;
import eu.gabier.shared.error.domain.ErrorStatus;
import eu.gabier.testing.application.AffectedTests;
import eu.gabier.testing.domain.BuiltProject;
import eu.gabier.testing.domain.ChangedBuild;
import eu.gabier.testing.domain.ProjectBuild;
import eu.gabier.testing.fixtures.AffectedTestsCase;
import eu.gabier.testing.fixtures.AffectedTestsCases;
import eu.gabier.testing.fixtures.BuildScenario;
import eu.gabier.testing.fixtures.CompiledProject;
import eu.gabier.testing.fixtures.FakeProjectBuild;
import eu.gabier.testing.infrastructure.secondary.NioProjectFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The method {@code affected-tests} through the handler, against the shop of the contract scenarios. */
class AffectedTestsMethodTest {

    @TempDir
    Path project;

    private BuiltProject built;
    private FakeProjectBuild build;

    private RequestHandler handler(ProjectBuild build) {
        return new RequestHandler(
                List.of(new AffectedTestsMethod(new AffectedTests(build, new NioProjectFiles(), project))));
    }

    private RequestHandler handlerOfTheShop() throws IOException {
        for (Map.Entry<Path, String> other : AffectedTestsCases.otherFiles().entrySet()) {
            Files.writeString(project.resolve(other.getKey()), other.getValue());
        }
        built = CompiledProject.of(project, AffectedTestsCases.mainSources(), AffectedTestsCases.testSources());

        build = new FakeProjectBuild(project).scripted(new BuildScenario("shop", Map.of(), built));

        return handler(build);
    }

    private static String ask(RequestHandler handler, String files) {
        return handler.handle("""
            {"jsonrpc":"2.0","id":1,"method":"affected-tests","params":{"files":"%s"}}\
            """.formatted(files)).line();
    }

    private String joined(List<Path> relative) {
        return relative.stream().map(project::resolve).map(Path::toString).collect(Collectors.joining("\\n"));
    }

    private static RpcResponse.Failure refusal(String line) {
        return (RpcResponse.Failure) RpcResponse.parse(line);
    }

    @Test
    void isNamedAffectedTestsAndTakesTheFiles() {
        var method = new AffectedTestsMethod(
                new AffectedTests(new FakeProjectBuild(project), new NioProjectFiles(), project));

        assertThat(method.name()).isEqualTo("affected-tests");
        assertThat(method.parameters()).containsExactly("files");
    }

    @Test
    void answersTheTestsOfEveryCaseOfTheContractScenarios() throws IOException {
        var handler = handlerOfTheShop();

        for (AffectedTestsCase scenario : AffectedTestsCases.all()) {
            if (!scenario.edits().isEmpty()) {
                continue;
            }
            var response = (RpcResponse.Success) RpcResponse.parse(ask(handler, joined(scenario.changed())));

            assertThat(SelectedTests.of(response.result()).tests())
                    .as(scenario.name())
                    .containsExactlyElementsOf(scenario.affected());
        }
    }

    @Test
    void answersTheTestsOfAllTheFilesGivenTogether() throws IOException {
        var handler = handlerOfTheShop();

        var response = (RpcResponse.Success) RpcResponse.parse(ask(
                handler,
                joined(List.of(
                        Path.of("src/main/java/shop/Repository.java"),
                        Path.of("src/test/java/shop/AbstractGreeterTest.java")))));

        assertThat(SelectedTests.of(response.result()).tests())
                .containsExactly(
                        "shop.ArchitectureTest",
                        "shop.FrenchGreeterTest",
                        "shop.LegacyTest",
                        "shop.NgTest",
                        "shop.ServiceTest");
    }

    @Test
    void cleansADotAndADoubleDotSegmentOfTheFilesItGets() throws IOException {
        var handler = handlerOfTheShop();

        var response = (RpcResponse.Success)
                RpcResponse.parse(ask(handler, project + "/./src/main/java/other/../shop/Unused.java"));

        assertThat(SelectedTests.of(response.result()).tests()).containsExactly("shop.ArchitectureTest");
    }

    @Test
    void doesNothingAfterTheReply() throws IOException {
        var handler = handlerOfTheShop();

        handler.handle("""
            {"jsonrpc":"2.0","id":1,"method":"affected-tests","params":{"files":"%s"}}\
            """.formatted(project.resolve("README.md"))).afterReply().run();
    }

    @Test
    void aFileThatDoesNotExistRefusesTheWholeCallWithItsProblemObject() throws IOException {
        var gone = project.resolve("src/main/java/shop/Gone.java");

        var failure = refusal(ask(handlerOfTheShop(), gone.toString()));

        assertThat(failure.problem().key()).isEqualTo("testing-file-missing");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(failure.problem().parameters()).containsEntry("file", gone.toString());
    }

    @Test
    void aFileOutsideTheSourceFoldersIsRefusedWithItsProblemObject() throws IOException {
        var handler = handlerOfTheShop();
        var elsewhere = Files.writeString(project.resolve("Elsewhere.java"), "class Elsewhere {}");

        var failure = refusal(ask(handler, elsewhere.toString()));

        assertThat(failure.problem().key()).isEqualTo("testing-not-in-a-source-folder");
        assertThat(failure.problem().parameters()).containsEntry("file", elsewhere.toString());
    }

    @Test
    void aFileThatDoesNotCompileIsRefusedWithItsProblemObject() throws IOException {
        var broken = project.resolve("src/main/java/shop/Greeter.java");
        var handler = handlerOfTheShop();
        built = new BuiltProject(built.folders(), built.libraries(), List.of(broken));
        build.scripted(new BuildScenario("shop-with-an-error", Map.of(), built));

        var failure = refusal(ask(handler, broken.toString()));

        assertThat(failure.problem().key()).isEqualTo("testing-compilation-failed");
        assertThat(failure.problem().parameters()).containsEntry("files", broken.toString());
    }

    @Test
    void aServerThatIsNotReadyIsRefusedWithItsProblemObject() {
        var handler = handler(new ProjectBuild() {
            @Override
            public BuiltProject built() {
                throw LanguageServerException.notReady();
            }

            @Override
            public ChangedBuild builtWithChanges() {
                throw LanguageServerException.notReady();
            }
        });

        var failure = refusal(
                ask(handler, project.resolve("src/main/java/shop/Greeter.java").toString()));

        assertThat(failure.problem().key()).isEqualTo("language-server-not-ready");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
    }

    @Test
    void noFileAtAllABlankFileAndARelativeFileAreRefusedAsInvalidParameters() throws IOException {
        var handler = handlerOfTheShop();

        for (var files : List.of("", "a\\n\\nb", " ", "src/main/java/shop/Greeter.java")) {
            var failure = refusal(ask(handler, files));

            assertThat(failure.problem().key()).as(files).isEqualTo("invalid-parameter");
            assertThat(failure.problem().parameters()).containsEntry("parameter", "files");
        }
    }

    @Test
    void aFileThatIsNotAPathIsRefusedAsAnInvalidParameter() throws IOException {
        var failure = refusal(ask(handlerOfTheShop(), "a\\u0000b"));

        assertThat(failure.problem().key()).isEqualTo("invalid-parameter");
        assertThat(failure.problem().parameters()).containsEntry("parameter", "files");
    }

    @Test
    void aRequestWithoutTheFilesIsRefusedBeforeTheMethodIsCalled() throws IOException {
        var line = handlerOfTheShop()
                .handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"affected-tests\"}")
                .line();

        var failure = refusal(line);
        assertThat(failure.problem().key()).isEqualTo("missing-parameter");
        assertThat(failure.problem().parameters()).containsEntry("parameter", "files");
    }
}
