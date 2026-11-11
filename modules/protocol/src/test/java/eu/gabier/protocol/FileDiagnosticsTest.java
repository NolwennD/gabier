package eu.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.protocol.FileDiagnostics.Diagnosis;
import eu.gabier.protocol.FileDiagnostics.Severity;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import eu.gabier.shared.error.domain.NumberValueTooLowException;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileDiagnosticsTest {

    @Test
    void isRefusedWhenAPositionIsNegative() {
        assertThatExceptionOfType(NumberValueTooLowException.class)
                .isThrownBy(() -> new Diagnosis(Severity.ERROR, -1, 0, "m"));
        assertThatExceptionOfType(NumberValueTooLowException.class)
                .isThrownBy(() -> new Diagnosis(Severity.ERROR, 0, -1, "m"));
    }

    @Test
    void isRefusedWithoutASeverityOrAMessage() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Diagnosis(null, 0, 0, "m"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Diagnosis(Severity.ERROR, 0, 0, null));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new FileDiagnostics("/p/A.java", null));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new FileDiagnostics(" ", List.of()));
    }

    @Test
    void saysTheSeverityInLowerCase() {
        assertThat(Severity.INFORMATION.text()).isEqualTo("information");
    }
}
