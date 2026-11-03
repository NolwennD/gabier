package eu.gabier.editing.fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.CleanUp;
import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.editing.domain.FileChange;
import eu.gabier.editing.domain.FileChange.CreateFile;
import eu.gabier.editing.domain.FileChange.DeleteFile;
import eu.gabier.editing.domain.FileChange.EditFile;
import eu.gabier.editing.domain.FileChange.RenameFile;
import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.Location;
import eu.gabier.editing.domain.NotMovableReason;
import eu.gabier.editing.domain.OfferedAction;
import eu.gabier.editing.domain.OfferedActions;
import eu.gabier.editing.domain.PackageName;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.Range;
import eu.gabier.editing.domain.ReferenceLocations;
import eu.gabier.editing.domain.TextEdit;
import eu.gabier.editing.domain.TypeKind;
import eu.gabier.editing.fixtures.Scenario.Answer;
import eu.gabier.editing.fixtures.Scenario.Request;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
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
    private static final Range SELECTION = new Range(POSITION, new Position(0, 7));

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
    void rejectsACleanUpAnswerForAnotherRequestAndAnotherAnswerForACleanUp() {
        Request.Rename rename = new Request.Rename(FILE, POSITION, "B");
        Request.CleanUpFile cleanUp = new Request.CleanUpFile(FILE, List.of(CleanUp.ADD_OVERRIDE));
        ChangeSet changes = set(new CreateFile(FILE));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(rename, new Answer.CleanedUp(changes)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(rename, new Answer.AlreadyClean()));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(cleanUp, new Answer.Organized(changes)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(cleanUp, new Answer.AlreadyOrganized()));
        assertThat(scenario(cleanUp, new Answer.CleanedUp(changes)).answer()).isEqualTo(new Answer.CleanedUp(changes));
        assertThat(scenario(cleanUp, new Answer.AlreadyClean()).answer()).isEqualTo(new Answer.AlreadyClean());
    }

    @Test
    void rejectsACleanUpRequestWithoutCleanUps() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Request.CleanUpFile(FILE, List.of()))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("cleanUps"));
    }

    @Test
    void buildsTheRefusalOfACleanUpRequest() {
        Request.CleanUpFile cleanUp = new Request.CleanUpFile(FILE, List.of(CleanUp.ADD_OVERRIDE));

        assertThat(cleanUp.refusal(LanguageServerErrorKey.NOT_READY).key()).isEqualTo(LanguageServerErrorKey.NOT_READY);
        assertThat(cleanUp.refusal(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT).parameters())
                .isEqualTo(Map.of("file", FILE.toString()));
        assertThat(cleanUp.refusal(LanguageServerErrorKey.FILE_MISSING).key())
                .isEqualTo(LanguageServerErrorKey.FILE_MISSING);
        assertThat(cleanUp.refusal(LanguageServerErrorKey.NOT_A_FILE).key())
                .isEqualTo(LanguageServerErrorKey.NOT_A_FILE);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> cleanUp.refusal(LanguageServerErrorKey.UNRESOLVED_IMPORTS));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> cleanUp.refusal(LanguageServerErrorKey.NO_SUCH_SYMBOL));
    }

    @Test
    void resolvesACleanUpRequestAndAnswerAgainstARoot() {
        TextEdit edit = new TextEdit(new Range(POSITION, POSITION), "class A {}\n");
        List<CleanUp> cleanUps = List.of(CleanUp.ADD_OVERRIDE);

        assertThat(new Request.CleanUpFile(FILE, cleanUps).resolvedIn(ROOT))
                .isEqualTo(new Request.CleanUpFile(Path.of("/project/src/A.java"), cleanUps));
        assertThat(new Answer.CleanedUp(set(new EditFile(FILE, List.of(edit)))).resolvedIn(ROOT))
                .isEqualTo(new Answer.CleanedUp(set(new EditFile(Path.of("/project/src/A.java"), List.of(edit)))));
        assertThat(new Answer.AlreadyClean().resolvedIn(ROOT)).isEqualTo(new Answer.AlreadyClean());
    }

    @Test
    void rejectsAnExtractedAnswerForAnotherRequestAndAnotherAnswerForExtractingAVariable() {
        Request.Rename rename = new Request.Rename(FILE, POSITION, "B");
        Request.ExtractVariable extract = new Request.ExtractVariable(FILE, SELECTION, "b");
        ChangeSet changes = set(new CreateFile(FILE));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(rename, new Answer.Extracted(changes)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(extract, new Answer.Renamed(changes)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(extract, new Answer.AlreadyOrganized()));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(
                        () -> scenario(new Request.ExtractMethod(FILE, SELECTION, "b"), new Answer.Renamed(changes)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(
                        () -> scenario(new Request.ExtractConstant(FILE, SELECTION, "B"), new Answer.Renamed(changes)));
    }

    @Test
    void rejectsAMovedAnswerForAnotherRequestAndAnotherAnswerForMovingAType() {
        Request.Rename rename = new Request.Rename(FILE, POSITION, "B");
        Request.MoveTypeToFile move = new Request.MoveTypeToFile(FILE, POSITION);
        ChangeSet changes = set(new CreateFile(FILE));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(rename, new Answer.Moved(changes)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(move, new Answer.Renamed(changes)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(move, new Answer.Extracted(changes)));
    }

    @Test
    void rejectsAMovedToAPackageAnswerForAnotherRequestAndAnotherAnswerForMovingToAPackage() {
        Request.Rename rename = new Request.Rename(FILE, POSITION, "B");
        Request.MoveToPackage move = new Request.MoveToPackage(List.of(FILE), PackageName.of("demo.b"));
        ChangeSet changes = set(new CreateFile(FILE));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(rename, new Answer.MovedToPackage(changes)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(move, new Answer.Moved(changes)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(move, new Answer.Renamed(changes)));
    }

    @Test
    void rejectsACreatedAnswerForAnotherRequestAndAnotherAnswerForCreatingAType() {
        Request.Rename rename = new Request.Rename(FILE, POSITION, "B");
        Request.CreateType create = new Request.CreateType(FILE, TypeKind.CLASS);
        ChangeSet changes = set(new CreateFile(FILE));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(rename, new Answer.Created(changes)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(create, new Answer.Moved(changes)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(create, new Answer.Extracted(changes)));
    }

    @Test
    void rejectsAnInlinedAnswerForAnotherRequestAndAnotherAnswerForInlining() {
        Request.Rename rename = new Request.Rename(FILE, POSITION, "B");
        Request.Inline inline = new Request.Inline(FILE, POSITION);
        ChangeSet changes = set(new CreateFile(FILE));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(rename, new Answer.Inlined(changes)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(inline, new Answer.Renamed(changes)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(inline, new Answer.Extracted(changes)));
    }

    @Test
    void rejectsARefusalThatTheRequestCannotGet() {
        Request.Diagnostics diagnostics = new Request.Diagnostics(FILE);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(diagnostics, new Answer.Refused(LanguageServerErrorKey.NO_SUCH_SYMBOL)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(diagnostics, new Answer.Refused(LanguageServerErrorKey.INVALID_NAME)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(diagnostics, new Answer.Refused(LanguageServerErrorKey.NOT_EXTRACTABLE)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(diagnostics, new Answer.Refused(LanguageServerErrorKey.AMBIGUOUS_IMPORTS)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(diagnostics, new Answer.Refused(LanguageServerErrorKey.UNRESOLVED_IMPORTS)));
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
        assertThat(diagnostics.refusal(LanguageServerErrorKey.FILE_MISSING).key())
                .isEqualTo(LanguageServerErrorKey.FILE_MISSING);
        assertThat(diagnostics.refusal(LanguageServerErrorKey.NOT_A_FILE).key())
                .isEqualTo(LanguageServerErrorKey.NOT_A_FILE);
        assertThat(rename.refusal(LanguageServerErrorKey.NOT_READY).key()).isEqualTo(LanguageServerErrorKey.NOT_READY);
        assertThat(rename.refusal(LanguageServerErrorKey.FILE_MISSING).key())
                .isEqualTo(LanguageServerErrorKey.FILE_MISSING);
        assertThat(rename.refusal(LanguageServerErrorKey.NOT_A_FILE).key())
                .isEqualTo(LanguageServerErrorKey.NOT_A_FILE);
        assertThat(rename.refusal(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT).parameters())
                .isEqualTo(Map.of("file", FILE.toString()));
        assertThat(rename.refusal(LanguageServerErrorKey.NO_SUCH_SYMBOL).parameters())
                .isEqualTo(Map.of("file", FILE.toString(), "line", "0", "column", "6"));
        LanguageServerException invalidName = rename.refusal(LanguageServerErrorKey.INVALID_NAME);
        assertThat(invalidName.parameters()).containsEntry("name", "1st");
        LanguageServerException emptyPackage =
                rename.refusal(LanguageServerErrorKey.EMPTY_PACKAGE, List.of("a", "a.b"));
        assertThat(emptyPackage.parameters())
                .isEqualTo(Map.of(
                        "file", FILE.toString(),
                        "line", "0",
                        "column", "6",
                        "package", "a",
                        "declaredPackage", "a.b"));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> rename.refusal(LanguageServerErrorKey.NOT_EXTRACTABLE));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> diagnostics.refusal(LanguageServerErrorKey.EMPTY_PACKAGE));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> rename.refusal(LanguageServerErrorKey.AMBIGUOUS_IMPORTS));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> rename.refusal(LanguageServerErrorKey.UNRESOLVED_IMPORTS));
    }

    @Test
    void buildsTheRefusalOfAnExtractVariableRequest() {
        Request.ExtractVariable extract = new Request.ExtractVariable(FILE, SELECTION, "1st");

        assertThat(extract.refusal(LanguageServerErrorKey.NOT_READY).key()).isEqualTo(LanguageServerErrorKey.NOT_READY);
        assertThat(extract.refusal(LanguageServerErrorKey.FILE_MISSING).key())
                .isEqualTo(LanguageServerErrorKey.FILE_MISSING);
        assertThat(extract.refusal(LanguageServerErrorKey.NOT_A_FILE).key())
                .isEqualTo(LanguageServerErrorKey.NOT_A_FILE);
        assertThat(extract.refusal(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT).parameters())
                .isEqualTo(Map.of("file", FILE.toString()));
        assertThat(extract.refusal(LanguageServerErrorKey.NOT_EXTRACTABLE).parameters())
                .isEqualTo(
                        Map.of("file", FILE.toString(), "line", "0", "column", "6", "endLine", "0", "endColumn", "7"));
        assertThat(extract.refusal(LanguageServerErrorKey.INVALID_NAME).parameters())
                .isEqualTo(Map.of("file", FILE.toString(), "line", "0", "column", "6", "name", "1st"));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> extract.refusal(LanguageServerErrorKey.NO_SUCH_SYMBOL));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> extract.refusal(LanguageServerErrorKey.AMBIGUOUS_IMPORTS));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> extract.refusal(LanguageServerErrorKey.UNRESOLVED_IMPORTS));
    }

    @Test
    void buildsTheRefusalOfAnExtractMethodRequest() {
        Request.ExtractMethod extract = new Request.ExtractMethod(FILE, SELECTION, "1st");

        assertThat(extract.refusal(LanguageServerErrorKey.NOT_READY).key()).isEqualTo(LanguageServerErrorKey.NOT_READY);
        assertThat(extract.refusal(LanguageServerErrorKey.FILE_MISSING).key())
                .isEqualTo(LanguageServerErrorKey.FILE_MISSING);
        assertThat(extract.refusal(LanguageServerErrorKey.NOT_A_FILE).key())
                .isEqualTo(LanguageServerErrorKey.NOT_A_FILE);
        assertThat(extract.refusal(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT).parameters())
                .isEqualTo(Map.of("file", FILE.toString()));
        assertThat(extract.refusal(LanguageServerErrorKey.NOT_EXTRACTABLE).parameters())
                .isEqualTo(
                        Map.of("file", FILE.toString(), "line", "0", "column", "6", "endLine", "0", "endColumn", "7"));
        assertThat(extract.refusal(LanguageServerErrorKey.INVALID_NAME).parameters())
                .isEqualTo(Map.of("file", FILE.toString(), "line", "0", "column", "6", "name", "1st"));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> extract.refusal(LanguageServerErrorKey.NO_SUCH_SYMBOL));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> extract.refusal(LanguageServerErrorKey.AMBIGUOUS_IMPORTS));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> extract.refusal(LanguageServerErrorKey.UNRESOLVED_IMPORTS));
    }

    @Test
    void buildsTheRefusalOfAnExtractConstantRequest() {
        Request.ExtractConstant extract = new Request.ExtractConstant(FILE, SELECTION, "1ST");

        assertThat(extract.refusal(LanguageServerErrorKey.NOT_READY).key()).isEqualTo(LanguageServerErrorKey.NOT_READY);
        assertThat(extract.refusal(LanguageServerErrorKey.FILE_MISSING).key())
                .isEqualTo(LanguageServerErrorKey.FILE_MISSING);
        assertThat(extract.refusal(LanguageServerErrorKey.NOT_A_FILE).key())
                .isEqualTo(LanguageServerErrorKey.NOT_A_FILE);
        assertThat(extract.refusal(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT).parameters())
                .isEqualTo(Map.of("file", FILE.toString()));
        assertThat(extract.refusal(LanguageServerErrorKey.NOT_EXTRACTABLE).parameters())
                .isEqualTo(
                        Map.of("file", FILE.toString(), "line", "0", "column", "6", "endLine", "0", "endColumn", "7"));
        assertThat(extract.refusal(LanguageServerErrorKey.INVALID_NAME).parameters())
                .isEqualTo(Map.of("file", FILE.toString(), "line", "0", "column", "6", "name", "1ST"));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> extract.refusal(LanguageServerErrorKey.NO_SUCH_SYMBOL));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> extract.refusal(LanguageServerErrorKey.AMBIGUOUS_IMPORTS));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> extract.refusal(LanguageServerErrorKey.UNRESOLVED_IMPORTS));
    }

    @Test
    void buildsTheRefusalOfAnExtractFieldRequest() {
        Request.ExtractField extract = new Request.ExtractField(FILE, SELECTION, "1st");

        assertThat(extract.refusal(LanguageServerErrorKey.NOT_READY).key()).isEqualTo(LanguageServerErrorKey.NOT_READY);
        assertThat(extract.refusal(LanguageServerErrorKey.FILE_MISSING).key())
                .isEqualTo(LanguageServerErrorKey.FILE_MISSING);
        assertThat(extract.refusal(LanguageServerErrorKey.NOT_A_FILE).key())
                .isEqualTo(LanguageServerErrorKey.NOT_A_FILE);
        assertThat(extract.refusal(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT).parameters())
                .isEqualTo(Map.of("file", FILE.toString()));
        assertThat(extract.refusal(LanguageServerErrorKey.NOT_EXTRACTABLE).parameters())
                .isEqualTo(
                        Map.of("file", FILE.toString(), "line", "0", "column", "6", "endLine", "0", "endColumn", "7"));
        assertThat(extract.refusal(LanguageServerErrorKey.INVALID_NAME).parameters())
                .isEqualTo(Map.of("file", FILE.toString(), "line", "0", "column", "6", "name", "1st"));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> extract.refusal(LanguageServerErrorKey.NO_SUCH_SYMBOL));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> extract.refusal(LanguageServerErrorKey.AMBIGUOUS_IMPORTS));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> extract.refusal(LanguageServerErrorKey.UNRESOLVED_IMPORTS));
    }

    @Test
    void refusesASelectionTheFourExtractionsCannotExtractInOneWay() {
        var refusals = List.of(
                new Request.ExtractVariable(FILE, SELECTION, "name").refusal(LanguageServerErrorKey.NOT_EXTRACTABLE),
                new Request.ExtractMethod(FILE, SELECTION, "name").refusal(LanguageServerErrorKey.NOT_EXTRACTABLE),
                new Request.ExtractConstant(FILE, SELECTION, "NAME").refusal(LanguageServerErrorKey.NOT_EXTRACTABLE),
                new Request.ExtractField(FILE, SELECTION, "name").refusal(LanguageServerErrorKey.NOT_EXTRACTABLE));

        assertThat(refusals).allSatisfy(refusal -> {
            assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_EXTRACTABLE);
            assertThat(refusal.parameters()).isEqualTo(refusals.getFirst().parameters());
            assertThat(refusal).hasMessage(refusals.getFirst().getMessage());
        });
    }

    @Test
    void buildsTheRefusalOfAMoveTypeToFileRequest() {
        Request.MoveTypeToFile move = new Request.MoveTypeToFile(FILE, POSITION);

        assertThat(move.refusal(LanguageServerErrorKey.NOT_READY).key()).isEqualTo(LanguageServerErrorKey.NOT_READY);
        assertThat(move.refusal(LanguageServerErrorKey.FILE_MISSING).key())
                .isEqualTo(LanguageServerErrorKey.FILE_MISSING);
        assertThat(move.refusal(LanguageServerErrorKey.NOT_A_FILE).key()).isEqualTo(LanguageServerErrorKey.NOT_A_FILE);
        assertThat(move.refusal(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT).parameters())
                .isEqualTo(Map.of("file", FILE.toString()));
        assertThat(move.refusal(LanguageServerErrorKey.NOT_MOVABLE).parameters())
                .isEqualTo(Map.of("file", FILE.toString(), "line", "0", "column", "6"));
        for (var key : List.of(
                LanguageServerErrorKey.NO_SUCH_SYMBOL,
                LanguageServerErrorKey.INVALID_NAME,
                LanguageServerErrorKey.NOT_EXTRACTABLE,
                LanguageServerErrorKey.AMBIGUOUS_IMPORTS,
                LanguageServerErrorKey.UNRESOLVED_IMPORTS)) {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .as(key.toString())
                    .isThrownBy(() -> move.refusal(key));
        }
    }

    @Test
    void buildsTheRefusalOfAMoveToAPackageRequest() {
        Path other = Path.of("src/B.java");
        PackageName target = PackageName.of("demo.b");
        Request.MoveToPackage move = new Request.MoveToPackage(List.of(FILE, other), target);

        assertThat(move.file()).isEqualTo(FILE);
        assertThat(move.refusal(LanguageServerErrorKey.NOT_READY).key()).isEqualTo(LanguageServerErrorKey.NOT_READY);
        assertThat(move.refusal(LanguageServerErrorKey.FILE_MISSING).parameters())
                .isEqualTo(Map.of("file", FILE.toString()));
        assertThat(move.refusal(LanguageServerErrorKey.NOT_A_FILE).key()).isEqualTo(LanguageServerErrorKey.NOT_A_FILE);
        assertThat(move.refusal(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT).parameters())
                .isEqualTo(Map.of("file", FILE.toString()));
        assertThat(move.refusal(LanguageServerErrorKey.NOT_MOVABLE_TO_PACKAGE, List.of("name-taken")))
                .hasMessage(
                        LanguageServerException.notMovableToPackage(List.of(FILE), target, NotMovableReason.NAME_TAKEN)
                                .getMessage());
        assertThat(move.refusal(LanguageServerErrorKey.NOT_MOVABLE_TO_PACKAGE, List.of("several-source-folders")))
                .hasMessage(LanguageServerException.notMovableToPackage(
                                List.of(FILE, other), target, NotMovableReason.SEVERAL_SOURCE_FOLDERS)
                        .getMessage());
        assertThat(move.refusal(LanguageServerErrorKey.NOT_MOVABLE_TO_PACKAGE, List.of("nothing-to-move")))
                .hasMessage(LanguageServerException.notMovableToPackage(
                                List.of(FILE, other), target, NotMovableReason.NOTHING_TO_MOVE)
                        .getMessage());
        for (var key : List.of(
                LanguageServerErrorKey.NO_SUCH_SYMBOL,
                LanguageServerErrorKey.INVALID_NAME,
                LanguageServerErrorKey.NOT_EXTRACTABLE,
                LanguageServerErrorKey.NOT_MOVABLE,
                LanguageServerErrorKey.AMBIGUOUS_IMPORTS,
                LanguageServerErrorKey.UNRESOLVED_IMPORTS)) {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .as(key.toString())
                    .isThrownBy(() -> move.refusal(key));
        }
    }

    @Test
    void buildsTheRefusalOfACreateTypeRequest() {
        Request.CreateType create = new Request.CreateType(FILE, TypeKind.RECORD);

        assertThat(create.refusal(LanguageServerErrorKey.NOT_READY).key()).isEqualTo(LanguageServerErrorKey.NOT_READY);
        assertThat(create.refusal(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT).parameters())
                .isEqualTo(Map.of("file", FILE.toString()));
        assertThat(create.refusal(LanguageServerErrorKey.NOT_CREATABLE).parameters())
                .isEqualTo(Map.of("file", FILE.toString(), "kind", "record"));
        for (var key : List.of(
                LanguageServerErrorKey.FILE_MISSING,
                LanguageServerErrorKey.NOT_A_FILE,
                LanguageServerErrorKey.NO_SUCH_SYMBOL,
                LanguageServerErrorKey.INVALID_NAME,
                LanguageServerErrorKey.NOT_EXTRACTABLE,
                LanguageServerErrorKey.NOT_INLINABLE,
                LanguageServerErrorKey.NOT_MOVABLE,
                LanguageServerErrorKey.AMBIGUOUS_IMPORTS,
                LanguageServerErrorKey.UNRESOLVED_IMPORTS)) {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .as(key.toString())
                    .isThrownBy(() -> create.refusal(key));
        }
    }

    @Test
    void buildsTheRefusalOfAnInlineRequest() {
        Request.Inline inline = new Request.Inline(FILE, POSITION);

        assertThat(inline.refusal(LanguageServerErrorKey.NOT_READY).key()).isEqualTo(LanguageServerErrorKey.NOT_READY);
        assertThat(inline.refusal(LanguageServerErrorKey.FILE_MISSING).key())
                .isEqualTo(LanguageServerErrorKey.FILE_MISSING);
        assertThat(inline.refusal(LanguageServerErrorKey.NOT_A_FILE).key())
                .isEqualTo(LanguageServerErrorKey.NOT_A_FILE);
        assertThat(inline.refusal(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT).parameters())
                .isEqualTo(Map.of("file", FILE.toString()));
        assertThat(inline.refusal(LanguageServerErrorKey.NOT_INLINABLE).parameters())
                .isEqualTo(Map.of("file", FILE.toString(), "line", "0", "column", "6"));
        for (var key : List.of(
                LanguageServerErrorKey.NO_SUCH_SYMBOL,
                LanguageServerErrorKey.INVALID_NAME,
                LanguageServerErrorKey.NOT_EXTRACTABLE,
                LanguageServerErrorKey.NOT_MOVABLE,
                LanguageServerErrorKey.AMBIGUOUS_IMPORTS,
                LanguageServerErrorKey.UNRESOLVED_IMPORTS)) {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .as(key.toString())
                    .isThrownBy(() -> inline.refusal(key));
        }
    }

    @Test
    void buildsTheRefusalOfAnOrganizeImportsRequest() {
        Request.OrganizeImports organize = new Request.OrganizeImports(FILE);

        assertThat(organize.refusal(LanguageServerErrorKey.NOT_READY).key())
                .isEqualTo(LanguageServerErrorKey.NOT_READY);
        assertThat(organize.refusal(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT).parameters())
                .isEqualTo(Map.of("file", FILE.toString()));
        assertThat(organize.refusal(LanguageServerErrorKey.UNRESOLVED_IMPORTS, List.of("a.B"))
                        .parameters())
                .isEqualTo(Map.of("file", FILE.toString(), "imports", "a.B"));
        assertThat(organize.refusal(LanguageServerErrorKey.AMBIGUOUS_IMPORTS, List.of("a.B", "c.B"))
                        .parameters())
                .isEqualTo(Map.of("file", FILE.toString(), "candidates", "a.B, c.B"));
        assertThat(organize.refusal(LanguageServerErrorKey.FILE_MISSING).key())
                .isEqualTo(LanguageServerErrorKey.FILE_MISSING);
        assertThat(organize.refusal(LanguageServerErrorKey.NOT_A_FILE).key())
                .isEqualTo(LanguageServerErrorKey.NOT_A_FILE);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> organize.refusal(LanguageServerErrorKey.NO_SUCH_SYMBOL));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> organize.refusal(LanguageServerErrorKey.INVALID_NAME));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> organize.refusal(LanguageServerErrorKey.NOT_EXTRACTABLE));
    }

    @Test
    void resolvesTheRequestAgainstARoot() {
        assertThat(new Request.Diagnostics(FILE).resolvedIn(ROOT))
                .isEqualTo(new Request.Diagnostics(Path.of("/project/src/A.java")));
        assertThat(new Request.Rename(FILE, POSITION, "B").resolvedIn(ROOT))
                .isEqualTo(new Request.Rename(Path.of("/project/src/A.java"), POSITION, "B"));
        assertThat(new Request.OrganizeImports(FILE).resolvedIn(ROOT))
                .isEqualTo(new Request.OrganizeImports(Path.of("/project/src/A.java")));
        assertThat(new Request.ExtractVariable(FILE, SELECTION, "b").resolvedIn(ROOT))
                .isEqualTo(new Request.ExtractVariable(Path.of("/project/src/A.java"), SELECTION, "b"));
        assertThat(new Request.ExtractMethod(FILE, SELECTION, "b").resolvedIn(ROOT))
                .isEqualTo(new Request.ExtractMethod(Path.of("/project/src/A.java"), SELECTION, "b"));
        assertThat(new Request.ExtractConstant(FILE, SELECTION, "B").resolvedIn(ROOT))
                .isEqualTo(new Request.ExtractConstant(Path.of("/project/src/A.java"), SELECTION, "B"));
        assertThat(new Request.ExtractField(FILE, SELECTION, "b").resolvedIn(ROOT))
                .isEqualTo(new Request.ExtractField(Path.of("/project/src/A.java"), SELECTION, "b"));
        assertThat(new Request.MoveTypeToFile(FILE, POSITION).resolvedIn(ROOT))
                .isEqualTo(new Request.MoveTypeToFile(Path.of("/project/src/A.java"), POSITION));
        assertThat(new Request.MoveToPackage(List.of(FILE, Path.of("src/B.java")), PackageName.of("demo.b"))
                        .resolvedIn(ROOT))
                .isEqualTo(new Request.MoveToPackage(
                        List.of(Path.of("/project/src/A.java"), Path.of("/project/src/B.java")),
                        PackageName.of("demo.b")));
        assertThat(new Request.Inline(FILE, POSITION).resolvedIn(ROOT))
                .isEqualTo(new Request.Inline(Path.of("/project/src/A.java"), POSITION));
        assertThat(new Request.CreateType(FILE, TypeKind.ENUM).resolvedIn(ROOT))
                .isEqualTo(new Request.CreateType(Path.of("/project/src/A.java"), TypeKind.ENUM));
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
    void resolvesTheChangesOfAMovedAnswerAgainstARoot() {
        TextEdit edit = new TextEdit(new Range(POSITION, POSITION), "class B {}");

        Answer resolved = new Answer.Moved(
                        set(new CreateFile(Path.of("src/B.java")), new EditFile(Path.of("src/B.java"), List.of(edit))))
                .resolvedIn(ROOT);

        assertThat(resolved)
                .isEqualTo(new Answer.Moved(set(
                        new CreateFile(Path.of("/project/src/B.java")),
                        new EditFile(Path.of("/project/src/B.java"), List.of(edit)))));
    }

    @Test
    void resolvesTheChangesOfAMovedToAPackageAnswerAgainstARoot() {
        Answer resolved = new Answer.MovedToPackage(set(new RenameFile(Path.of("src/B.java"), Path.of("src/b/B.java"))))
                .resolvedIn(ROOT);

        assertThat(resolved)
                .isEqualTo(new Answer.MovedToPackage(
                        set(new RenameFile(Path.of("/project/src/B.java"), Path.of("/project/src/b/B.java")))));
    }

    @Test
    void resolvesTheChangesOfACreatedAnswerAgainstARoot() {
        TextEdit edit = new TextEdit(new Range(POSITION, POSITION), "class B {}");

        Answer resolved = new Answer.Created(
                        set(new CreateFile(Path.of("src/B.java")), new EditFile(Path.of("src/B.java"), List.of(edit))))
                .resolvedIn(ROOT);

        assertThat(resolved)
                .isEqualTo(new Answer.Created(set(
                        new CreateFile(Path.of("/project/src/B.java")),
                        new EditFile(Path.of("/project/src/B.java"), List.of(edit)))));
    }

    @Test
    void resolvesTheChangesOfAnInlinedAnswerAgainstARoot() {
        TextEdit edit = new TextEdit(new Range(POSITION, POSITION), "");

        Answer resolved = new Answer.Inlined(set(new EditFile(Path.of("src/B.java"), List.of(edit)))).resolvedIn(ROOT);

        assertThat(resolved)
                .isEqualTo(new Answer.Inlined(set(new EditFile(Path.of("/project/src/B.java"), List.of(edit)))));
    }

    @Test
    void resolvesTheChangesOfAnOrganizedAnswerAgainstARoot() {
        TextEdit edit = new TextEdit(new Range(POSITION, POSITION), "import a.B;\n");

        Answer resolved = new Answer.Organized(set(new EditFile(FILE, List.of(edit)))).resolvedIn(ROOT);

        assertThat(resolved)
                .isEqualTo(new Answer.Organized(set(new EditFile(Path.of("/project/src/A.java"), List.of(edit)))));
    }

    @Test
    void resolvesTheChangesOfAnExtractedAnswerAgainstARoot() {
        TextEdit edit = new TextEdit(new Range(POSITION, POSITION), "int b = 1;\n");

        Answer resolved = new Answer.Extracted(set(new EditFile(FILE, List.of(edit)))).resolvedIn(ROOT);

        assertThat(resolved)
                .isEqualTo(new Answer.Extracted(set(new EditFile(Path.of("/project/src/A.java"), List.of(edit)))));
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

    @Test
    void rejectsAReferencedAnswerForAnotherRequestAndAnotherAnswerForReferences() {
        Request.Rename rename = new Request.Rename(FILE, POSITION, "B");
        Request.References references = new Request.References(FILE, POSITION, false);
        ReferenceLocations none = new ReferenceLocations(List.of(), List.of());

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(rename, new Answer.Referenced(none)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(references, new Answer.Renamed(set(new CreateFile(FILE)))));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(references, new Answer.Diagnosed(List.of())));
    }

    @Test
    void buildsTheRefusalOfAReferencesRequest() {
        Request.References references = new Request.References(FILE, POSITION, true);

        assertThat(references.refusal(LanguageServerErrorKey.NOT_READY).key())
                .isEqualTo(LanguageServerErrorKey.NOT_READY);
        assertThat(references.refusal(LanguageServerErrorKey.FILE_MISSING).key())
                .isEqualTo(LanguageServerErrorKey.FILE_MISSING);
        assertThat(references.refusal(LanguageServerErrorKey.NOT_A_FILE).key())
                .isEqualTo(LanguageServerErrorKey.NOT_A_FILE);
        assertThat(references
                        .refusal(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT)
                        .parameters())
                .isEqualTo(Map.of("file", FILE.toString()));
        assertThat(references.refusal(LanguageServerErrorKey.NO_SUCH_SYMBOL).parameters())
                .isEqualTo(Map.of("file", FILE.toString(), "line", "0", "column", "6"));
        for (var key : List.of(
                LanguageServerErrorKey.INVALID_NAME,
                LanguageServerErrorKey.NOT_EXTRACTABLE,
                LanguageServerErrorKey.NOT_INLINABLE,
                LanguageServerErrorKey.NOT_MOVABLE,
                LanguageServerErrorKey.NOT_CREATABLE,
                LanguageServerErrorKey.AMBIGUOUS_IMPORTS,
                LanguageServerErrorKey.UNRESOLVED_IMPORTS)) {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .as(key.toString())
                    .isThrownBy(() -> references.refusal(key));
        }
    }

    @Test
    void buildsTheRefusalsOfTheRequestsAboutCodeActions() {
        var actions = new Request.CodeActions(FILE, SELECTION);
        var apply = new Request.ApplyCodeAction(FILE, SELECTION, new OfferedAction("quickfix", "Fix it"));

        for (Request request : List.<Request>of(actions, apply)) {
            assertThat(request.refusal(LanguageServerErrorKey.NOT_READY).key())
                    .isEqualTo(LanguageServerErrorKey.NOT_READY);
            assertThat(request.refusal(LanguageServerErrorKey.FILE_MISSING).key())
                    .isEqualTo(LanguageServerErrorKey.FILE_MISSING);
            assertThat(request.refusal(LanguageServerErrorKey.NOT_A_FILE).key())
                    .isEqualTo(LanguageServerErrorKey.NOT_A_FILE);
            assertThat(request.refusal(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT)
                            .parameters())
                    .isEqualTo(Map.of("file", FILE.toString()));
        }
        assertThat(apply.refusal(LanguageServerErrorKey.NOT_APPLICABLE).parameters())
                .containsEntry("title", "Fix it")
                .containsEntry("line", "0")
                .containsEntry("endColumn", "7");
        for (var key : List.of(
                LanguageServerErrorKey.NO_SUCH_SYMBOL,
                LanguageServerErrorKey.INVALID_NAME,
                LanguageServerErrorKey.NOT_EXTRACTABLE,
                LanguageServerErrorKey.NOT_INLINABLE,
                LanguageServerErrorKey.NOT_MOVABLE,
                LanguageServerErrorKey.NOT_MOVABLE_TO_PACKAGE,
                LanguageServerErrorKey.AMBIGUOUS_IMPORTS,
                LanguageServerErrorKey.UNRESOLVED_IMPORTS,
                LanguageServerErrorKey.NOT_CREATABLE)) {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .as(key.toString())
                    .isThrownBy(() -> actions.refusal(key));
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .as(key.toString())
                    .isThrownBy(() -> apply.refusal(key));
        }
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> actions.refusal(LanguageServerErrorKey.NOT_APPLICABLE));
    }

    @Test
    void refusesAnActionThatIsNotApplicableOnlyForTheEditOfACodeAction() {
        var requests = List.<Request>of(
                new Request.Diagnostics(FILE),
                new Request.OrganizeImports(FILE),
                new Request.CleanUpFile(FILE, List.of(CleanUp.ADD_OVERRIDE)),
                new Request.Rename(FILE, POSITION, "B"),
                new Request.ExtractVariable(FILE, SELECTION, "v"),
                new Request.ExtractMethod(FILE, SELECTION, "m"),
                new Request.ExtractConstant(FILE, SELECTION, "C"),
                new Request.ExtractField(FILE, SELECTION, "f"),
                new Request.MoveTypeToFile(FILE, POSITION),
                new Request.MoveToPackage(List.of(FILE), PackageName.of("a.b")),
                new Request.Inline(FILE, POSITION),
                new Request.References(FILE, POSITION, false),
                new Request.CreateType(FILE, TypeKind.CLASS));

        for (Request request : requests) {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .as(request.toString())
                    .isThrownBy(() -> request.refusal(LanguageServerErrorKey.NOT_APPLICABLE));
        }
    }

    @Test
    void rejectsAnAnswerAboutCodeActionsThatDoesNotFitTheRequest() {
        var offered = new Answer.Offered(new OfferedActions(List.of()));
        var applied = new Answer.CodeActionApplied(set(new CreateFile(FILE)));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(new Request.Diagnostics(FILE), offered));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scenario(new Request.CodeActions(FILE, SELECTION), applied));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() ->
                        scenario(new Request.ApplyCodeAction(FILE, SELECTION, new OfferedAction("", "x")), offered));
        assertThat(scenario(new Request.CodeActions(FILE, SELECTION), offered).answer())
                .isSameAs(offered);
    }

    @Test
    void resolvesTheFileOfTheRequestsAboutCodeActionsAndOfTheEditOfTheAnswerAgainstARoot() {
        var action = new OfferedAction("quickfix", "Fix it");

        assertThat(new Request.CodeActions(FILE, SELECTION).resolvedIn(ROOT))
                .isEqualTo(new Request.CodeActions(Path.of("/project/src/A.java"), SELECTION));
        assertThat(new Request.ApplyCodeAction(FILE, SELECTION, action).resolvedIn(ROOT))
                .isEqualTo(new Request.ApplyCodeAction(Path.of("/project/src/A.java"), SELECTION, action));
        assertThat(new Answer.CodeActionApplied(set(new CreateFile(FILE))).resolvedIn(ROOT))
                .isEqualTo(new Answer.CodeActionApplied(set(new CreateFile(Path.of("/project/src/A.java")))));
    }

    @Test
    void resolvesTheFileOfAReferencesRequestAgainstARoot() {
        assertThat(new Request.References(FILE, POSITION, true).resolvedIn(ROOT))
                .isEqualTo(new Request.References(Path.of("/project/src/A.java"), POSITION, true));
    }

    @Test
    void resolvesTheLocationsOfAReferencedAnswerAgainstARoot() {
        Position elsewhere = new Position(3, 4);

        Answer resolved = new Answer.Referenced(new ReferenceLocations(
                        List.of(new Location(Path.of("src/A.java"), POSITION)),
                        List.of(new Location(Path.of("src/B.java"), elsewhere))))
                .resolvedIn(ROOT);

        assertThat(resolved)
                .isEqualTo(new Answer.Referenced(new ReferenceLocations(
                        List.of(new Location(Path.of("/project/src/A.java"), POSITION)),
                        List.of(new Location(Path.of("/project/src/B.java"), elsewhere)))));
    }

    @Test
    void rejectsAReferencedAnswerWithoutLocations() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Answer.Referenced(null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("locations"));
    }
}
