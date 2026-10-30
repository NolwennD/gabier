package fr.craft.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.entry;

import fr.craft.gabier.shared.error.domain.ErrorStatus;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonTest {

    @Nested
    class Quote {

        @Test
        void surroundsTheTextWithQuotationMarks() {
            assertThat(Json.quote("abc")).isEqualTo("\"abc\"");
        }

        @Test
        void escapesWhatRfc8259Requires() {
            assertThat(Json.quote("\"\\\n\r\t\u0001")).isEqualTo("\"\\\"\\\\\\n\\r\\t\\u0001\"");
        }

        @Test
        void escapesDelAndTheC1ControlCharactersSoThatNoTerminalObeysThem() {
            assertThat(Json.quote("a\u007fb\u0080c\u009bd\u009fe")).isEqualTo("\"a\\u007fb\\u0080c\\u009bd\\u009fe\"");
        }

        @Test
        void leavesTheCharactersAroundTheC1RangeAsTheyAre() {
            assertThat(Json.quote("~ ")).isEqualTo("\"~ \"");
        }

        @Test
        void leavesTheOtherCharactersAsTheyAre() {
            assertThat(Json.quote("é/\u2028")).isEqualTo("\"é/\u2028\"");
        }
    }

    @Nested
    class Parse {

        @Test
        void readsAnObjectInTheOrderOfItsMembers() {
            var parsed = Json.parse("{\"b\":1,\"a\":2}");

            assertThat(parsed)
                    .asInstanceOf(InstanceOfAssertFactories.MAP)
                    .containsExactly(entry("b", new BigDecimal("1")), entry("a", new BigDecimal("2")));
        }

        @Test
        void readsAnArray() {
            assertThat(Json.parse("[1,\"a\",null,true,false,[],{}]"))
                    .isEqualTo(Arrays.asList(new BigDecimal("1"), "a", null, true, false, List.of(), Map.of()));
        }

        @Test
        void readsTheNullLiteralAsNull() {
            assertThat(Json.parse("null")).isNull();
        }

        @Test
        void readsNumbersWithoutLosingTheirDigits() {
            assertThat(Json.parse("12345678901234567890")).isEqualTo(new BigDecimal("12345678901234567890"));
            assertThat(Json.parse("-0.5e+2")).isEqualTo(new BigDecimal("-0.5e+2"));
        }

        @Test
        void readsTheEscapesOfAString() {
            assertThat(Json.parse("\"\\\"\\\\\\/\\b\\f\\n\\r\\t\\u00e9\"")).isEqualTo("\"\\/\b\f\n\r\té");
        }

        @Test
        void readsHexadecimalDigitsInEitherCase() {
            assertThat(Json.parse("\"\\u00E9\\u00e9\"")).isEqualTo("éé");
        }

        @Test
        void readsASurrogatePairWrittenAsTwoEscapes() {
            assertThat(Json.parse("\"\\ud83d\\ude00\"")).isEqualTo("\uD83D\uDE00");
        }

        @Test
        void readsCharactersOutsideAsciiAsTheyAre() {
            assertThat(Json.parse("\"é\uD83D\uDE00\"")).isEqualTo("é\uD83D\uDE00");
        }

        @Test
        void ignoresTheWhitespaceBetweenTokens() {
            assertThat(Json.parse(" \t\r\n[ 1 ,\n2 ] \n")).isEqualTo(List.of(new BigDecimal("1"), new BigDecimal("2")));
        }

        @Test
        void readsWhatItWrites() {
            assertThat(Json.parse(Json.quote("a\"b\\c\nd\u0001é"))).isEqualTo("a\"b\\c\nd\u0001é");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "{}",
                    "[]",
                    "\"a\"",
                    "0",
                    "-0",
                    "10",
                    "1.5",
                    "1e5",
                    "1E+5",
                    "1.5e-5",
                    "true",
                    "false",
                    "null",
                    "{\"a\":{\"b\":[1,{\"c\":null}]}}",
                    "\"\\u0041\"",
                    " [ ] "
                })
        void acceptsWhatARealParserAccepts(String json) {
            assertThatCode(() -> StrictJson.parse(json)).doesNotThrowAnyException();
            assertThatCode(() -> Json.parse(json)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "{",
                    "}",
                    "[",
                    "[1,]",
                    "[,1]",
                    "[1 2]",
                    "{\"a\":}",
                    "{\"a\"}",
                    "{\"a\":1 \"b\":2}",
                    "\"\\u12",
                    "{\"a\" 1}",
                    "{\"a\":1,}",
                    "{1:2}",
                    "{'a':1}",
                    "01",
                    "1.",
                    ".5",
                    "+1",
                    "-",
                    "1e",
                    "1e+",
                    "NaN",
                    "Infinity",
                    "\"abc",
                    "\"\\x\"",
                    "\"\\u12\"",
                    "\"\\u12g4\"",
                    "\"\\",
                    "\"a\nb\"",
                    "\"a\tb\"",
                    "tru",
                    "nul",
                    "truex",
                    "{} x",
                    "[] []",
                    "'a'"
                })
        void refusesWhatARealParserRefuses(String json) {
            assertThatCode(() -> StrictJson.parse(json)).isInstanceOf(IllegalArgumentException.class);

            var failure = catchThrowableOfType(MalformedJsonException.class, () -> Json.parse(json));

            assertThat(failure).isNotNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " \n"})
        void refusesAnEmptyText(String json) {
            // A real parser takes an empty document for a null: not the cli, which always expects a value.
            assertThat(catchThrowableOfType(MalformedJsonException.class, () -> Json.parse(json)))
                    .hasMessageEndingWith("the text ends too soon");
        }

        @Test
        void saysWhatIsWrongAndWhere() {
            var failure = catchThrowableOfType(MalformedJsonException.class, () -> Json.parse("[1,]"));

            assertThat(failure.key()).isEqualTo(ProtocolErrorKey.MALFORMED_JSON);
            assertThat(failure.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
            assertThat(failure.getMessage()).isEqualTo("Malformed JSON at position 3: a value was expected");
            assertThat(failure.parameters()).containsEntry("position", "3");
        }

        @Test
        void saysTheTextEndedTooSoon() {
            var failure = catchThrowableOfType(MalformedJsonException.class, () -> Json.parse("[1"));

            assertThat(failure.getMessage()).isEqualTo("Malformed JSON at position 2: the text ends too soon");
        }

        @ParameterizedTest
        @ValueSource(strings = {"1e99999999999", "1e999999999", "1e-999999999", "[1e1001]", "0.5e-1001"})
        void refusesANumberWhoseExponentIsOutOfRange(String json) {
            var failure = catchThrowableOfType(MalformedJsonException.class, () -> Json.parse(json));

            assertThat(failure).hasMessageEndingWith("the exponent of the number is out of range");
        }

        @Test
        void readsANumberWhoseExponentIsAtTheLimit() {
            assertThat(Json.parse("1e1000")).isEqualTo(new BigDecimal("1e1000"));
            assertThat(Json.parse("1e-1000")).isEqualTo(new BigDecimal("1e-1000"));
        }

        @Test
        void refusesNestingDeeperThanTheLimit() {
            var failure = catchThrowableOfType(MalformedJsonException.class, () -> Json.parse("[".repeat(100_000)));

            assertThat(failure).hasMessageEndingWith("the nesting is too deep");
            assertThat(catchThrowableOfType(MalformedJsonException.class, () -> Json.parse("{\"a\":".repeat(100_000))))
                    .hasMessageEndingWith("the nesting is too deep");
        }

        @Test
        void readsNestingAtTheLimit() {
            assertThat(Json.parse("[".repeat(64) + "]".repeat(64))).isInstanceOf(List.class);
            assertThat(catchThrowableOfType(
                            MalformedJsonException.class, () -> Json.parse("[".repeat(65) + "]".repeat(65))))
                    .isNotNull();
        }

        @Test
        void doesNotQuoteTheTextItRefuses() {
            var failure = catchThrowableOfType(MalformedJsonException.class, () -> Json.parse("secret"));

            assertThat(failure.getMessage()).doesNotContain("secret");
            assertThat(failure.parameters().values()).noneMatch(value -> value.contains("secret"));
        }
    }
}
