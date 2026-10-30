package fr.craft.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProtocolVersionTest {

    @Nested
    class Validation {

        @Test
        void rejectsNegativeMajor() {
            assertThatIllegalArgumentException().isThrownBy(() -> new ProtocolVersion(-1, 0));
        }

        @Test
        void rejectsNegativeMinor() {
            assertThatIllegalArgumentException().isThrownBy(() -> new ProtocolVersion(0, -1));
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
