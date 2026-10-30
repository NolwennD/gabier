package fr.craft.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.craft.gabier.shared.error.domain.NumberValueTooLowException;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProtocolVersionTest {

    @Nested
    class Validation {

        @Test
        void rejectsNegativeMajor() {
            assertThatExceptionOfType(NumberValueTooLowException.class)
                    .isThrownBy(() -> new ProtocolVersion(-1, 0))
                    .satisfies(exception -> {
                        assertThat(exception.field()).isEqualTo("major");
                        assertThat(exception.parameters()).isEqualTo(Map.of("min", "0", "value", "-1"));
                    });
        }

        @Test
        void rejectsNegativeMinor() {
            assertThatExceptionOfType(NumberValueTooLowException.class)
                    .isThrownBy(() -> new ProtocolVersion(0, -1))
                    .satisfies(exception -> {
                        assertThat(exception.field()).isEqualTo("minor");
                        assertThat(exception.parameters()).isEqualTo(Map.of("min", "0", "value", "-1"));
                    });
        }
    }

    @Nested
    class Rendering {

        @Test
        void rendersAsMajorDotMinor() {
            assertThat(new ProtocolVersion(2, 7)).hasToString("2.7");
        }

        @Test
        void currentIsZeroPointOne() {
            assertThat(ProtocolVersion.CURRENT).hasToString("0.1");
        }
    }
}
