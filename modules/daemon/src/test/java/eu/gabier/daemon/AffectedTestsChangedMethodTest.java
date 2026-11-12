package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.RpcResponse;
import eu.gabier.protocol.SelectedTests;
import eu.gabier.shared.error.domain.ErrorStatus;
import eu.gabier.testing.application.AffectedTests;
import eu.gabier.testing.domain.BuiltProject;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The method {@code affected-tests-changed} through the handler, against the shop of the contract scenarios: the
 * tests that what changed since the last answer affects, with no file given.
 */
class AffectedTestsChangedMethodTest {

    private static final String REQUEST = """
        {"jsonrpc":"2.0","id":1,"method":"affected-tests-changed","params":{}}\
        """;

    @TempDir
    Path project;

    private BuiltProject built;
    private FakeProjectBuild build;
    private RequestHandler handler;

    private void servingTheShop(List<Path> filesWithErrors) throws IOException {
        for (Map.Entry<Path, String> other : AffectedTestsCases.otherFiles().entrySet()) {
            Files.writeString(project.resolve(other.getKey()), other.getValue());
        }
        built = CompiledProject.of(
                project, AffectedTestsCases.mainSources(), AffectedTestsCases.testSources(), filesWithErrors);
        build = new FakeProjectBuild(project).scripted(new BuildScenario("shop", Map.of(), built));
        handler = new RequestHandler(
                List.of(new AffectedTestsChangedMethod(new AffectedTests(build, new NioProjectFiles(), project))));
    }

    private List<String> asked() {
        var response =
                (RpcResponse.Success) RpcResponse.parse(handler.handle(REQUEST).line());

        return SelectedTests.of(response.result()).tests();
    }

    @Test
    void isNamedAffectedTestsChangedAndTakesNoParameter() throws IOException {
        servingTheShop(List.of());

        var method = new AffectedTestsChangedMethod(new AffectedTests(build, new NioProjectFiles(), project));

        assertThat(method.name()).isEqualTo("affected-tests-changed");
        assertThat(method.parameters()).isEmpty();
    }

    @Test
    void answersNoTestWhenNothingChanged() throws IOException {
        servingTheShop(List.of());

        assertThat(asked()).isEmpty();
    }

    @Test
    void answersTheTestsOfWhatChangedAndThenNoTestUntilAnotherChange() throws IOException {
        servingTheShop(List.of());
        build.edited(Path.of("src/main/java/shop/Repository.java"));

        assertThat(asked())
                .containsExactly("shop.ArchitectureTest", "shop.LegacyTest", "shop.NgTest", "shop.ServiceTest");
        assertThat(asked()).isEmpty();

        build.edited(Path.of("src/test/java/shop/ServiceTest.java"));

        assertThat(asked()).containsExactly("shop.ArchitectureTest", "shop.ServiceTest");
    }

    @Test
    void answersTheChangesToEveryClientOfTheSameDaemonOnlyOnce() throws IOException {
        servingTheShop(List.of());
        build.edited(Path.of("src/main/java/shop/Unused.java"));

        var first = asked();
        var second = asked();

        assertThat(first).containsExactly("shop.ArchitectureTest");
        assertThat(second).isEmpty();
    }

    @Test
    void aFileThatDoesNotCompileRefusesTheCallWithItsProblemObjectAndKeepsTheChanges() throws IOException {
        var greeter = project.resolve("src/main/java/shop/Greeter.java");
        servingTheShop(List.of(greeter));
        build.edited(Path.of("src/main/java/shop/Greeter.java"));

        var failure =
                (RpcResponse.Failure) RpcResponse.parse(handler.handle(REQUEST).line());

        assertThat(failure.problem().key()).isEqualTo("testing-compilation-failed");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(failure.problem().parameters()).containsEntry("files", greeter.toString());
        assertThat(build.builtWithChanges().sources()).containsExactly(greeter);
    }

    @Test
    void refusesAParameterAsItTakesNone() throws IOException {
        servingTheShop(List.of());

        var failure =
                (RpcResponse.Failure) RpcResponse.parse(handler.handle("""
                    {"jsonrpc":"2.0","id":1,"method":"affected-tests-changed","params":{"files":"/project/A.java"}}\
                    """).line());

        assertThat(failure.problem().key()).isEqualTo("unknown-parameter");
    }

    @Test
    void doesNothingAfterTheReply() throws IOException {
        servingTheShop(List.of());

        handler.handle(REQUEST).afterReply().run();
    }
}
