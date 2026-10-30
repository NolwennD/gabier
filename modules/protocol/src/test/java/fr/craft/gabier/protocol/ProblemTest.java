package fr.craft.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.google.gson.JsonObject;
import fr.craft.gabier.shared.error.domain.ErrorStatus;
import fr.craft.gabier.shared.error.domain.MissingMandatoryValueException;
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

            assertThat(problem.toJson())
                    .isEqualTo("{\"type\":\"urn:gabier:error:some-key\",\"title\":\"Some key\","
                            + "\"detail\":\"It broke\",\"key\":\"some-key\","
                            + "\"parameters\":{\"apple\":\"2\",\"zebra\":\"1\"}}");
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
            assertThat(problem("é日/\u007f", Map.of()).toJson()).contains("\"detail\":\"é日/\u007f\"");
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
        void staysOnOneLine() {
            var text = problem("two\nlines\r\nand more", Map.of("a", "x\ny")).toText();

            assertThat(text).isEqualTo("Some key: two lines and more (some-key; a=x y)");
            assertThat(List.of(text.split("\\R"))).hasSize(1);
        }
    }

    @Test
    void keepsItsCategoryOutOfTheJson() {
        assertThat(problem("d", Map.of()).category()).isEqualTo(ErrorStatus.BAD_REQUEST);
    }
}
