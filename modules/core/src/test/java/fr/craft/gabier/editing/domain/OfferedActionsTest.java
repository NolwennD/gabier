package fr.craft.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.craft.gabier.shared.error.domain.ErrorStatus;
import fr.craft.gabier.shared.error.domain.MissingMandatoryValueException;
import fr.craft.gabier.shared.error.domain.NullElementInCollectionException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OfferedActionsTest {

    private static final OfferedAction IMPORT = new OfferedAction("quickfix", "Import 'List' (java.util)");
    private static final OfferedAction CREATE = new OfferedAction("quickfix", "Create class 'List<T>'");
    private static final OfferedAction TEXT_BLOCK = new OfferedAction("quickassist", "Convert to Text Block");

    @Test
    void findsTheOnlyActionWithATitle() {
        var offered = new OfferedActions(List.of(IMPORT, CREATE, TEXT_BLOCK));

        assertThat(offered.titled("Create class 'List<T>'")).isEqualTo(CREATE);
    }

    @Test
    void refusesATitleNoActionHasAndListsWhatIsOffered() {
        var offered = new OfferedActions(List.of(IMPORT, new OfferedAction("", "A bare one")));

        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> offered.titled("Import List"))
                .satisfies(exception -> {
                    assertThat(exception.key()).isEqualTo(EditingErrorKey.NO_SUCH_CODE_ACTION);
                    assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
                    assertThat(exception.parameters())
                            .isEqualTo(Map.of(
                                    "title",
                                    "Import List",
                                    "choices",
                                    "quickfix: Import 'List' (java.util)\nA bare one"));
                })
                .withMessage("The language server offers no action titled \"Import List\" on this range: it offers"
                        + " quickfix: Import 'List' (java.util); A bare one");
    }

    @Test
    void refusesATitleWhenNothingIsOffered() {
        var nothing = new OfferedActions(List.of());

        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> nothing.titled("Anything"))
                .satisfies(exception -> assertThat(exception.parameters()).containsEntry("choices", ""))
                .withMessage("The language server offers no action titled \"Anything\" on this range: it offers none");
    }

    @Test
    void refusesATitleSeveralActionsHaveAndListsThose() {
        var twice = new OfferedAction("source", "Import 'List' (java.util)");
        var offered = new OfferedActions(List.of(IMPORT, CREATE, twice));

        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> offered.titled("Import 'List' (java.util)"))
                .satisfies(exception -> {
                    assertThat(exception.key()).isEqualTo(EditingErrorKey.AMBIGUOUS_CODE_ACTION);
                    assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
                    assertThat(exception.parameters())
                            .isEqualTo(Map.of(
                                    "title",
                                    "Import 'List' (java.util)",
                                    "choices",
                                    "quickfix: Import 'List' (java.util)\nsource: Import 'List' (java.util)"));
                })
                .withMessage("The language server offers several actions titled \"Import 'List' (java.util)\" on this"
                        + " range: quickfix: Import 'List' (java.util); source: Import 'List' (java.util). Try a"
                        + " narrower range");
    }

    @Test
    void holdsItsActionsInTheOrderGiven() {
        assertThat(new OfferedActions(List.of(TEXT_BLOCK, IMPORT)).actions()).containsExactly(TEXT_BLOCK, IMPORT);
    }

    @Test
    void rejectsMissingActionsAndATitleThatIsBlankOrMissing() {
        var nothing = new OfferedActions(List.of());

        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new OfferedActions(null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("actions"));
        assertThatExceptionOfType(NullElementInCollectionException.class)
                .isThrownBy(() -> new OfferedActions(Arrays.asList(IMPORT, null)))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("actions"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> nothing.titled(" "))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("title"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new OfferedAction(null, "x"))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("kind"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new OfferedAction("quickfix", " "))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("title"));
    }
}
