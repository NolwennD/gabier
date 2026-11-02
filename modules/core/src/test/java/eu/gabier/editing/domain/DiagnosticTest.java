package eu.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.editing.domain.Diagnostic.Severity;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import org.junit.jupiter.api.Test;

class DiagnosticTest {

    private static final Position POSITION = new Position(3, 14);

    @Test
    void holdsWhatTheServerReported() {
        Diagnostic diagnostic = new Diagnostic(Severity.ERROR, POSITION, "Type mismatch");

        assertThat(diagnostic.severity()).isEqualTo(Severity.ERROR);
        assertThat(diagnostic.position()).isEqualTo(POSITION);
        assertThat(diagnostic.message()).isEqualTo("Type mismatch");
    }

    @Test
    void knowsTheFourSeveritiesOfTheProtocolFromTheMostSevere() {
        assertThat(Severity.values())
                .containsExactly(Severity.ERROR, Severity.WARNING, Severity.INFORMATION, Severity.HINT);
    }

    @Test
    void rejectsAMissingSeverity() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Diagnostic(null, POSITION, "Type mismatch"))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("severity"));
    }

    @Test
    void rejectsAMissingPosition() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Diagnostic(Severity.ERROR, null, "Type mismatch"))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("position"));
    }

    @Test
    void rejectsAMissingMessage() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Diagnostic(Severity.ERROR, POSITION, null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("message"));
    }
}
