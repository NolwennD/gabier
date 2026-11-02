package eu.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.protocol.FoundReferences.Place;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import eu.gabier.shared.error.domain.NumberValueTooLowException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FoundReferencesTest {

    private static final Place DECLARATION = new Place("/work/demo/Ledger.java", 9, 16, "    public void deposit() {");
    private static final Place USE =
            new Place("/work/demo/Bank.java", 7, 15, "        ledger.deposit(\"x\"); // \u001b");

    private static Map<String, Object> place(Object file, Object line, Object column, Object source) {
        return Map.of("file", file, "line", line, "column", column, "source", source);
    }

    private static Map<String, Object> result(Object declarations, Object references) {
        return Map.of("declarations", declarations, "references", references);
    }

    private static FoundReferences readBack(FoundReferences found) {
        var response = (RpcResponse.Success) RpcResponse.parse(JsonRpc.success("1", found.toJson()));

        return FoundReferences.of(response.result());
    }

    @Test
    void noReferenceIsTwoEmptyLists() {
        assertThat(new FoundReferences(List.of(), List.of()).toJson())
                .isEqualTo("{\"declarations\":[],\"references\":[]}");
    }

    @Test
    void isWrittenWithTheZeroBasedPositionsAndTheSourceLineExactly() {
        var json = new FoundReferences(List.of(DECLARATION), List.of(USE)).toJson();

        assertThat(json).isEqualTo("""
            {"declarations":[\
            {"file":"/work/demo/Ledger.java","line":9,"column":16,"source":"    public void deposit() {"}],\
            "references":[\
            {"file":"/work/demo/Bank.java","line":7,"column":15,"source":"        ledger.deposit(\\"x\\"); // \\u001b"}]}\
            """);
        StrictJson.parse(json);
    }

    @Test
    void isReadFromWhatItWrites() {
        var found = new FoundReferences(List.of(DECLARATION), List.of(USE, DECLARATION));

        assertThat(readBack(found)).isEqualTo(found);
    }

    @Test
    void isRefusedWhenAListIsMissingOrNotAList() {
        for (var invalid : List.of(
                Map.<String, Object>of(),
                Map.<String, Object>of("declarations", List.of()),
                Map.<String, Object>of("references", List.of()),
                result("none", List.of()),
                result(List.of(), Map.of()))) {
            assertThatExceptionOfType(InvalidResponseException.class)
                    .as(invalid.toString())
                    .isThrownBy(() -> FoundReferences.of(invalid));
        }
    }

    @Test
    void isRefusedWhenAnElementIsNotAnObject() {
        assertThatExceptionOfType(InvalidResponseException.class)
                .isThrownBy(() -> FoundReferences.of(result(List.of(), List.of("Bank.java"))));
    }

    @Test
    void isRefusedWhenAMemberOfAnElementIsMissingOrOfAnotherType() {
        var one = new BigDecimal("1");
        var invalid = List.of(
                Map.<String, Object>of("line", one, "column", one, "source", "s"),
                place(7, one, one, "s"),
                place("f", "1", one, "s"),
                place("f", one, "1", "s"),
                place("f", new BigDecimal("1.5"), one, "s"),
                place("f", new BigDecimal("1e20"), one, "s"),
                place("f", one, one, 7));

        for (var element : invalid) {
            assertThatExceptionOfType(InvalidResponseException.class)
                    .as(element.toString())
                    .isThrownBy(() -> FoundReferences.of(result(List.of(), List.of(element))));
        }
    }

    @Test
    void isRefusedWhenAPositionIsNegative() {
        assertThatExceptionOfType(NumberValueTooLowException.class).isThrownBy(() -> new Place("f", -1, 0, "s"));
        assertThatExceptionOfType(NumberValueTooLowException.class).isThrownBy(() -> new Place("f", 0, -1, "s"));
    }

    @Test
    void isRefusedWithoutAFileASourceOrALists() {
        assertThatExceptionOfType(MissingMandatoryValueException.class).isThrownBy(() -> new Place(null, 0, 0, "s"));
        assertThatExceptionOfType(MissingMandatoryValueException.class).isThrownBy(() -> new Place("f", 0, 0, null));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new FoundReferences(null, List.of()));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new FoundReferences(List.of(), null));
    }
}
