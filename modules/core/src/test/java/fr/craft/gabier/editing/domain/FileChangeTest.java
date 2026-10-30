package fr.craft.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.craft.gabier.editing.domain.FileChange.CreateFile;
import fr.craft.gabier.editing.domain.FileChange.DeleteFile;
import fr.craft.gabier.editing.domain.FileChange.EditFile;
import fr.craft.gabier.editing.domain.FileChange.RenameFile;
import fr.craft.gabier.shared.error.domain.MissingMandatoryValueException;
import fr.craft.gabier.shared.error.domain.NullElementInCollectionException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FileChangeTest {

    private static final Path FILE = Path.of("/project/A.java");
    private static final TextEdit EDIT = new TextEdit(new Range(new Position(0, 0), new Position(0, 1)), "x");

    @Nested
    class EditingAFile {

        @Test
        void rejectsMissingFile() {
            assertThatExceptionOfType(MissingMandatoryValueException.class)
                    .isThrownBy(() -> new EditFile(null, List.of(EDIT)))
                    .satisfies(exception -> assertThat(exception.field()).isEqualTo("file"));
        }

        @Test
        void rejectsMissingEdits() {
            assertThatExceptionOfType(MissingMandatoryValueException.class)
                    .isThrownBy(() -> new EditFile(FILE, null))
                    .satisfies(exception -> assertThat(exception.field()).isEqualTo("edits"));
        }

        @Test
        void rejectsNoEdit() {
            assertThatExceptionOfType(MissingMandatoryValueException.class)
                    .isThrownBy(() -> new EditFile(FILE, List.of()))
                    .satisfies(exception -> assertThat(exception.field()).isEqualTo("edits"));
        }

        @Test
        void rejectsANullEdit() {
            List<TextEdit> edits = Arrays.asList(EDIT, null);

            assertThatExceptionOfType(NullElementInCollectionException.class)
                    .isThrownBy(() -> new EditFile(FILE, edits))
                    .satisfies(exception -> assertThat(exception.field()).isEqualTo("edits"));
        }

        @Test
        void keepsACopyOfTheEdits() {
            List<TextEdit> edits = new ArrayList<>(List.of(EDIT));

            EditFile change = new EditFile(FILE, edits);
            edits.clear();

            assertThat(change.edits()).containsExactly(EDIT);
        }
    }

    @Nested
    class CreatingAFile {

        @Test
        void rejectsMissingFile() {
            assertThatExceptionOfType(MissingMandatoryValueException.class)
                    .isThrownBy(() -> new CreateFile(null))
                    .satisfies(exception -> assertThat(exception.field()).isEqualTo("file"));
        }
    }

    @Nested
    class RenamingAFile {

        @Test
        void rejectsMissingSource() {
            assertThatExceptionOfType(MissingMandatoryValueException.class)
                    .isThrownBy(() -> new RenameFile(null, FILE))
                    .satisfies(exception -> assertThat(exception.field()).isEqualTo("from"));
        }

        @Test
        void rejectsMissingTarget() {
            assertThatExceptionOfType(MissingMandatoryValueException.class)
                    .isThrownBy(() -> new RenameFile(FILE, null))
                    .satisfies(exception -> assertThat(exception.field()).isEqualTo("to"));
        }
    }

    @Nested
    class DeletingAFile {

        @Test
        void rejectsMissingFile() {
            assertThatExceptionOfType(MissingMandatoryValueException.class)
                    .isThrownBy(() -> new DeleteFile(null))
                    .satisfies(exception -> assertThat(exception.field()).isEqualTo("file"));
        }
    }
}
