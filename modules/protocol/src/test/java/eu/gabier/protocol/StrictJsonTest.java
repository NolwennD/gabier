package eu.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The proof "this is valid JSON" is worth something only if the helper refuses what is not. */
class StrictJsonTest {

    @Test
    void parsesAValidObject() {
        assertThat(StrictJson.parse("{\"a\":[1,\"b\"]}")
                        .getAsJsonObject()
                        .get("a")
                        .getAsJsonArray())
                .hasSize(2);
    }

    @Test
    void refusesSingleQuotes() {
        assertThatThrownBy(() -> StrictJson.parse("{'a':1}")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesAnUnquotedName() {
        assertThatThrownBy(() -> StrictJson.parse("{a:1}")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesATrailingComma() {
        assertThatThrownBy(() -> StrictJson.parse("{\"a\":1,}")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesAnUnescapedControlCharacter() {
        assertThatThrownBy(() -> StrictJson.parse("{\"a\":\"x\ny\"}")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesTextAfterTheValue() {
        assertThatThrownBy(() -> StrictJson.parse("{} {}")).isInstanceOf(IllegalArgumentException.class);
    }
}
