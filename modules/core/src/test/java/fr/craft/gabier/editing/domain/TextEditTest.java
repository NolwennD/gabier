package fr.craft.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.craft.gabier.shared.error.domain.MissingMandatoryValueException;
import org.junit.jupiter.api.Test;

class TextEditTest {

    private static final Range RANGE = new Range(new Position(0, 0), new Position(0, 1));

    @Test
    void rejectsMissingRange() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new TextEdit(null, "x"))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("range"));
    }

    @Test
    void rejectsMissingReplacement() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new TextEdit(RANGE, null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("newText"));
    }

    @Test
    void acceptsAnEmptyReplacementWhichDeletesTheRange() {
        assertThat(new TextEdit(RANGE, "").newText()).isEmpty();
    }
}
