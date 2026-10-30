package fr.craft.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.craft.gabier.protocol.FileDiagnostics.Diagnosis;
import fr.craft.gabier.protocol.FileDiagnostics.Severity;
import fr.craft.gabier.shared.error.domain.MissingMandatoryValueException;
import fr.craft.gabier.shared.error.domain.NumberValueTooLowException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FileDiagnosticsTest {

    private static final Diagnosis ERROR = new Diagnosis(Severity.ERROR, 4, 15, "Type mismatch: String to int");
    private static final Diagnosis WARNING = new Diagnosis(Severity.WARNING, 0, 0, "He said \"unused\"\nthen left");

    private static Map<String, Object> member(String severity, Object line, Object column, Object message) {
        return Map.of("severity", severity, "line", line, "column", column, "message", message);
    }

    private static Map<String, Object> result(Object diagnostics) {
        return Map.of("diagnostics", diagnostics);
    }

    private static FileDiagnostics readBack(FileDiagnostics diagnostics) {
        var response = (RpcResponse.Success) RpcResponse.parse(JsonRpc.success("1", diagnostics.toJson()));

        return FileDiagnostics.of(response.result());
    }

    @Test
    void aCleanFileIsAnEmptyList() {
        assertThat(new FileDiagnostics(List.of()).toJson()).isEqualTo("{\"diagnostics\":[]}");
    }

    @Test
    void isWrittenAsTheResultOfTheDiagnosticsMethodWithTheZeroBasedPositions() {
        var json = new FileDiagnostics(List.of(ERROR, WARNING)).toJson();

        assertThat(json).isEqualTo("""
            {"diagnostics":[\
            {"severity":"error","line":4,"column":15,"message":"Type mismatch: String to int"},\
            {"severity":"warning","line":0,"column":0,"message":"He said \\"unused\\"\\nthen left"}]}\
            """);
        StrictJson.parse(json);
    }

    @Test
    void isReadFromWhatItWrites() {
        var diagnostics = new FileDiagnostics(List.of(ERROR, WARNING));

        assertThat(readBack(diagnostics)).isEqualTo(diagnostics);
    }

    @Test
    void readsEverySeverity() {
        for (var severity : Severity.values()) {
            var diagnostics = new FileDiagnostics(List.of(new Diagnosis(severity, 1, 2, "m")));

            assertThat(readBack(diagnostics)).isEqualTo(diagnostics);
        }
    }

    @Test
    void isRefusedWhenTheListIsMissingOrNotAList() {
        for (var invalid : List.of(Map.<String, Object>of(), result("none"), result(Map.of()))) {
            assertThatExceptionOfType(InvalidResponseException.class).isThrownBy(() -> FileDiagnostics.of(invalid));
        }
    }

    @Test
    void isRefusedWhenAnElementIsNotAnObject() {
        assertThatExceptionOfType(InvalidResponseException.class)
                .isThrownBy(() -> FileDiagnostics.of(result(List.of("error"))));
    }

    @Test
    void isRefusedWhenAMemberOfAnElementIsMissingOrOfAnotherType() {
        var one = new BigDecimal("1");
        var invalid = List.of(
                Map.<String, Object>of("line", one, "column", one, "message", "m"),
                member("fatal", one, one, "m"),
                member("error", "1", one, "m"),
                member("error", one, "1", "m"),
                member("error", new BigDecimal("1.5"), one, "m"),
                member("error", new BigDecimal("1e20"), one, "m"),
                member("error", one, one, 7));

        for (var element : invalid) {
            assertThatExceptionOfType(InvalidResponseException.class)
                    .as(element.toString())
                    .isThrownBy(() -> FileDiagnostics.of(result(List.of(element))));
        }
    }

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
        assertThatExceptionOfType(MissingMandatoryValueException.class).isThrownBy(() -> new FileDiagnostics(null));
    }

    @Test
    void saysTheSeverityInLowerCase() {
        assertThat(Severity.INFORMATION.text()).isEqualTo("information");
    }
}
