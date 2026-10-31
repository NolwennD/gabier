package fr.craft.gabier.editing.fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.Diagnostic;
import fr.craft.gabier.editing.domain.FileChange;
import fr.craft.gabier.editing.domain.FileChange.CreateFile;
import fr.craft.gabier.editing.domain.FileChange.DeleteFile;
import fr.craft.gabier.editing.domain.FileChange.EditFile;
import fr.craft.gabier.editing.domain.FileChange.RenameFile;
import fr.craft.gabier.editing.domain.LanguageServerErrorKey;
import fr.craft.gabier.editing.domain.LanguageServerException;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.editing.domain.Range;
import fr.craft.gabier.editing.domain.TextEdit;
import fr.craft.gabier.editing.fixtures.Scenario.Answer;
import fr.craft.gabier.editing.fixtures.Scenario.Request;
import fr.craft.gabier.shared.error.domain.MissingMandatoryValueException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScenarioTest {

    private static final Path ROOT = Path.of("/project");
    private static final Path FILE = Path.of("src/A.java");
    private static final Map<Path, String> FILES = Map.of(FILE, "class A {}\n");
    private static final Position POSITION = new Position(0, 6);

    private static Scenario scenario(Request request, Answer answer) {
        return new Scenario("a-scenario", FILES, request, answer);
    }

    private static ChangeSet set(FileChange... changes) {
        return new ChangeSet(List.of(changes));
    }

    @Test
    void rejectsABlankName() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(
                        () -> new Scenario(" ", FILES, new Request.Diagnostics(FILE), new Answer.Diagnosed(List.of())))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("name"));
    }

    @Test
    void rejectsMissingFiles() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(
                        () -> new Scenario("a", null, new Request.Diagnostics(FILE), new Answer.Diagnosed(List.of())))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("files"));
    }

    @Test
    void rejectsAMissingRequestAndAMissingAnswer() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Scenario("a", FILES, null, new Answer.Diagnosed(List.of())))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("request"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Scenario("a", FILES, new Request.Diagnostics(FILE), null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("answer"));
    }

    @Test
    void rejectsAnAnswerThatDoesNotFitTheRequest() {
        Request.Diagnostics diagnostics = new Request.Diagnostics(FILE);
        Request.Rename rename = new Request.Rename(FILE, POSITION, "B");
        ChangeSet changes = set(new CreateFile(FILE));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(diagnostics, new Answer.Renamed(changes)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(rename, new Answer.Diagnosed(List.of())));
    }

    @Test
    void rejectsAnOrganizedAnswerForAnotherRequestAndAnotherAnswerForOrganizingImports() {
        Request.Rename rename = new Request.Rename(FILE, POSITION, "B");
        Request.OrganizeImports organize = new Request.OrganizeImports(FILE);
        ChangeSet changes = set(new CreateFile(FILE));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(rename, new Answer.Organized(changes)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(rename, new Answer.AlreadyOrganized()));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(organize, new Answer.Renamed(changes)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(organize, new Answer.Diagnosed(List.of())));
    }

    @Test
    void rejectsARefusalThatTheRequestCannotGet() {
        Request.Diagnostics diagnostics = new Request.Diagnostics(FILE);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(diagnostics, new Answer.Refused(LanguageServerErrorKey.NO_SUCH_SYMBOL)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(diagnostics, new Answer.Refused(LanguageServerErrorKey.INVALID_NAME)));
    }

    @Test
    void buildsTheRefusalOfARequest() {
        Request.Diagnostics diagnostics = new Request.Diagnostics(FILE);
        Request.Rename rename = new Request.Rename(FILE, POSITION, "1st");

        assertThat(diagnostics.refusal(LanguageServerErrorKey.NOT_READY)).hasMessageContaining("importing");
        assertThat(diagnostics
                        .refusal(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT)
                        .parameters())
                .isEqualTo(Map.of("file", FILE.toString()));
        assertThat(rename.refusal(LanguageServerErrorKey.NOT_READY).key()).isEqualTo(LanguageServerErrorKey.NOT_READY);
        assertThat(rename.refusal(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT).parameters())
                .isEqualTo(Map.of("file", FILE.toString()));
        assertThat(rename.refusal(LanguageServerErrorKey.NO_SUCH_SYMBOL).parameters())
                .isEqualTo(Map.of("file", FILE.toString(), "line", "0", "column", "6"));
        LanguageServerException invalidName = rename.refusal(LanguageServerErrorKey.INVALID_NAME);
        assertThat(invalidName.parameters()).containsEntry("name", "1st");
    }

    @Test
    void buildsTheRefusalOfAnOrganizeImportsRequest() {
        Request.OrganizeImports organize = new Request.OrganizeImports(FILE);

        assertThat(organize.refusal(LanguageServerErrorKey.NOT_READY).key())
                .isEqualTo(LanguageServerErrorKey.NOT_READY);
        assertThat(organize.refusal(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT).parameters())
                .isEqualTo(Map.of("file", FILE.toString()));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> organize.refusal(LanguageServerErrorKey.NO_SUCH_SYMBOL));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> organize.refusal(LanguageServerErrorKey.INVALID_NAME));
    }

    @Test
    void resolvesTheRequestAgainstARoot() {
        assertThat(new Request.Diagnostics(FILE).resolvedIn(ROOT))
                .isEqualTo(new Request.Diagnostics(Path.of("/project/src/A.java")));
        assertThat(new Request.Rename(FILE, POSITION, "B").resolvedIn(ROOT))
                .isEqualTo(new Request.Rename(Path.of("/project/src/A.java"), POSITION, "B"));
        assertThat(new Request.OrganizeImports(FILE).resolvedIn(ROOT))
                .isEqualTo(new Request.OrganizeImports(Path.of("/project/src/A.java")));
    }

    @Test
    void normalizesAPathThatLeavesTheRoot() {
        assertThat(new Request.Diagnostics(Path.of("../outside/B.java")).resolvedIn(ROOT))
                .isEqualTo(new Request.Diagnostics(Path.of("/outside/B.java")));
    }

    @Test
    void resolvesTheChangesOfAnAnswerAgainstARoot() {
        TextEdit edit = new TextEdit(new Range(POSITION, POSITION), "x");
        ChangeSet changes = set(
                new CreateFile(Path.of("src/B.java")),
                new EditFile(FILE, List.of(edit)),
                new RenameFile(FILE, Path.of("src/C.java")),
                new DeleteFile(Path.of("src/D.java")));

        Answer resolved = new Answer.Renamed(changes).resolvedIn(ROOT);

        assertThat(resolved)
                .isEqualTo(new Answer.Renamed(set(
                        new CreateFile(Path.of("/project/src/B.java")),
                        new EditFile(Path.of("/project/src/A.java"), List.of(edit)),
                        new RenameFile(Path.of("/project/src/A.java"), Path.of("/project/src/C.java")),
                        new DeleteFile(Path.of("/project/src/D.java")))));
    }

    @Test
    void resolvesTheChangesOfAnOrganizedAnswerAgainstARoot() {
        TextEdit edit = new TextEdit(new Range(POSITION, POSITION), "import a.B;\n");

        Answer resolved = new Answer.Organized(set(new EditFile(FILE, List.of(edit)))).resolvedIn(ROOT);

        assertThat(resolved)
                .isEqualTo(new Answer.Organized(set(new EditFile(Path.of("/project/src/A.java"), List.of(edit)))));
    }

    @Test
    void leavesTheOtherAnswersAsTheyAre() {
        Answer diagnosed =
                new Answer.Diagnosed(List.of(new Diagnostic(Diagnostic.Severity.ERROR, POSITION, "Type mismatch")));
        Answer refused = new Answer.Refused(LanguageServerErrorKey.NOT_READY);
        Answer alreadyOrganized = new Answer.AlreadyOrganized();

        assertThat(alreadyOrganized.resolvedIn(ROOT)).isEqualTo(alreadyOrganized);
        assertThat(diagnosed.resolvedIn(ROOT)).isEqualTo(diagnosed);
        assertThat(refused.resolvedIn(ROOT)).isEqualTo(refused);
    }

    @Test
    void resolvesTheWholeScenario() {
        Scenario scenario = scenario(new Request.Diagnostics(FILE), new Answer.Diagnosed(List.of()));

        Scenario resolved = scenario.inside(ROOT);

        assertThat(resolved.name()).isEqualTo("a-scenario");
        assertThat(resolved.files()).isEqualTo(Map.of(Path.of("/project/src/A.java"), "class A {}\n"));
        assertThat(resolved.request()).isEqualTo(new Request.Diagnostics(Path.of("/project/src/A.java")));
        assertThat(resolved.answer()).isEqualTo(scenario.answer());
    }

    @Test
    void writesTheFilesOfTheProjectUnderADirectory(@TempDir Path directory) throws IOException {
        Scenario scenario = new Scenario(
                "a-scenario",
                Map.of(Path.of("src/a/A.java"), "class A {}\n", Path.of("src/B.java"), "class B {}\n"),
                new Request.Diagnostics(Path.of("src/B.java")),
                new Answer.Diagnosed(List.of()));

        scenario.writeProject(directory);

        assertThat(directory.resolve("src/a/A.java")).hasContent("class A {}\n");
        assertThat(directory.resolve("src/B.java")).hasContent("class B {}\n");
    }
}
