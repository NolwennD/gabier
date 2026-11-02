package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.editing.application.CleanUpFile;
import eu.gabier.editing.fixtures.CleanUpScenarios;
import eu.gabier.editing.fixtures.FakeJavaLanguageServer;
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
 * The method {@code cleanup} through the handler, against the fake language server scripted by the scenarios and the
 * real files of a temporary project: what is written, what is answered, and that a refusal writes nothing.
 */
class CleanUpMethodTest {

    private static final Path BANNER = Path.of("src/main/java/demo/Banner.java");
    private static final Path PLAIN = Path.of("src/main/java/demo/Plain.java");
    private static final Path NOTICE = Path.of("src/main/java/demo/Notice.java");

    @TempDir
    Path project;

    private Path banner;

    private RequestHandler handler(Scenario... scenarios) throws IOException {
        var server = new FakeJavaLanguageServer(project);
        for (Scenario scenario : scenarios) {
            scenario.writeProject(project);
            server.scripted(scenario);
        }
        banner = project.resolve(BANNER);

        return new RequestHandler(List.of(new CleanUpMethod(new CleanUpFile(server, new NioFileStore()))));
    }

    private static String line(String params) {
        return """
            {"jsonrpc":"2.0","id":1,"method":"cleanup","params":%s}\
            """.formatted(params);
    }

    private static String cleanUp(Path file, String cleanUps, String dryRun) {
        return line("""
            {"file":"%s","cleanUps":"%s","dryRun":"%s"}\
            """.formatted(file, cleanUps, dryRun));
    }

    private static ChangedFiles changed(RequestHandler.Reply reply) {
        return ChangedFiles.of(((RpcResponse.Success) RpcResponse.parse(reply.line())).result());
    }

    private static RpcResponse.Failure refused(RequestHandler.Reply reply) {
        return (RpcResponse.Failure) RpcResponse.parse(reply.line());
    }

    @Test
    void isNamedAndTakesTheFileTheCleanUpsAndTheDryRun() throws IOException {
        var method = new CleanUpMethod(new CleanUpFile(new FakeJavaLanguageServer(project), new NioFileStore()));

        assertThat(method.name()).isEqualTo("cleanup");
        assertThat(method.parameters()).containsExactlyInAnyOrder("file", "cleanUps", "dryRun");
    }

    @Test
    void cleansTheFileOnDiskAndAnswersWhatChangedAndTheDiff() throws IOException {
        var handler = handler(CleanUpScenarios.CLEAN_UP_A_CONCATENATION_INTO_A_TEXT_BLOCK);

        var answer = changed(handler.handle(cleanUp(banner, "stringConcatToTextBlock", "false")));

        assertThat(Files.readString(banner)).contains("return \"\"\"").doesNotContain("+ \"");
        assertThat(answer.modified()).containsExactly(banner);
        assertThat(answer.created()).isEmpty();
        assertThat(answer.renamed()).isEmpty();
        assertThat(answer.deleted()).isEmpty();
        assertThat(answer.diff())
                .contains("--- " + banner, "+++ " + banner)
                .contains("+        return \"\"\"", "+            Hello, \"world\"!");
    }

    @Test
    void readsSeveralCleanUpsOnePerLineAndPassesThemInTheOrderGiven() throws IOException {
        var handler = handler(CleanUpScenarios.CLEAN_UP_WITH_TWO_CLEAN_UPS);
        var notice = project.resolve(NOTICE);

        var answer = changed(handler.handle(cleanUp(notice, "addFinalModifier\\nstringConcatToTextBlock", "false")));

        assertThat(answer.modified()).containsExactly(notice);
        assertThat(Files.readString(notice)).contains("final String title = \"\"\"");
    }

    @Test
    void aDryRunAnswersWhatWouldChangeAndWritesNothing() throws IOException {
        var handler = handler(CleanUpScenarios.CLEAN_UP_A_CONCATENATION_INTO_A_TEXT_BLOCK);
        var before = Files.readString(banner);

        var answer = changed(handler.handle(cleanUp(banner, "stringConcatToTextBlock", "true")));

        assertThat(answer.modified()).containsExactly(banner);
        assertThat(answer.diff()).contains("+        return \"\"\"");
        assertThat(Files.readString(banner)).isEqualTo(before);
    }

