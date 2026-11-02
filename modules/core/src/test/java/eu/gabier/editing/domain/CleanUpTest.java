package eu.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.shared.error.domain.ErrorStatus;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CleanUpTest {

    @Test
    void isIdentifiedByTheNameJdtlsKnowsItBy() {
        assertThat(CleanUp.STRING_CONCAT_TO_TEXT_BLOCK.id()).isEqualTo("stringConcatToTextBlock");
        assertThat(CleanUp.ADD_FINAL_MODIFIER.id()).isEqualTo("addFinalModifier");
    }

    @Test
    void hasOneIdentifierPerCleanUpAndNoneForTheOrganizationOfTheImports() {
        List<String> ids = Arrays.stream(CleanUp.values()).map(CleanUp::id).toList();

        assertThat(ids).doesNotHaveDuplicates().hasSize(19).doesNotContain("organizeImports");
        assertThat(CleanUp.ids()).isEqualTo(ids);
    }

    @Test
    void isFoundByItsIdentifier() {
        assertThat(CleanUp.of("stringConcatToTextBlock")).isEqualTo(CleanUp.STRING_CONCAT_TO_TEXT_BLOCK);
        assertThat(CleanUp.of("qualifyStaticMembers")).isEqualTo(CleanUp.QUALIFY_STATIC_MEMBERS);
    }

    @Test
    void ofAllKeepsTheOrderGiven() {
        assertThat(CleanUp.ofAll(List.of("invertEquals", "addOverride", "stringConcatToTextBlock")))
                .containsExactly(CleanUp.INVERT_EQUALS, CleanUp.ADD_OVERRIDE, CleanUp.STRING_CONCAT_TO_TEXT_BLOCK);
    }

    @Test
    void refusesAnIdentifierItDoesNotKnowAndNamesTheOnesItKnows() {
        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> CleanUp.of("stringConcatToTextBlok"))
                .satisfies(exception -> {
                    assertThat(exception.key()).isEqualTo(EditingErrorKey.UNKNOWN_CLEAN_UP);
                    assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
                    assertThat(exception.parameters())
                            .containsEntry("cleanUp", "stringConcatToTextBlok")
                            .containsEntry("known", String.join(", ", CleanUp.ids()));
                })
                .withMessageStartingWith("\"stringConcatToTextBlok\" is not a clean-up of the language server: ")
                .withMessageContaining("stringConcatToTextBlock");
    }

    @Test
    void refusesTheOrganizationOfTheImportsAsAnUnknownCleanUp() {
        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> CleanUp.of("organizeImports"))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.UNKNOWN_CLEAN_UP))
                .withMessageContaining("to organize the imports, use organize-imports");
    }

    @Test
    void refusesTheFirstIdentifierItDoesNotKnowWhateverComesAfter() {
        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> CleanUp.ofAll(List.of("addOverride", "nope", "alsoNope")))
                .satisfies(exception -> assertThat(exception.parameters()).containsEntry("cleanUp", "nope"));
    }
}
