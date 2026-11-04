package eu.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.protocol.CodeActions.Action;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import eu.gabier.shared.error.domain.NullElementInCollectionException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CodeActionsTest {

    private static final Action FIX = new Action("quickfix", "Import 'List' (java.util)");
    private static final Action EXTRACTION =
            new Action("refactor.extract.variable", "Extract to local variable", "extract-variable");
    private static final Action HOSTILE = new Action("", "Fix \"it\" \u001b[31m");

    private static CodeActions readBack(CodeActions actions) {
        var response = (RpcResponse.Success) RpcResponse.parse(JsonRpc.success("1", actions.toJson()));

        return CodeActions.of(response.result());
    }

    @Test
    void nothingOfferedIsAnEmptyList() {
        assertThat(new CodeActions(List.of()).toJson()).isEqualTo("{\"actions\":[]}");
    }

    @Test
    void isWrittenWithTheKindAndTheTitleExactlyInTheOrderGiven() {
        var json = new CodeActions(List.of(FIX, HOSTILE)).toJson();

        assertThat(json).isEqualTo("""
            {"actions":[\
            {"kind":"quickfix","title":"Import 'List' (java.util)"},\
            {"kind":"","title":"Fix \\"it\\" \\u001b[31m"}]}\
            """);
        StrictJson.parse(json);
    }

    @Test
    void namesTheCommandOnlyOfTheActionsThatHaveOne() {
        var json = new CodeActions(List.of(FIX, EXTRACTION)).toJson();

        assertThat(json).isEqualTo("""
            {"actions":[\
            {"kind":"quickfix","title":"Import 'List' (java.util)"},\
            {"kind":"refactor.extract.variable","title":"Extract to local variable","command":"extract-variable"}]}\
            """);
        StrictJson.parse(json);
    }

    @Test
    void readsAnActionWithNoCommandMemberAsOneWithNoCommand() {
        var result = Map.<String, Object>of(
                "actions", List.of(Map.of("kind", "quickfix", "title", "Import 'List' (java.util)")));

        assertThat(CodeActions.of(result).actions()).containsExactly(FIX);
        assertThat(FIX.command()).isEmpty();
        assertThat(EXTRACTION.command()).isEqualTo("extract-variable");
    }

    @Test
    void isReadFromWhatItWrites() {
        var actions = new CodeActions(List.of(HOSTILE, FIX, EXTRACTION));

        assertThat(readBack(actions)).isEqualTo(actions);
    }

    @Test
    void isRefusedWhenTheListIsMissingOrNotAList() {
        for (var invalid : List.of(Map.<String, Object>of(), Map.<String, Object>of("actions", "none"))) {
            assertThatExceptionOfType(InvalidResponseException.class)
                    .as(invalid.toString())
                    .isThrownBy(() -> CodeActions.of(invalid));
        }
    }

    @Test
    void isRefusedWhenAnElementIsNotAnObjectOrHasAMemberMissingOrNotAString() {
        for (var element : List.of(
                "quickfix",
                Map.<String, Object>of("title", "t"),
                Map.<String, Object>of("kind", "k"),
                Map.<String, Object>of("kind", 1, "title", "t"),
                Map.<String, Object>of("kind", "k", "title", 1),
                Map.<String, Object>of("kind", "k", "title", "t", "command", 1))) {
            assertThatExceptionOfType(InvalidResponseException.class)
                    .as(element.toString())
                    .isThrownBy(() -> CodeActions.of(Map.of("actions", List.of(element))));
        }
    }

    @Test
    void isRefusedWithoutAKindATitleOrActions() {
        assertThatExceptionOfType(MissingMandatoryValueException.class).isThrownBy(() -> new Action(null, "t"));
        assertThatExceptionOfType(MissingMandatoryValueException.class).isThrownBy(() -> new Action("k", null));
        assertThatExceptionOfType(MissingMandatoryValueException.class).isThrownBy(() -> new Action("k", "t", null));
        assertThatExceptionOfType(MissingMandatoryValueException.class).isThrownBy(() -> new CodeActions(null));
        assertThatExceptionOfType(NullElementInCollectionException.class)
                .isThrownBy(() -> new CodeActions(Arrays.asList(FIX, null)));
    }
}
