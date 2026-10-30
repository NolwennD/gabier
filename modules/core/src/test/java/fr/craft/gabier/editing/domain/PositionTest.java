package fr.craft.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.craft.gabier.shared.error.domain.NumberValueTooLowException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PositionTest {

    @Test
    void rejectsNegativeLine() {
        assertThatExceptionOfType(NumberValueTooLowException.class)
                .isThrownBy(() -> new Position(-1, 0))
                .satisfies(exception -> {
                    assertThat(exception.field()).isEqualTo("line");
                    assertThat(exception.parameters()).isEqualTo(Map.of("min", "0", "value", "-1"));
                });
    }

    @Test
    void rejectsNegativeColumn() {
        assertThatExceptionOfType(NumberValueTooLowException.class)
                .isThrownBy(() -> new Position(0, -1))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("column"));
    }

    @Test
    void ordersByLineThenColumn() {
        assertThat(new Position(1, 9)).isLessThan(new Position(2, 0));
        assertThat(new Position(2, 3)).isLessThan(new Position(2, 4));
        assertThat(new Position(2, 4)).isGreaterThan(new Position(2, 3));
        assertThat(new Position(2, 4)).isEqualByComparingTo(new Position(2, 4));
    }
}
