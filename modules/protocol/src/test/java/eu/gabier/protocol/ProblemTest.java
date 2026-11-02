package eu.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.google.gson.JsonObject;
import eu.gabier.shared.error.domain.ErrorStatus;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProblemTest {

    private static Problem problem(String detail, Map<String, String> parameters) {
        return new Problem(
                "urn:gabier:error:some-key", "Some key", detail, "some-key", parameters, ErrorStatus.BAD_REQUEST);
    }

    @Nested
    class Validation {

        @Test
        void rejectsABlankType() {
            assertThatExceptionOfType(MissingMandatoryValueException.class)
                    .isThrownBy(() -> new Problem(" ", "T", "d", "k", Map.of(), ErrorStatus.BAD_REQUEST));
        }

        @Test
        void rejectsABlankTitle() {
            assertThatExceptionOfType(MissingMandatoryValueException.class)
                    .isThrownBy(() -> new Problem("urn:x", "", "d", "k", Map.of(), ErrorStatus.BAD_REQUEST));
        }

        @Test
        void rejectsABlankDetail() {
            assertThatExceptionOfType(MissingMandatoryValueException.class)
                    .isThrownBy(() -> new Problem("urn:x", "T", " ", "k", Map.of(), ErrorStatus.BAD_REQUEST));
        }

        @Test
        void rejectsABlankKey() {
            assertThatExceptionOfType(MissingMandatoryValueException.class)
                    .isThrownBy(() -> new Problem("urn:x", "T", "d", "", Map.of(), ErrorStatus.BAD_REQUEST));
        }

        @Test
        void rejectsMissingParameters() {
            assertThatExceptionOfType(MissingMandatoryValueException.class)
                    .isThrownBy(() -> new Problem("urn:x", "T", "d", "k", null, ErrorStatus.BAD_REQUEST));
        }

        @Test
        void rejectsAMissingCategory() {
            assertThatExceptionOfType(MissingMandatoryValueException.class)
                    .isThrownBy(() -> new Problem("urn:x", "T", "d", "k", Map.of(), null));
        }
    }

    @Nested
    class Parameters {

        @Test
        void areKeptSortedByName() {
            var unsorted = new LinkedHashMap<String, String>();
            unsorted.put("zebra", "1");
            unsorted.put("apple", "2");

            assertThat(problem("d", unsorted).parameters().keySet()).containsExactly("apple", "zebra");
        }

        @Test
        void doNotFollowLaterChangesOfTheMapGiven() {
            var given = new LinkedHashMap<String, String>();
            given.put("a", "1");
            var problem = problem("d", given);

            given.put("b", "2");

            assertThat(problem.parameters()).containsOnlyKeys("a");
        }

        @Test
        void cannotBeChanged() {
            var problem = problem("d", Map.of("a", "1"));

            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> problem.parameters().put("b", "2"));
        }
    }

    @Nested
    class Json {

        @Test
        void writesTheMembersInAStableOrderOnOneLine() {
            var problem = problem("It broke", Map.of("zebra", "1", "apple", "2"));

            assertThat(problem.toJson()).isEqualTo("""
                {"type":"urn:gabier:error:some-key","title":"Some key",\
                "detail":"It broke","key":"some-key",\
                "parameters":{"apple":"2","zebra":"1"}}\
                """);
        }

        @Test
        void writesNoParameterAsAnEmptyObject() {
            assertThat(problem("d", Map.of()).toJson()).endsWith("\"parameters\":{}}");
        }

        @Test
        void isValidJson() {
            var json = StrictJson.parse(
                            problem("It broke", Map.of("file", "A.java")).toJson())
                    .getAsJsonObject();

            assertThat(json.keySet()).containsExactly("type", "title", "detail", "key", "parameters");
            assertThat(json.get("type").getAsString()).isEqualTo("urn:gabier:error:some-key");
            assertThat(json.get("title").getAsString()).isEqualTo("Some key");
            assertThat(json.get("detail").getAsString()).isEqualTo("It broke");
            assertThat(json.get("key").getAsString()).isEqualTo("some-key");
            assertThat(json.getAsJsonObject("parameters").keySet()).containsExactly("file");
        }

        @Test
        void hasNoStatusMember() {
            assertThat(StrictJson.parse(problem("d", Map.of()).toJson())
                            .getAsJsonObject()
                            .has("status"))
                    .isFalse();
        }

        @Test
        void escapesWhatJsonRequiresToEscapeAndNothingElse() {
            var tricky = "quote \" backslash \\ newline \n return \r tab \t bell \u0007 unit \u001f del \u007f "
                    + "accent é cjk 日本 emoji 😀 solidus / ";
            JsonObject json = StrictJson.parse(
                            problem(tricky, Map.of(tricky, tricky)).toJson())
                    .getAsJsonObject();

            assertThat(json.get("detail").getAsString()).isEqualTo(tricky);
            assertThat(json.getAsJsonObject("parameters").get(tricky).getAsString())
                    .isEqualTo(tricky);
        }

        @Test
        void writesTheShortEscapesAndTheUnicodeEscapeOfAControlCharacter() {
            assertThat(problem("\"\\\n\r\t\u0001\u001f", Map.of()).toJson())
                    .contains("\"detail\":\"\\\"\\\\\\n\\r\\t\\u0001\\u001f\"");
        }

        @Test
        void writesNonAsciiAndOtherCharactersAsTheyAre() {
            assertThat(problem("é日/\u2028\ud83d\ude00", Map.of()).toJson())
                    .contains("\"detail\":\"é日/\u2028\ud83d\ude00\"");
        }

        @Test
        void escapesDelAndTheC1ControlCharactersSoThatTheJsonCannotDriveATerminal() {
            var json = problem("a\u007fb\u009bc", Map.of("k\u0080", "\u009f")).toJson();

            assertThat(json).contains("\"detail\":\"a\\u007fb\\u009bc\"").contains("\"k\\u0080\":\"\\u009f\"");
            assertThat(StrictJson.parse(json).getAsJsonObject().get("detail").getAsString())
                    .isEqualTo("a\u007fb\u009bc");
        }
    }

    @Nested
    class Text {

        @Test
        void isTheTitleTheDetailAndTheKey() {
            assertThat(problem("It broke", Map.of()).toText()).isEqualTo("Some key: It broke (some-key)");
        }

        @Test
        void listsTheParametersInTheOrderOfTheNames() {
            assertThat(problem("It broke", Map.of("zebra", "1", "apple", "2")).toText())
                    .isEqualTo("Some key: It broke (some-key; apple=2, zebra=1)");
        }

        @Test
        void showsLineBreaksAsEscapesAndStaysOnOneLine() {
            var text = problem("two\nlines\r\nand more", Map.of("a", "x\ny")).toText();

            assertThat(text).isEqualTo("Some key: two\\u000alines\\u000d\\u000aand more (some-key; a=x\\u000ay)");
            assertThat(List.of(text.split("\\R"))).hasSize(1);
        }

        @Test
        void showsAnotherSeparatorAFillerAndABlankBrailleAsEscapesSoThatNothingLooksBlank() {
            assertThat(problem("badㅤ x⠀  ", Map.of("p", "　")).toText())
                    .isEqualTo("Some key: bad\\u3164 x\\u2800 \\u00a0 (some-key; p=\\u3000)");
        }

        @Test
        void showsUnicodeLineAndParagraphSeparatorsAsEscapes() {
            var text = problem("a\u2028b\u2029c", Map.of()).toText();

            assertThat(text).isEqualTo("Some key: a\\u2028b\\u2029c (some-key)");
        }

        @Test
        void showsTheC0ControlCharactersOfADetailAsEscapes() {
            assertThat(problem("\u001b[2J\u0000\t\u001f", Map.of()).toText())
                    .isEqualTo("Some key: \\u001b[2J\\u0000\\u0009\\u001f (some-key)");
        }

        @Test
        void showsDelAsAnEscape() {
            assertThat(problem("a\u007fb", Map.of()).toText()).isEqualTo("Some key: a\\u007fb (some-key)");
        }

        @Test
        void showsTheC1ControlCharactersAsEscapes() {
            assertThat(problem("\u0080\u009b2J\u009f", Map.of()).toText())
                    .isEqualTo("Some key: \\u0080\\u009b2J\\u009f (some-key)");
        }

        @Test
        void showsTheControlCharactersOfAParameterNameAndValueAsEscapes() {
            assertThat(problem("d", Map.of("k\u001b", "\u001b[2J")).toText())
                    .isEqualTo("Some key: d (some-key; k\\u001b=\\u001b[2J)");
        }

        @Test
        void leavesPrintableAndNonAsciiCharactersAsTheyAre() {
            assertThat(problem("é日 ~\ud83d\ude00", Map.of()).toText())
                    .isEqualTo("Some key: é日 ~\ud83d\ude00 (some-key)");
        }

        @Test
        void showsABidirectionalControlAsAnEscapeSoThatAnExtensionIsNotReversed() {
            var text = problem("evil\u202egpj.txt", Map.of("name", "\u2066x\u2069"))
                    .toText();

            assertThat(text).isEqualTo("Some key: evil\\u202egpj.txt (some-key; name=\\u2066x\\u2069)");
        }

        @Test
        void showsAZeroWidthCharacterAsAnEscapeSoThatItHidesNothing() {
            var text = problem("a\u200bb\u200d\ufeff", Map.of()).toText();

            assertThat(text).isEqualTo("Some key: a\\u200bb\\u200d\\ufeff (some-key)");
        }

        @Test
        void showsATagCharacterOutsideTheBasicPlaneAsAnEscapeOfItsCodePoint() {
            var tag = new String(Character.toChars(0xE0041));

            assertThat(problem("a" + tag + "b", Map.of()).toText()).isEqualTo("Some key: a\\u{e0041}b (some-key)");
        }

        @Test
        void tellsAnEscapeFromTheSixCharactersOfAnEscapeWrittenByHand() {
            var real = problem("\u001b[2J", Map.of()).toText();
            var written = problem("\\u001b[2J", Map.of()).toText();

            assertThat(real).isEqualTo("Some key: \\u001b[2J (some-key)");
            assertThat(written).isEqualTo("Some key: \\\\u001b[2J (some-key)").isNotEqualTo(real);
        }

        @Test
        void doublesEveryBackslashWhereverItIs() {
            assertThat(problem("a\\b\\\\", Map.of("p\\", "c:\\dir")).toText())
                    .isEqualTo("Some key: a\\\\b\\\\\\\\ (some-key; p\\\\=c:\\\\dir)");
        }

        @Test
        void showsAnUnpairedSurrogateAsAnEscapeOfItsCodeUnit() {
            var text = problem("a\ud800b\udc00c", Map.of("p", "\udbff")).toText();

            assertThat(text).isEqualTo("Some key: a\\ud800b\\udc00c (some-key; p=\\udbff)");
        }

        @Test
        void showsAnUnpairedHighSurrogateAtTheEndOfTheTextAsAnEscape() {
            assertThat(problem("end\ud83d", Map.of()).toText()).isEqualTo("Some key: end\\ud83d (some-key)");
        }

        @Test
        void keepsAPairedSurrogateAsOneCharacter() {
            assertThat(problem("\ud83d\ude00", Map.of()).toText()).isEqualTo("Some key: \ud83d\ude00 (some-key)");
        }

        @Test
        void containsNoControlCharacterWhateverTheInput() {
            var everyChar = new StringBuilder();
            for (char character = 0; character < 0x100; character++) {
                everyChar.append(character);
            }

            var text = problem(everyChar.toString(), Map.of("p", everyChar.toString()))
                    .toText();

            assertThat(text.chars().filter(Character::isISOControl)).isEmpty();
        }
    }

    @Test
    void keepsItsCategoryOutOfTheJson() {
        var problem = problem("d", Map.of());

        assertThat(problem.category()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(StrictJson.parse(problem.toJson()).getAsJsonObject().keySet())
                .doesNotContain("category", "status");
        assertThat(problem.toJson()).doesNotContain("BAD_REQUEST");
    }
}