    @Test
    void aFileWithNothingToCleanAnswersNoChangeAndNoDiff() throws IOException {
        var handler = handler(CleanUpScenarios.CLEAN_UP_A_FILE_THAT_IS_CLEAN);
        var plain = project.resolve(PLAIN);
        var text = Files.readString(plain);

        var answer = changed(handler.handle(cleanUp(plain, "stringConcatToTextBlock", "false")));

        assertThat(answer).isEqualTo(new ChangedFiles(List.of(), List.of(), List.of(), List.of(), ""));
        assertThat(Files.readString(plain)).isEqualTo(text);
    }

    @Test
    void anUnknownCleanUpIsRefusedWithTheOnesTheServerKnowsAndNothingIsWritten() throws IOException {
        var handler = handler(CleanUpScenarios.CLEAN_UP_A_CONCATENATION_INTO_A_TEXT_BLOCK);
        var before = Files.readString(banner);

        var failure = refused(handler.handle(cleanUp(banner, "stringConcatToTextBlock\\ntextBlocks", "false")));

        assertThat(failure.problem().key()).isEqualTo("editing-unknown-clean-up");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(failure.problem().parameters())
                .containsEntry("cleanUp", "textBlocks")
                .hasEntrySatisfying("known", known -> assertThat(known).contains("stringConcatToTextBlock"));
        assertThat(Files.readString(banner)).isEqualTo(before);
    }

    @Test
    void aServerThatIsNotReadyIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(CleanUpScenarios.CLEAN_UP_WHILE_THE_SERVER_IMPORTS);

        var failure = refused(handler.handle(cleanUp(banner, "stringConcatToTextBlock", "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-not-ready");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
    }

    @Test
    void aFileOutsideTheProjectIsRefusedWithItsProblemObject() throws IOException {
        var handler = handler(CleanUpScenarios.CLEAN_UP_OUTSIDE_THE_PROJECT);
        var outside = project.resolve("../outside/Elsewhere.java").normalize();

        var failure = refused(handler.handle(cleanUp(outside, "stringConcatToTextBlock", "false")));

        assertThat(failure.problem().key()).isEqualTo("language-server-file-outside-project");
        assertThat(failure.problem().parameters()).containsEntry("file", outside.toString());
    }

    @Test
    void aRequestWithoutTheCleanUpsIsRefusedBeforeTheMethodIsCalled() throws IOException {
        var handler = handler(CleanUpScenarios.CLEAN_UP_A_CONCATENATION_INTO_A_TEXT_BLOCK);

        var failure = refused(handler.handle(line("""
            {"file":"%s","dryRun":"false"}\
            """.formatted(banner))));

        assertThat(failure.problem().key()).isEqualTo("missing-parameter");
        assertThat(failure.problem().parameters()).containsEntry("parameter", "cleanUps");
    }

    @Test
    void aValueThatMeansNothingIsRefusedAsAnInvalidParameterNamingIt() throws IOException {
        var handler = handler(CleanUpScenarios.CLEAN_UP_A_CONCATENATION_INTO_A_TEXT_BLOCK);
        List<String[]> requests = List.of(
                new String[] {
                    "dryRun", "{\"file\":\"%s\",\"cleanUps\":\"addOverride\",\"dryRun\":\"maybe\"}".formatted(banner)
                },
                new String[] {"cleanUps", "{\"file\":\"%s\",\"cleanUps\":\"\",\"dryRun\":\"false\"}".formatted(banner)},
                new String[] {"cleanUps", "{\"file\":\"%s\",\"cleanUps\":\"  \",\"dryRun\":\"false\"}".formatted(banner)
                },
                new String[] {"file", "{\"file\":\"a\\u0000b\",\"cleanUps\":\"addOverride\",\"dryRun\":\"false\"}"});

        for (var request : requests) {
            var reply = handler.handle(line(request[1]));

            var failure = refused(reply);
            assertThat(reply.line()).as(request[1]).contains("\"code\":-32602");
            assertThat(failure.problem().key()).as(request[1]).isEqualTo("invalid-parameter");
            assertThat(failure.problem().parameters()).as(request[1]).containsEntry("parameter", request[0]);
        }
    }
}
