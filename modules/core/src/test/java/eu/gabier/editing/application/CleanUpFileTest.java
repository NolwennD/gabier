package eu.gabier.editing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.editing.domain.ChangeReport;
import eu.gabier.editing.domain.CleanUp;
import eu.gabier.editing.domain.EditingErrorKey;
import eu.gabier.editing.domain.EditingException;
import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.fixtures.CleanUpScenarios;
import eu.gabier.editing.fixtures.FakeJavaLanguageServer;
import eu.gabier.editing.fixtures.Scenario;
import eu.gabier.editing.fixtures.Scenario.Request;
import eu.gabier.editing.infrastructure.secondary.NioFileStore;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CleanUpFileTest {

    private static final String BANNER = "src/main/java/demo/Banner.java";
    private static final String NOTICE = "src/main/java/demo/Notice.java";
    private static final String PLAIN = "src/main/java/demo/Plain.java";
    private static final List<String> TEXT_BLOCK = List.of("stringConcatToTextBlock");

    @TempDir
    Path root;

    private FakeJavaLanguageServer server;
    private CleanUpFile cleanUp;

    /** Writes the project of the scenarios and plays them with a fake scripted by them. */
    private void project(Scenario... scenarios) throws IOException {
        server = new FakeJavaLanguageServer(root);
        for (Scenario scenario : scenarios) {
            scenario.writeProject(root);
            server.scripted(scenario);
        }
        cleanUp = new CleanUpFile(server, new NioFileStore());
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

    @Test
    void rewritesTheConcatenationsOfTheFileIntoTextBlocksOnDisk() throws IOException {
        project(CleanUpScenarios.CLEAN_UP_A_CONCATENATION_INTO_A_TEXT_BLOCK);

        ChangeReport report = cleanUp.apply(path(BANNER), TEXT_BLOCK);

        assertThat(text(BANNER))
                .contains("return \"\"\"")
                .contains("Hello, \"world\"!")
                .doesNotContain("+ \"");
        assertThat(report.modified()).containsExactly(path(BANNER));
        assertThat(report.created()).isEmpty();
        assertThat(report.renamed()).isEmpty();
        assertThat(report.deleted()).isEmpty();
    }

    @Test
    void asksTheServerOnceForTheFileAndTheCleanUpsInTheOrderGiven() throws IOException {
        project(CleanUpScenarios.CLEAN_UP_WITH_TWO_CLEAN_UPS);

        cleanUp.apply(path(NOTICE), List.of("addFinalModifier", "stringConcatToTextBlock"));

        assertThat(server.asked()).hasSize(1);
        assertThat(server.asked().getFirst())
                .isEqualTo(new Request.CleanUpFile(
                        path(NOTICE), List.of(CleanUp.ADD_FINAL_MODIFIER, CleanUp.STRING_CONCAT_TO_TEXT_BLOCK)));
        assertThat(text(NOTICE)).contains("final String title = \"\"\"");
    }

    @Test
    void writesNothingOnADryRunAndGivesTheDiff() throws IOException {
        project(CleanUpScenarios.CLEAN_UP_A_CONCATENATION_INTO_A_TEXT_BLOCK);
        Map<String, String> before = snapshot();

        ChangeReport report = cleanUp.dryRun(path(BANNER), TEXT_BLOCK);

        assertThat(snapshot()).isEqualTo(before);
        assertThat(report.modified()).containsExactly(path(BANNER));
        assertThat(report.diff()).contains("+        return \"\"\"", "+            Hello, \"world\"!");
    }

    @Test
    void givesTheSameReportOnADryRunAndOnTheRealRun() throws IOException {
        project(CleanUpScenarios.CLEAN_UP_A_CONCATENATION_INTO_A_TEXT_BLOCK);

        ChangeReport planned = cleanUp.dryRun(path(BANNER), TEXT_BLOCK);
        ChangeReport done = cleanUp.apply(path(BANNER), TEXT_BLOCK);

        assertThat(done).isEqualTo(planned);
    }

    @Test
    void changesNothingForAFileWithNothingToCleanAndIsNotARefusal() throws IOException {
        project(CleanUpScenarios.CLEAN_UP_A_FILE_THAT_IS_CLEAN);
        Map<String, String> before = snapshot();

        ChangeReport applied = cleanUp.apply(path(PLAIN), TEXT_BLOCK);
        ChangeReport planned = cleanUp.dryRun(path(PLAIN), TEXT_BLOCK);

        assertThat(snapshot()).isEqualTo(before);
        assertThat(applied).isEqualTo(new ChangeReport(List.of(), List.of(), List.of(), List.of(), ""));
        assertThat(planned).isEqualTo(applied);
    }

    @Test
    void refusesAnUnknownCleanUpBeforeAskingTheServerAndWritesNothing() throws IOException {
        project(CleanUpScenarios.CLEAN_UP_A_CONCATENATION_INTO_A_TEXT_BLOCK);
        Map<String, String> before = snapshot();

        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> cleanUp.apply(path(BANNER), List.of("stringConcatToTextBlock", "textBlocks")))
                .satisfies(exception -> {
                    assertThat(exception.key()).isEqualTo(EditingErrorKey.UNKNOWN_CLEAN_UP);
                    assertThat(exception.parameters()).containsEntry("cleanUp", "textBlocks");
                });
        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> cleanUp.dryRun(path(BANNER), List.of("textBlocks")))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.UNKNOWN_CLEAN_UP));

        assertThat(server.asked()).isEmpty();
        assertThat(snapshot()).isEqualTo(before);
    }

    private void assertRefused(Scenario scenario, LanguageServerErrorKey key) throws IOException {
        project(scenario);
        Map<String, String> before = snapshot();
        Path file = root.resolve(scenario.request().file()).normalize();

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> cleanUp.apply(file, TEXT_BLOCK))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(key));
        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> cleanUp.dryRun(file, TEXT_BLOCK))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(key));

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void reportsAServerThatIsNotReady() throws IOException {
        assertRefused(CleanUpScenarios.CLEAN_UP_WHILE_THE_SERVER_IMPORTS, LanguageServerErrorKey.NOT_READY);
    }

    @Test
    void reportsAFileOutsideTheProject() throws IOException {
        assertRefused(CleanUpScenarios.CLEAN_UP_OUTSIDE_THE_PROJECT, LanguageServerErrorKey.FILE_OUTSIDE_PROJECT);
    }

    @Test
    void rejectsMissingCollaborators() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new CleanUpFile(null, new NioFileStore()))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("server"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new CleanUpFile(new FakeJavaLanguageServer(root), null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("store"));
    }

    @Test
    void rejectsAMissingFileAndMissingCleanUps() throws IOException {
        project(CleanUpScenarios.CLEAN_UP_A_CONCATENATION_INTO_A_TEXT_BLOCK);

        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> cleanUp.apply(null, TEXT_BLOCK))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("file"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> cleanUp.dryRun(null, TEXT_BLOCK))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("file"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> cleanUp.apply(path(BANNER), null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("ids"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> cleanUp.apply(path(BANNER), List.of()))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("ids"));
    }
}
