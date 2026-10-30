package fr.craft.gabier.editing.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.editing.domain.ChangeApplier;
import fr.craft.gabier.editing.domain.Diagnostic;
import fr.craft.gabier.editing.domain.FileChange;
import fr.craft.gabier.editing.domain.FileChange.EditFile;
import fr.craft.gabier.editing.domain.LanguageServerErrorKey;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.editing.domain.TextEdit;
import fr.craft.gabier.editing.fixtures.Scenario.Answer;
import fr.craft.gabier.editing.fixtures.Scenario.Request;
import fr.craft.gabier.editing.infrastructure.secondary.NioFileStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The catalogue is data that adapters replay against a real server: it has to be coherent before it is trusted. */
class ScenariosTest {

    private static final List<Scenario> ALL = Scenarios.all();

    private static String lineOf(Scenario scenario, Path file, int line) {
        return scenario.files().get(file).split("\n", -1)[line];
    }

    /** The identifier that starts at the position, or an empty string if there is none. */
    private static String identifierAt(Scenario scenario, Path file, Position position) {
        String line = lineOf(scenario, file, position.line());
        int end = position.column();
        while (end < line.length() && Character.isJavaIdentifierPart(line.charAt(end))) {
            end++;
        }
        boolean startsHere =
                position.column() == 0 || !Character.isJavaIdentifierPart(line.charAt(position.column() - 1));

        return startsHere ? line.substring(position.column(), end) : "";
    }

    private static String selected(Scenario scenario, EditFile change, TextEdit edit) {
        int line = edit.range().start().line();
        assertThat(edit.range().end().line()).as("a single-line edit").isEqualTo(line);

        return lineOf(scenario, change.file(), line)
                .substring(edit.range().start().column(), edit.range().end().column());
    }

    private static List<Scenario> answeredWith(Class<? extends Answer> answer) {
        return ALL.stream()
                .filter(scenario -> answer.isInstance(scenario.answer()))
                .toList();
    }

    @Test
    void holdsTheCleanFileTheErrorTheWarningTheFourRenamesAndTheRefusals() {
        assertThat(ALL.stream().map(Scenario::name))
                .containsExactly(
                        "diagnostics-of-a-clean-file",
                        "diagnostics-of-a-file-with-an-error",
                        "diagnostics-of-a-file-with-a-warning",
                        "diagnostics-while-the-server-imports",
                        "diagnostics-of-a-file-outside-the-project",
                        "rename-a-local-variable",
                        "rename-a-field",
                        "rename-a-method",
                        "rename-a-type",
                        "rename-where-there-is-no-symbol",
                        "rename-to-an-invalid-name",
                        "rename-while-the-server-imports",
                        "rename-a-file-outside-the-project");
    }

    @Test
    void refusesForEachReasonOfThePortOnARename() {
        assertThat(answeredWith(Answer.Refused.class).stream()
                        .filter(scenario -> scenario.request() instanceof Request.Rename)
                        .map(scenario -> ((Answer.Refused) scenario.answer()).key()))
                .containsExactlyInAnyOrder(LanguageServerErrorKey.values());
    }

    @Test
    void holdsOnlyRelativeJavaSourcesAndRelativeRequests() {
        for (Scenario scenario : ALL) {
            assertThat(scenario.files().keySet())
                    .as(scenario.name())
                    .isNotEmpty()
                    .allSatisfy(file -> {
                        assertThat(file.isAbsolute()).isFalse();
                        assertThat(file.toString()).startsWith("src/main/java/").endsWith(".java");
                    });
            assertThat(scenario.request().file().isAbsolute())
                    .as(scenario.name())
                    .isFalse();
        }
    }

    @Test
    void asksAboutAFileOfTheProjectExceptWhenTheFileIsOutsideIt() {
        for (Scenario scenario : ALL) {
            Path file = scenario.request().file();
            boolean outside = scenario.answer() instanceof Answer.Refused refused
                    && refused.key() == LanguageServerErrorKey.FILE_OUTSIDE_PROJECT;

            if (outside) {
                assertThat(file.normalize().startsWith(".."))
                        .as(scenario.name())
                        .isTrue();
            } else {
                assertThat(scenario.files()).as(scenario.name()).containsKey(file);
            }
        }
    }

    @Test
    void keepsEveryDiagnosticInsideItsFile() {
        for (Scenario scenario : answeredWith(Answer.Diagnosed.class)) {
            Path file = scenario.request().file();
            List<Diagnostic> diagnostics = ((Answer.Diagnosed) scenario.answer()).diagnostics();

            for (Diagnostic diagnostic : diagnostics) {
                String line = lineOf(scenario, file, diagnostic.position().line());
                assertThat(diagnostic.position().column()).as(scenario.name()).isLessThan(line.length());
            }
        }
    }

