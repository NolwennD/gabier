package eu.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import eu.gabier.shared.error.domain.NumberValueTooLowException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class JdtlsProgressTest {

    @Test
    void saysWhatJdtlsDoesAndForHowLongInWords() {
        assertThat(new JdtlsProgress(
                                JdtlsProgress.Phase.IMPORTING, "Configure project :core", Duration.ofMillis(47_900))
                        .text())
                .isEqualTo("importing the project for 47s: Configure project :core");
        assertThat(new JdtlsProgress(JdtlsProgress.Phase.LAUNCHING, "Init...", Duration.ofSeconds(3)).text())
                .isEqualTo("launching jdtls for 3s: Init...");
    }

    @Test
    void saysNoMessageWhenJdtlsGaveNone() {
        assertThat(new JdtlsProgress(JdtlsProgress.Phase.LAUNCHING, "", Duration.ZERO).text())
                .isEqualTo("launching jdtls for 0s");
    }

    @Test
    void refusesWhatIsMissingOrNegative() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new JdtlsProgress(null, "", Duration.ZERO));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new JdtlsProgress(JdtlsProgress.Phase.IMPORTING, null, Duration.ZERO));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new JdtlsProgress(JdtlsProgress.Phase.IMPORTING, "", null));
        assertThatExceptionOfType(NumberValueTooLowException.class)
                .isThrownBy(() -> new JdtlsProgress(JdtlsProgress.Phase.IMPORTING, "", Duration.ofMillis(-1)));
    }
}
