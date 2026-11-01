package fr.craft.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.shared.error.domain.ErrorStatus;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LanguageServerExceptionTest {

    private static final Path FILE = Path.of("/project/src/A.java");

    @Test
    void givesTheKeyThatReportsAreMappedFrom() {
        assertThat(Arrays.stream(LanguageServerErrorKey.values()).map(LanguageServerErrorKey::get))
                .containsExactly(
                        "language-server-ambiguous-imports",
                        "language-server-file-missing",
                        "language-server-file-outside-project",
                        "language-server-invalid-name",
                        "language-server-no-such-symbol",
                        "language-server-not-an-expression",
                        "language-server-not-creatable",
                        "language-server-not-inlinable",
                        "language-server-not-extractable",
                        "language-server-not-a-file",
                        "language-server-not-movable",
                        "language-server-not-ready",
                        "language-server-unresolved-imports");
    }

    @Test
    void saysThatTheServerIsStillImportingTheProject() {
        LanguageServerException exception = LanguageServerException.notReady();

        assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NOT_READY);
        assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(exception.parameters()).isEmpty();
        assertThat(exception).hasMessage("The language server is still importing the project: try again later");
    }

    @Test
    void saysWhichPositionHoldsNoSymbol() {
        LanguageServerException exception = LanguageServerException.noSuchSymbol(FILE, new Position(4, 7));

        assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NO_SUCH_SYMBOL);
        assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(exception.parameters()).isEqualTo(Map.of("file", FILE.toString(), "line", "4", "column", "7"));
        assertThat(exception).hasMessage("There is no symbol at 4:7 in " + FILE);
    }

    @Test
    void saysWhichSelectionIsNotAnExpression() {
        LanguageServerException exception =
                LanguageServerException.notAnExpression(FILE, new Range(new Position(4, 7), new Position(5, 2)));

        assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NOT_AN_EXPRESSION);
        assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(exception.parameters())
                .isEqualTo(
                        Map.of("file", FILE.toString(), "line", "4", "column", "7", "endLine", "5", "endColumn", "2"));
        assertThat(exception).hasMessage("The selection 4:7-5:2 of " + FILE + " is not an expression");
    }

    @Test
    void saysWhichSelectionCannotBeExtractedIntoAMethod() {
        LanguageServerException exception =
                LanguageServerException.notExtractable(FILE, new Range(new Position(4, 7), new Position(5, 2)));

        assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NOT_EXTRACTABLE);
        assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(exception.parameters())
                .isEqualTo(
                        Map.of("file", FILE.toString(), "line", "4", "column", "7", "endLine", "5", "endColumn", "2"));
        assertThat(exception)
                .hasMessage("The selection 4:7-5:2 of " + FILE + " is not statements or an expression to extract");
    }

    @Test
    void saysWhichTypeCannotBeMovedToAFileOfItsOwn() {
        LanguageServerException exception = LanguageServerException.notMovable(FILE, new Position(4, 7));

        assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NOT_MOVABLE);
        assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(exception.parameters()).isEqualTo(Map.of("file", FILE.toString(), "line", "4", "column", "7"));
        assertThat(exception)
                .hasMessage("No nested type at 4:7 in " + FILE + " can be moved to a file of its own: the position is"
                        + " outside any type, the type is a top-level type, or a file of its name exists in its"
                        + " package");
    }

    @Test
    void saysWhichPositionHoldsNothingTheServerCanInline() {
        LanguageServerException exception = LanguageServerException.notInlinable(FILE, new Position(4, 7));

        assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NOT_INLINABLE);
        assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(exception.parameters()).isEqualTo(Map.of("file", FILE.toString(), "line", "4", "column", "7"));
        assertThat(exception)
                .hasMessage("Nothing at 4:7 in " + FILE + " can be inlined: the position is not on a local variable,"
                        + " a method or a constant, or the server refuses it (a variable assigned more than once, a"
                        + " method that is recursive or abstract, a declaration that nothing uses)");
    }

    @Test
    void saysWhichKindOfTypeTheServerCannotCreateWhere() {
        LanguageServerException exception = LanguageServerException.notCreatable(FILE, TypeKind.RECORD);

        assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NOT_CREATABLE);
        assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(exception.parameters()).isEqualTo(Map.of("file", FILE.toString(), "kind", "record"));
        assertThat(exception)
                .hasMessage("A record cannot be created in " + FILE + ": the file is outside every source folder of"
                        + " the project, or the Java level of the project has no such type");
    }

    @Test
    void saysWhichNameIsNotAcceptedForTheSymbol() {
        LanguageServerException exception = LanguageServerException.invalidName(FILE, new Position(4, 7), "1st");

        assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.INVALID_NAME);
        assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(exception.parameters())
                .isEqualTo(Map.of("file", FILE.toString(), "line", "4", "column", "7", "name", "1st"));
        assertThat(exception).hasMessage("\"1st\" is not a valid name for the symbol at 4:7 in " + FILE);
    }

    @Test
    void saysWhichFileIsNotInTheProject() {
        LanguageServerException exception = LanguageServerException.fileOutsideProject(FILE);

        assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT);
        assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(exception.parameters()).isEqualTo(Map.of("file", FILE.toString()));
        assertThat(exception).hasMessage("The file " + FILE + " is not in the project of the language server");
    }

    @Test
    void saysWhichFileDoesNotExist() {
        LanguageServerException exception = LanguageServerException.fileMissing(FILE);

        assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.FILE_MISSING);
        assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(exception.parameters()).isEqualTo(Map.of("file", FILE.toString()));
        assertThat(exception).hasMessage("The file " + FILE + " does not exist");
    }

    @Test
    void saysWhichPathIsNotAFile() {
        LanguageServerException exception = LanguageServerException.notAFile(FILE);

        assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NOT_A_FILE);
        assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(exception.parameters()).isEqualTo(Map.of("file", FILE.toString()));
        assertThat(exception).hasMessage(FILE + " is not a file");
    }

    @Test
    void namesTheImportsTheServerCannotResolve() {
        LanguageServerException exception =
                LanguageServerException.unresolvedImports(FILE, List.of("com.nowhere.Missing", "org.gone.Lost"));

        assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.UNRESOLVED_IMPORTS);
        assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(exception.parameters())
                .isEqualTo(Map.of("file", FILE.toString(), "imports", "com.nowhere.Missing, org.gone.Lost"));
        assertThat(exception)
                .hasMessage("The language server cannot resolve the imports com.nowhere.Missing, org.gone.Lost of "
                        + FILE + ": organizing them would delete them");
    }

    @Test
    void namesTheCandidatesOfEachTypeThatSeveralPackagesDeclare() {
        LanguageServerException exception = LanguageServerException.ambiguousImports(
                FILE, List.of("java.awt.List", "java.util.List", "java.sql.Date", "java.util.Date"));

        assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.AMBIGUOUS_IMPORTS);
        assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(exception.parameters())
                .isEqualTo(Map.of(
                        "file",
                        FILE.toString(),
                        "candidates",
                        "java.awt.List, java.util.List, java.sql.Date, java.util.Date"));
        assertThat(exception)
                .hasMessage("The file " + FILE + " uses types that several packages declare, so none was imported: "
                        + "List (java.awt.List, java.util.List); Date (java.sql.Date, java.util.Date)");
    }
}