    @Test
    void showsACleanFileAndAFileWithOneError() {
        Scenario clean = ALL.get(0);
        Scenario broken = ALL.get(1);

        assertThat(((Answer.Diagnosed) clean.answer()).diagnostics()).isEmpty();
        assertThat(((Answer.Diagnosed) broken.answer()).diagnostics())
                .extracting(Diagnostic::severity)
                .containsExactly(Diagnostic.Severity.ERROR);
    }

    @Test
    void showsAFileWithOnlyAWarningAtTheNameOfTheVariable() {
        Scenario sloppy = ALL.get(2);
        Diagnostic warning = ((Answer.Diagnosed) sloppy.answer()).diagnostics().getFirst();

        assertThat(((Answer.Diagnosed) sloppy.answer()).diagnostics()).hasSize(1);
        assertThat(warning.severity()).isEqualTo(Diagnostic.Severity.WARNING);
        assertThat(identifierAt(sloppy, sloppy.request().file(), warning.position()))
                .isEqualTo("unused");
    }

    @Test
    void startsEveryRenameOnTheSymbolAndEditsOnlyThatSymbol() {
        for (Scenario scenario : answeredWith(Answer.Renamed.class)) {
            Request.Rename request = (Request.Rename) scenario.request();
            String symbol = identifierAt(scenario, request.file(), request.position());
            List<FileChange> changes =
                    ((Answer.Renamed) scenario.answer()).changes().changes();

            assertThat(symbol).as(scenario.name()).isNotEmpty().isNotEqualTo(request.newName());
            changes.stream()
                    .filter(EditFile.class::isInstance)
                    .map(EditFile.class::cast)
                    .forEach(change -> change.edits().forEach(edit -> {
                        assertThat(selected(scenario, change, edit))
                                .as(scenario.name())
                                .isEqualTo(symbol);
                        assertThat(edit.newText()).as(scenario.name()).isEqualTo(request.newName());
                    }));
        }
    }

    @Test
    void startsTheRefusedRenamesWhereTheirReasonIs() {
        Scenario noSymbol = ALL.stream()
                .filter(scenario -> scenario.name().equals("rename-where-there-is-no-symbol"))
                .findFirst()
                .orElseThrow();
        Scenario invalidName = ALL.stream()
                .filter(scenario -> scenario.name().equals("rename-to-an-invalid-name"))
                .findFirst()
                .orElseThrow();
        Request.Rename onNothing = (Request.Rename) noSymbol.request();
        Request.Rename onASymbol = (Request.Rename) invalidName.request();

        assertThat(identifierAt(noSymbol, onNothing.file(), onNothing.position()))
                .isEmpty();
        assertThat(identifierAt(invalidName, onASymbol.file(), onASymbol.position()))
                .isNotEmpty();
        assertThat(Character.isJavaIdentifierStart(onASymbol.newName().charAt(0)))
                .isFalse();
    }

    @Test
    void leavesNoWholeWordOfTheOldNameOnceTheRenameIsApplied(@TempDir Path directory) throws IOException {
        for (Scenario scenario : answeredWith(Answer.Renamed.class)) {
            Path root = Files.createTempDirectory(directory, "project");
            Scenario inside = scenario.inside(root);
            inside.writeProject(root);
            Request.Rename request = (Request.Rename) scenario.request();
            String oldName = identifierAt(scenario, request.file(), request.position());
            Pattern oldWord =
                    Pattern.compile("(?<![\\p{L}\\p{N}_$])" + Pattern.quote(oldName) + "(?![\\p{L}\\p{N}_$])");

            new ChangeApplier(new NioFileStore()).apply(((Answer.Renamed) inside.answer()).changes());

            try (Stream<Path> written = Files.walk(root)) {
                List<Path> sources = written.filter(Files::isRegularFile).toList();
                assertThat(sources).as(scenario.name()).isNotEmpty();
                for (Path source : sources) {
                    assertThat(oldWord.matcher(Files.readString(source)).find())
                            .as(scenario.name() + " in " + source)
                            .isFalse();
                }
            }
        }
    }

    @Test
    void namesEveryScenarioOnce() {
        assertThat(new HashSet<>(ALL.stream().map(Scenario::name).toList())).hasSameSizeAs(ALL);
    }
}
