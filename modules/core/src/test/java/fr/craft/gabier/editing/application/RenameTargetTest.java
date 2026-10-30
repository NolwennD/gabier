package fr.craft.gabier.editing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.craft.gabier.editing.application.RenameTarget.At;
import fr.craft.gabier.editing.application.RenameTarget.Token;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.shared.error.domain.MissingMandatoryValueException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RenameTargetTest {

    private static final Path FILE = Path.of("/project/A.java");

    @Test
    void namesASymbolByItsPosition() {
        RenameTarget target = new At(FILE, new Position(3, 4));

        assertThat(target.file()).isEqualTo(FILE);
    }

    @Test
    void namesASymbolByAnIdentifierOfTheFile() {
        RenameTarget target = new Token(FILE, "count");

        assertThat(target.file()).isEqualTo(FILE);
    }

    @Test
    void rejectsAMissingFileOrPosition() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new At(null, new Position(0, 0)))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("file"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new At(FILE, null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("position"));
    }

    @Test
    void rejectsAMissingFileOrABlankIdentifier() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Token(null, "count"))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("file"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Token(FILE, " "))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("identifier"));
    }
}
