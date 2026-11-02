package eu.gabier.editing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.editing.domain.ChangeReport;
import eu.gabier.editing.domain.EditingErrorKey;
import eu.gabier.editing.domain.EditingException;
import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.OfferedAction;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.Range;
import eu.gabier.editing.fixtures.CodeActionScenarios;
import eu.gabier.editing.fixtures.FakeJavaLanguageServer;
import eu.gabier.editing.fixtures.Scenario;
import eu.gabier.editing.fixtures.Scenario.Request;
import eu.gabier.editing.infrastructure.secondary.NioFileStore;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApplyCodeActionTest {

    private static final String OFFERS = "src/main/java/demo/Offers.java";
    private static final String BANNER = "src/main/java/demo/Banner.java";
    private static final String IMPORT = "Import 'List' (java.util)";
    private static final String TEXT_BLOCK = "Convert String concatenation to Text Block";
    private static final Range THE_TYPE = new Range(new Position(3, 4), new Position(3, 8));
    private static final Range THE_NAME = new Range(new Position(3, 17), new Position(3, 22));
    private static final Range THE_CONCATENATION = new Range(new Position(4, 15), new Position(6, 26));

    @TempDir
    Path root;

    private FakeJavaLanguageServer server;
    private ApplyCodeAction apply;

    /** Writes the project of the scenarios and plays them with a fake scripted by them. */
    private void project(Scenario... scenarios) throws IOException {
        server = new FakeJavaLanguageServer(root);
        for (Scenario scenario : scenarios) {
            scenario.writeProject(root);
            server.scripted(scenario);
        }
        apply = new ApplyCodeAction(server, new NioFileStore());
    }

    private Path path(String relative) {
        return root.resolve(relative);
    }

    private String text(String relative) throws IOException {
        return Files.readString(path(relative));
    }

    /** Every path under the root, with the text of its files: what "nothing was written" means. */
    private Map<String, String> snapshot() throws IOException {
        Map<String, String> snapshot = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                snapshot.put(
                        root.relativize(path).toString(),
                        Files.isDirectory(path) ? "<directory>" : Files.readString(path));
            }
        }

        return snapshot;
    }

    private void projectOfTheImport() throws IOException {
        project(
                CodeActionScenarios.CODE_ACTIONS_ON_A_TYPE_THAT_IS_NOT_IMPORTED,
                CodeActionScenarios.APPLY_THE_IMPORT_OF_A_TYPE);
    }

    @Test
    void appliesTheFixThatHasTheTitleAndWritesTheFile() throws IOException {
        projectOfTheImport();

        ChangeReport report = apply.apply(path(OFFERS), THE_TYPE, IMPORT);

        assertThat(text(OFFERS)).startsWith("package demo;\n\nimport java.util.List;\n\npublic class Offers");
        assertThat(report.modified()).containsExactly(path(OFFERS));
        assertThat(report.created()).isEmpty();
        assertThat(report.deleted()).isEmpty();
    }

    @Test
    void appliesTheRewriteOfACodeThatIsNotAnError() throws IOException {
        project(
                CodeActionScenarios.CODE_ACTIONS_ON_A_CONCATENATION,
                CodeActionScenarios.APPLY_A_REWRITE_OF_A_CONCATENATION);

        apply.apply(path(BANNER), THE_CONCATENATION, TEXT_BLOCK);

        assertThat(text(BANNER)).contains("return \"\"\"\n            Hello, \"world\"!\n");
    }

    @Test
    void asksWhatTheRangeOffersThenTheEditOfTheActionThatHasTheTitle() throws IOException {
        projectOfTheImport();

        apply.apply(path(OFFERS), THE_TYPE, IMPORT);

        assertThat(server.asked())
                .containsExactly(
                        new Request.CodeActions(path(OFFERS), THE_TYPE),
                        new Request.ApplyCodeAction(path(OFFERS), THE_TYPE, new OfferedAction("quickfix", IMPORT)));
    }

    @Test
    void writesNothingOnADryRunAndGivesTheDiff() throws IOException {
        projectOfTheImport();
        Map<String, String> before = snapshot();

        ChangeReport report = apply.dryRun(path(OFFERS), THE_TYPE, IMPORT);

        assertThat(snapshot()).isEqualTo(before);
        assertThat(report.modified()).containsExactly(path(OFFERS));
        assertThat(report.diff()).contains("+++ " + path(OFFERS)).contains("+import java.util.List;");
    }

    @Test
    void givesTheSameReportOnADryRunAndOnTheRealRun() throws IOException {
        projectOfTheImport();

        ChangeReport planned = apply.dryRun(path(OFFERS), THE_TYPE, IMPORT);
        ChangeReport done = apply.apply(path(OFFERS), THE_TYPE, IMPORT);

        assertThat(done).isEqualTo(planned);
    }

    @Test
    void refusesATitleTheRangeDoesNotOfferAndListsWhatItOffers() throws IOException {
        projectOfTheImport();
        Map<String, String> before = snapshot();

        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> apply.apply(path(OFFERS), THE_TYPE, "Import 'Map' (java.util)"))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.NO_SUCH_CODE_ACTION))
                .withMessage("The language server offers no action titled \"Import 'Map' (java.util)\" on this range:"
                        + " it offers quickfix: " + IMPORT);
        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> apply.dryRun(path(OFFERS), THE_TYPE, "Import 'Map' (java.util)"))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.NO_SUCH_CODE_ACTION));

        assertThat(snapshot()).isEqualTo(before);
        assertThat(server.asked()).hasSize(2).allMatch(Request.CodeActions.class::isInstance);
    }

    @Test
    void refusesAnActionThatIsACommandForAnEditorAndWritesNothing() throws IOException {
        project(
                CodeActionScenarios.CODE_ACTIONS_ON_THE_NAME_OF_A_METHOD,
                CodeActionScenarios.APPLY_AN_ACTION_THAT_IS_A_COMMAND);
        Map<String, String> before = snapshot();
        var changeSignature = "Change signature for 'names'";

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> apply.apply(path(OFFERS), THE_NAME, changeSignature))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NOT_APPLICABLE))
                .withMessageContaining("The action \"" + changeSignature + "\" on 3:17-3:22 of " + path(OFFERS));

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void rejectsMissingCollaborators() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new ApplyCodeAction(null, new NioFileStore()))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("server"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new ApplyCodeAction(new FakeJavaLanguageServer(root), null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("store"));
    }

    @Test
    void rejectsMissingArguments() throws IOException {
        projectOfTheImport();

        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> apply.apply(null, THE_TYPE, IMPORT))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("file"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> apply.dryRun(path(OFFERS), null, IMPORT))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("range"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> apply.apply(path(OFFERS), THE_TYPE, " "))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("title"));
    }
}
