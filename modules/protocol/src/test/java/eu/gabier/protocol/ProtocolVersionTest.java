package eu.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import eu.gabier.shared.error.domain.NotMatchingExpectedPatternException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProtocolVersionTest {

    @Nested
    class Validation {

        @ParameterizedTest
        @ValueSource(
                strings = {"0.1.0", "1.20.300", "0.0.0-dev", "1.0.0-rc.1", "1.0.0+build.5", "2.1.0-beta.2+sha.abc"})
        void acceptsASemanticVersion(String value) {
            assertThat(new ProtocolVersion(value)).hasToString(value);
        }

        @ParameterizedTest
        @ValueSource(strings = {"v0.1.0", "0.1", "01.2.3", "1.2.3.4", "1.2.3-", "latest", " 0.1.0", "0.1.0\n"})
        void rejectsWhatIsNotASemanticVersion(String value) {
            assertThatExceptionOfType(NotMatchingExpectedPatternException.class)
                    .isThrownBy(() -> new ProtocolVersion(value))
                    .satisfies(exception -> assertThat(exception.field()).isEqualTo("value"));
        }

        @Test
        void rejectsNoValue() {
            assertThatExceptionOfType(MissingMandatoryValueException.class).isThrownBy(() -> new ProtocolVersion(null));
        }
    }

    @Nested
    class Current {

        @Test
        void isTheVersionTheBuildWasGiven() {
            assertThat(ProtocolVersion.CURRENT).hasToString(System.getProperty("gabier.version"));
        }
    }

    @Nested
    class Reading {

        @Test
        void readsTheVersionOfTheResource() {
            var resource = new ByteArrayInputStream("version=1.4.2\n".getBytes(StandardCharsets.UTF_8));

            assertThat(ProtocolVersion.read(resource)).isEqualTo(new ProtocolVersion("1.4.2"));
        }

        @Test
        void refusesAResourceThatIsNotThere() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> ProtocolVersion.read(null))
                    .withMessageContaining("version.properties");
        }

        @Test
        void refusesAResourceWithNoVersion() {
            var resource = new ByteArrayInputStream("other=1.4.2\n".getBytes(StandardCharsets.UTF_8));

            assertThatIllegalStateException()
                    .isThrownBy(() -> ProtocolVersion.read(resource))
                    .withMessageContaining("version.properties");
        }

        @Test
        void refusesAResourceThatTheBuildLeftUnfilled() {
            var resource = new ByteArrayInputStream("version=@version@\n".getBytes(StandardCharsets.UTF_8));

            assertThatExceptionOfType(NotMatchingExpectedPatternException.class)
                    .isThrownBy(() -> ProtocolVersion.read(resource));
        }

        @Test
        void reportsAResourceThatCannotBeRead() {
            var broken = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("disk");
                }
            };

            assertThatExceptionOfType(UncheckedIOException.class).isThrownBy(() -> ProtocolVersion.read(broken));
        }
    }
}
