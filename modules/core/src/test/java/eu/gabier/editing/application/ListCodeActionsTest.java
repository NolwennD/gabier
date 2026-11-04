package eu.gabier.editing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.OfferedAction;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.Range;
import eu.gabier.editing.fixtures.CodeActionScenarios;
import eu.gabier.editing.fixtures.FakeJavaLanguageServer;
import eu.gabier.editing.fixtures.Scenario;
import eu.gabier.editing.fixtures.Scenario.Request;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ListCodeActionsTest {

    private static final String OFFERS = "src/main/java/demo/Offers.java";
    private static final Range THE_TYPE = new Range(new Position(3, 4), new Position(3, 8));
    private static final Range A_BLANK_LINE = new Range(new Position(6, 0), new Position(6, 0));

    @TempDir
    Path root;

    private FakeJavaLanguageServer server;
    private ListCodeActions list;

    private void project(Scenario... scenarios) throws IOException {
        server = new FakeJavaLanguageServer(root);
        for (Scenario scenario : scenarios) {
            scenario.writeProject(root);
            server.scripted(scenario);
        }
        list = new ListCodeActions(server);
    }

    @Test
    void listsTheFixOfAnErrorWithItsKindAndItsTitle() throws IOException {
        project(CodeActionScenarios.CODE_ACTIONS_ON_A_TYPE_THAT_IS_NOT_IMPORTED);

        assertThat(list.list(root.resolve(OFFERS), THE_TYPE).actions())
                .containsExactly(new OfferedAction("quickfix", "Import 'List' (java.util)"));
    }

    @Test
    void listsTheRewriteOfACodeThatIsNotAnError() throws IOException {
        project(CodeActionScenarios.CODE_ACTIONS_ON_A_CONCATENATION);
        Path banner = root.resolve("src/main/java/demo/Banner.java");

        assertThat(list.list(banner, new Range(new Position(4, 15), new Position(6, 26)))
                        .actions())
                .containsExactly(
                        new OfferedAction("refactor.extract.variable", "Extract to local variable"),
                        new OfferedAction("quickassist", "Convert String concatenation to Text Block"));
    }

    @Test
    void namesTheCommandOfGabierForTheExtractionAndNoneForTheRewriteOrTheFix() throws IOException {
        project(CodeActionScenarios.CODE_ACTIONS_ON_A_CONCATENATION);
        Path banner = root.resolve("src/main/java/demo/Banner.java");

        assertThat(list.list(banner, new Range(new Position(4, 15), new Position(6, 26)))
                        .actions())
                .extracting(action -> action.command().orElse("-"))
                .containsExactly("extract-variable", "-");
    }

    @Test
    void answersAnEmptyListNotAnErrorWhereNothingIsOffered() throws IOException {
        project(CodeActionScenarios.CODE_ACTIONS_WHERE_NOTHING_IS_OFFERED);

        assertThat(list.list(root.resolve(OFFERS), A_BLANK_LINE).actions()).isEmpty();
    }

    @Test
    void asksTheServerOnceForTheFileAndTheRangeAsGiven() throws IOException {
        project(CodeActionScenarios.CODE_ACTIONS_ON_A_TYPE_THAT_IS_NOT_IMPORTED);

        list.list(root.resolve(OFFERS), THE_TYPE);

        assertThat(server.asked()).containsExactly(new Request.CodeActions(root.resolve(OFFERS), THE_TYPE));
    }

    @Test
    void reportsAServerThatIsNotReadyAndAFileOutsideTheProject() throws IOException {
        project(
                CodeActionScenarios.CODE_ACTIONS_WHILE_THE_SERVER_IMPORTS,
                CodeActionScenarios.CODE_ACTIONS_OUTSIDE_THE_PROJECT);

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> list.list(root.resolve(OFFERS), THE_TYPE))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> list.list(root.getParent().resolve("outside/Elsewhere.java"), THE_TYPE))
                .satisfies(exception ->
                        assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));
    }

    @Test
    void rejectsAMissingServerAndMissingArguments() throws IOException {
        project(CodeActionScenarios.CODE_ACTIONS_ON_A_TYPE_THAT_IS_NOT_IMPORTED);

        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new ListCodeActions(null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("server"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> list.list(null, THE_TYPE))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("file"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> list.list(root.resolve(OFFERS), null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("range"));
    }
}
