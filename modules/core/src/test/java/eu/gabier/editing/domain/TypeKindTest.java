package eu.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TypeKindTest {

    @Test
    void isOneOfTheFourKindsOfTypeInTheOrderOfTheHelp() {
        assertThat(Arrays.stream(TypeKind.values()).map(TypeKind::keyword))
                .containsExactly("class", "interface", "record", "enum");
    }

    @Test
    void isFoundByItsKeyword() {
        assertThat(TypeKind.ofKeyword("class")).contains(TypeKind.CLASS);
        assertThat(TypeKind.ofKeyword("interface")).contains(TypeKind.INTERFACE);
        assertThat(TypeKind.ofKeyword("record")).contains(TypeKind.RECORD);
        assertThat(TypeKind.ofKeyword("enum")).contains(TypeKind.ENUM);
    }

    @Test
    void isNotFoundByAnythingElse() {
        assertThat(TypeKind.ofKeyword("Class")).isEqualTo(Optional.empty());
        assertThat(TypeKind.ofKeyword("annotation")).isEmpty();
        assertThat(TypeKind.ofKeyword("")).isEmpty();
    }
}
