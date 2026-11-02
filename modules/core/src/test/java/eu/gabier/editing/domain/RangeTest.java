package eu.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.shared.error.domain.ErrorStatus;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RangeTest {

    private static final Position START = new Position(2, 4);

    @Test
    void rejectsMissingStart() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Range(null, START))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("start"));
    }

    @Test
    void rejectsMissingEnd() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Range(START, null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("end"));
    }

    @Test
    void rejectsAnEndBeforeTheStart() {
        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> new Range(START, new Position(2, 3)))
                .satisfies(exception -> {
                    assertThat(exception.key()).isEqualTo(EditingErrorKey.INVALID_RANGE);
                    assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
                    assertThat(exception.parameters()).isEqualTo(Map.of("start", "2:4", "end", "2:3"));
                });
    }

    @Test
    void acceptsAnEmptyRangeAndAnOrderedOne() {
        assertThat(new Range(START, START).start()).isEqualTo(START);
        assertThat(new Range(START, new Position(3, 0)).end()).isEqualTo(new Position(3, 0));
    }
}
