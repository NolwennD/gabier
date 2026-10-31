package fr.craft.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SourceTextTest {

    private static final Path FILE = Path.of("/project/A.java");

    private static TextEdit edit(int startLine, int startColumn, int endLine, int endColumn, String newText) {
        return new TextEdit(new Range(new Position(startLine, startColumn), new Position(endLine, endColumn)), newText);
    }

    /** The text that results from applying the one edit that {@code replacing} gives. */
    private static String replaced(String before, String after) {
        return SourceText.edited(FILE, before, List.of(SourceText.replacing(before, after)));
    }

    @Nested
    class Editing {

        @Test
        void appliesTheEditsToTheText() {
            String edited = SourceText.edited(
                    FILE,
                    "int count = 1;\nreturn count;\n",
                    List.of(edit(0, 4, 0, 9, "total"), edit(1, 7, 1, 12, "total")));

            assertThat(edited).isEqualTo("int total = 1;\nreturn total;\n");
        }

        @Test
        void refusesAnEditThatDoesNotFitTheText() {
            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> SourceText.edited(FILE, "abc", List.of(edit(0, 9, 0, 9, "x"))));
        }
    }

    @Nested
    class PositionOfAnOffset {

        @Test
        void countsTheColumnInTheLine() {
            assertThat(SourceText.positionAt("int x;\nint y;\n", 11)).isEqualTo(new Position(1, 4));
        }

        @Test
        void givesTheStartOfTheText() {
            assertThat(SourceText.positionAt("int x;", 0)).isEqualTo(new Position(0, 0));
        }

        @Test
        void givesTheEndOfTheTextAfterTheLastTerminator() {
            assertThat(SourceText.positionAt("int x;\n", 7)).isEqualTo(new Position(1, 0));
        }

        @Test
        void followsLinesEndedByCarriageReturnAndLineFeed() {
            assertThat(SourceText.positionAt("a;\r\nbc;", 5)).isEqualTo(new Position(1, 1));
        }

        @Test
        void countsInUtf16CodeUnits() {
            assertThat(SourceText.positionAt("😀x", 2)).isEqualTo(new Position(0, 2));
        }
    }

    @Nested
    class OneEditBetweenTwoTexts {

        @Test
        void coversOnlyWhatDiffers() {
            assertThat(SourceText.replacing("int count = 1;", "int total = 1;")).isEqualTo(edit(0, 4, 0, 9, "total"));
        }

        @Test
        void insertsWhatTheNewTextAdds() {
            assertThat(SourceText.replacing("a;\nc;\n", "a;\nb;\nc;\n")).isEqualTo(edit(1, 0, 1, 0, "b;\n"));
        }

        @Test
        void deletesWhatTheNewTextRemoves() {
            assertThat(SourceText.replacing("a;\nb;\nc;\n", "a;\nc;\n")).isEqualTo(edit(1, 0, 2, 0, ""));
        }

        @Test
        void spansTheLinesBetweenTwoDistantChanges() {
            String before = "int a = 1;\nint b = 2;\nint c = 3;\n";
            String after = "int x = 1;\nint b = 2;\nint c = 3;\nint d = 4;\n";

            assertThat(SourceText.replacing(before, after))
                    .isEqualTo(edit(0, 4, 2, 9, "x = 1;\nint b = 2;\nint c = 3;\nint d = 4"));
            assertThat(replaced(before, after)).isEqualTo(after);
        }

        @Test
        void doesNotCountTheSameCharactersTwiceWhenThePrefixAndTheSuffixMeet() {
            assertThat(SourceText.replacing("aa", "aaa")).isEqualTo(edit(0, 2, 0, 2, "a"));
            assertThat(replaced("aaa", "aa")).isEqualTo("aa");
        }

        @Test
        void startsAtTheStartOfTheTextWhenTheTextsDifferFromTheFirstCharacter() {
            assertThat(SourceText.replacing("abc", "xbc")).isEqualTo(edit(0, 0, 0, 1, "x"));
        }

        @Test
        void cutsAfterAHighSurrogateOrACarriageReturnThatDoesNotBeginAPair() {
            assertThat(SourceText.replacing("a\uD83Dx", "a\uD83Dy")).isEqualTo(edit(0, 2, 0, 3, "y"));
            assertThat(SourceText.replacing("a\rb", "a\rc")).isEqualTo(edit(1, 0, 1, 1, "c"));
        }

        @Test
        void changesNothingForTwoEqualTexts() {
            assertThat(replaced("int x;\n", "int x;\n")).isEqualTo("int x;\n");
        }

        @Test
        void doesNotSplitASurrogatePairAtTheStartOfTheChange() {
            // The two characters share their high surrogate.
            assertThat(replaced("a😀b", "a😁b")).isEqualTo("a😁b");
            assertThat(SourceText.replacing("a😀b", "a😁b")).isEqualTo(edit(0, 1, 0, 3, "😁"));
        }

        @Test
        void doesNotSplitASurrogatePairAtTheEndOfTheChange() {
            // The two characters share their low surrogate.
            assertThat(replaced("a😀b", "a🈀b")).isEqualTo("a🈀b");
            assertThat(SourceText.replacing("a😀b", "a🈀b")).isEqualTo(edit(0, 1, 0, 3, "🈀"));
        }

        @Test
        void doesNotStartInTheMiddleOfALineTerminator() {
            assertThat(SourceText.replacing("x\r\ny", "x\rzy")).isEqualTo(edit(0, 1, 1, 0, "\rz"));
        }

        @Test
        void doesNotEndInTheMiddleOfALineTerminator() {
            assertThat(SourceText.replacing("x\r\ny", "x\n\ny")).isEqualTo(edit(0, 1, 1, 0, "\n\n"));
        }
    }
}
