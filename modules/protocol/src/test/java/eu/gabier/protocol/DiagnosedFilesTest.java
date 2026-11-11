package eu.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.protocol.FileDiagnostics.Diagnosis;
import eu.gabier.protocol.FileDiagnostics.Severity;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiagnosedFilesTest {

    private static final Diagnosis ERROR = new Diagnosis(Severity.ERROR, 4, 15, "Type mismatch: String to int");
    private static final Diagnosis WARNING = new Diagnosis(Severity.WARNING, 0, 0, "He said \"unused\"\nthen left");

    private static Map<String, Object> member(String severity, Object line, Object column, Object message) {
        return Map.of("severity", severity, "line", line, "column", column, "message", message);
    }

    private static Map<String, Object> file(Object name, Object diagnostics) {
        return Map.of("file", name, "diagnostics", diagnostics);
    }

    private static Map<String, Object> result(Object files) {
        return Map.of("files", files);
    }

    private static DiagnosedFiles readBack(DiagnosedFiles diagnosed) {
        var response = (RpcResponse.Success) RpcResponse.parse(JsonRpc.success("1", diagnosed.toJson()));

        return DiagnosedFiles.of(response.result());
    }

    @Test
    void aCleanFileIsAnEmptyList() {
        assertThat(new DiagnosedFiles(List.of(new FileDiagnostics("/p/A.java", List.of()))).toJson())
                .isEqualTo("{\"files\":[{\"file\":\"/p/A.java\",\"diagnostics\":[]}]}");
    }

    @Test
    void isWrittenAsTheResultOfTheDiagnosticsMethodWithTheZeroBasedPositions() {
        var json = new DiagnosedFiles(List.of(
                        new FileDiagnostics("/p/A.java", List.of(ERROR, WARNING)),
                        new FileDiagnostics("/p/B.java", List.of())))
                .toJson();

        assertThat(json).isEqualTo("""
            {"files":[\
            {"file":"/p/A.java","diagnostics":[\
            {"severity":"error","line":4,"column":15,"message":"Type mismatch: String to int"},\
            {"severity":"warning","line":0,"column":0,"message":"He said \\"unused\\"\\nthen left"}]},\
            {"file":"/p/B.java","diagnostics":[]}]}\
            """);
        StrictJson.parse(json);
    }

    @Test
    void keepsTheOrderOfTheFilesAndAFileGivenTwiceTwice() {
        var diagnosed = new DiagnosedFiles(List.of(
                new FileDiagnostics("/p/B.java", List.of(ERROR)),
                new FileDiagnostics("/p/A.java", List.of()),
                new FileDiagnostics("/p/B.java", List.of(ERROR))));

        assertThat(readBack(diagnosed)).isEqualTo(diagnosed);
        assertThat(readBack(diagnosed).files())
                .extracting(FileDiagnostics::file)
                .containsExactly("/p/B.java", "/p/A.java", "/p/B.java");
    }

    @Test
    void readsEverySeverity() {
        for (var severity : Severity.values()) {
            var diagnosed = new DiagnosedFiles(
                    List.of(new FileDiagnostics("/p/A.java", List.of(new Diagnosis(severity, 1, 2, "m")))));

            assertThat(readBack(diagnosed)).isEqualTo(diagnosed);
        }
    }

    @Test
    void isRefusedWhenTheListOfFilesIsMissingOrNotAList() {
        for (var invalid : List.of(Map.<String, Object>of(), result("none"), result(Map.of()))) {
            assertThatExceptionOfType(InvalidResponseException.class).isThrownBy(() -> DiagnosedFiles.of(invalid));
        }
    }

    @Test
    void isRefusedWhenAFileIsNotAnObjectOrHasNoNameOrNoListOfProblems() {
        var invalid = List.of(
                "A.java",
                file(7, List.of()),
                file(" ", List.of()),
                Map.<String, Object>of("diagnostics", List.of()),
                Map.<String, Object>of("file", "/p/A.java"),
                file("/p/A.java", "none"));

        for (var element : invalid) {
            assertThatExceptionOfType(InvalidResponseException.class)
                    .as(element.toString())
                    .isThrownBy(() -> DiagnosedFiles.of(result(List.of(element))));
        }
    }

    @Test
    void isRefusedWhenAProblemIsNotAnObject() {
        assertThatExceptionOfType(InvalidResponseException.class)
                .isThrownBy(() -> DiagnosedFiles.of(result(List.of(file("/p/A.java", List.of("error"))))));
    }

    @Test
    void isRefusedWhenAMemberOfAProblemIsMissingOrOfAnotherType() {
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
                    .isThrownBy(() -> DiagnosedFiles.of(result(List.of(file("/p/A.java", List.of(element))))));
        }
    }

    @Test
    void isRefusedWithoutItsFiles() {
        assertThatExceptionOfType(MissingMandatoryValueException.class).isThrownBy(() -> new DiagnosedFiles(null));
    }
}
