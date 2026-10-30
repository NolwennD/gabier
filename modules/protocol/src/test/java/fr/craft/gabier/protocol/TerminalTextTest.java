package fr.craft.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TerminalTextTest {

    private static String escape(int codePoint) {
        return codePoint > 0xFFFF ? "\\u{" + Integer.toHexString(codePoint) + "}" : String.format("\\u%04x", codePoint);
    }

    private static String around(int codePoint) {
        return "x" + new String(Character.toChars(codePoint)) + "y";
    }

    @Nested
    class Escapes {

        @ParameterizedTest
        @ValueSource(ints = {0x0000, 0x0009, 0x000a, 0x001b, 0x001f, 0x007f, 0x0080, 0x009b, 0x009f})
        void aControlCharacter(int codePoint) {
            assertThat(TerminalText.neutralise(around(codePoint))).isEqualTo("x" + escape(codePoint) + "y");
        }

        @ParameterizedTest
        @ValueSource(ints = {0x2028, 0x2029})
        void aLineOrParagraphSeparator(int codePoint) {
            assertThat(TerminalText.neutralise(around(codePoint))).isEqualTo("x" + escape(codePoint) + "y");
        }

        @ParameterizedTest
        @ValueSource(ints = {0x00ad, 0x200b, 0x200d, 0x200e, 0x202e, 0x2066, 0x2060, 0xfeff, 0xe0001, 0xe0041, 0xe007f})
        void aFormatCharacter(int codePoint) {
            assertThat(TerminalText.neutralise(around(codePoint))).isEqualTo("x" + escape(codePoint) + "y");
        }

        @ParameterizedTest
        @ValueSource(ints = {0x00a0, 0x1680, 0x2000, 0x2003, 0x200a, 0x202f, 0x205f, 0x3000})
        void aSpaceThatIsNotTheSpace(int codePoint) {
            assertThat(TerminalText.neutralise(around(codePoint))).isEqualTo("x" + escape(codePoint) + "y");
        }

        @ParameterizedTest
        @ValueSource(ints = {0x115f, 0x1160, 0x3164, 0xffa0})
        void aHangulFiller(int codePoint) {
            assertThat(TerminalText.neutralise(around(codePoint))).isEqualTo("x" + escape(codePoint) + "y");
        }

        @Test
        void theBrailleBlank() {
            assertThat(TerminalText.neutralise("x⠀")).isEqualTo("x\\u2800");
        }

        @Test
        void theCombiningGraphemeJoiner() {
            assertThat(TerminalText.neutralise("a͏b")).isEqualTo("a\\u034fb");
        }

        @ParameterizedTest
        @ValueSource(ints = {0xfe00, 0xfe0f, 0xe0100, 0xe01ef})
        void aVariationSelector(int codePoint) {
            assertThat(TerminalText.neutralise(around(codePoint))).isEqualTo("x" + escape(codePoint) + "y");
        }

        @Test
        void aBackslashIsDoubled() {
            assertThat(TerminalText.neutralise("a\\b")).isEqualTo("a\\\\b");
        }

        @Test
        void anUnpairedSurrogateIsEscapedAsItsCodeUnit() {
            assertThat(TerminalText.neutralise("a\ud800b")).isEqualTo("a\\ud800b");
        }
    }

    @Nested
    class Kept {

        @Test
        void aPrintableSpace() {
            assertThat(TerminalText.neutralise("a b  c")).isEqualTo("a b  c");
        }

        @Test
        void anAccentAnIdeographAndAnEmojiPair() {
            assertThat(TerminalText.neutralise("é 日本 😀 ~")).isEqualTo("é 日本 😀 ~");
        }

        @Test
        void nothingAtAll() {
            assertThat(TerminalText.neutralise("")).isEmpty();
        }
    }

    @Nested
    class Sweep {

        // The hidden characters of Unicode, written by hand from the Unicode character database (categories Cc, Cf, Zs
        // other than U+0020, Zl and Zp, then the characters that print as blank or change what is next to them): the
        // expected side of the sweep does not ask the code under test, nor the JDK, what is hidden. Pairs of the first
        // and the last code point of a range.
        private static final int[][] HIDDEN = {
            {0x0000, 0x001f},
            {0x007f, 0x009f}, // Cc: C0, DEL, C1
            {0x00a0, 0x00a0},
            {0x1680, 0x1680},
            {0x2000, 0x200a},
            {0x202f, 0x202f},
            {0x205f, 0x205f},
            {0x3000, 0x3000}, // Zs other than U+0020
            {0x2028, 0x2029}, // Zl, Zp
            {0x00ad, 0x00ad},
            {0x0600, 0x0605},
            {0x061c, 0x061c},
            {0x06dd, 0x06dd},
            {0x070f, 0x070f},
            {0x0890, 0x0891},
            {0x08e2, 0x08e2},
            {0x180e, 0x180e},
            {0x200b, 0x200f},
            {0x202a, 0x202e},
            {0x2060, 0x2064},
            {0x2066, 0x206f},
            {0xfeff, 0xfeff},
            {0xfff9, 0xfffb},
            {0x110bd, 0x110bd},
            {0x110cd, 0x110cd},
            {0x13430, 0x1343f},
            {0x1bca0, 0x1bca3},
            {0x1d173, 0x1d17a},
            {0xe0001, 0xe0001},
            {0xe0020, 0xe007f}, // Cf
            {0x034f, 0x034f}, // combining grapheme joiner
            {0x115f, 0x1160},
            {0x3164, 0x3164},
            {0xffa0, 0xffa0}, // Hangul fillers
            {0x2800, 0x2800}, // braille blank
            {0xfe00, 0xfe0f},
            {0xe0100, 0xe01ef} // variation selectors
        };

        private static boolean isHidden(int codePoint) {
            for (var range : HIDDEN) {
                if (codePoint >= range[0] && codePoint <= range[1]) {
                    return true;
                }
            }

            return false;
        }

        @Test
        void everyCodePointIsEscapedWhenTheListOfHiddenCharactersHasItAndKeptAsItIsOtherwise() {
            var wrong = new ArrayList<String>();
            for (var codePoint = 0; codePoint <= Character.MAX_CODE_POINT; codePoint++) {
                if (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE) {
                    continue;
                }
                var character = new String(Character.toChars(codePoint));
                var expected = codePoint == '\\' ? "\\\\" : isHidden(codePoint) ? escape(codePoint) : character;

                var actual = TerminalText.neutralise(character);

                if (!actual.equals(expected)) {
                    wrong.add(escape(codePoint) + " gave " + actual);
                }
            }

            assertThat(wrong).isEmpty();
        }

        @Test
        void whatIsNotHiddenIsKeptInAWholeText() {
            var text = "Straße 日本語 😀 (a-b) ~ x";

            assertThat(TerminalText.neutralise(text)).isEqualTo(text);
        }
    }
}
