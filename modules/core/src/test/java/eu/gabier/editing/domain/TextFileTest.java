package eu.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.shared.error.domain.ErrorStatus;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TextFileTest {

    private static final Path FILE = Path.of("/project/A.java");
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static TextEdit edit(int startLine, int startColumn, int endLine, int endColumn, String newText) {
        return new TextEdit(new Range(new Position(startLine, startColumn), new Position(endLine, endColumn)), newText);
    }

    private static String edited(String text, TextEdit... edits) {
        return new TextFile(text, false).edit(FILE, List.of(edits)).text();
    }

    @Nested
    class Editing {

        @Test
        void replacesARangeInALine() {
            assertThat(edited("int count = 1;", edit(0, 4, 0, 9, "total"))).isEqualTo("int total = 1;");
        }

        @Test
        void insertsAtAnEmptyRange() {
            assertThat(edited("ab", edit(0, 1, 0, 1, "-"))).isEqualTo("a-b");
        }

        @Test
        void deletesWithAnEmptyReplacement() {
            assertThat(edited("abc", edit(0, 1, 0, 2, ""))).isEqualTo("ac");
        }

        @Test
        void replacesARangeAcrossLines() {
            assertThat(edited("one\ntwo\nthree\n", edit(0, 2, 2, 1, "-"))).isEqualTo("on-hree\n");
        }

        @Test
        void insertsAtTheEndOfTheTextAfterTheLastTerminator() {
            assertThat(edited("a\n", edit(1, 0, 1, 0, "b\n"))).isEqualTo("a\nb\n");
        }

        @Test
        void appliesTheEditsOfOneFileTogetherWhateverTheirOrder() {
            TextEdit first = edit(0, 0, 0, 1, "A");
            TextEdit second = edit(0, 2, 0, 3, "CC");
            TextEdit third = edit(1, 0, 1, 1, "D");

            String inOrder = edited("abc\nd", first, second, third);
            String reversed = edited("abc\nd", third, second, first);
            String shuffled = edited("abc\nd", second, third, first);

            assertThat(inOrder).isEqualTo("AbCC\nD");
            assertThat(reversed).isEqualTo(inOrder);
            assertThat(shuffled).isEqualTo(inOrder);
        }

        @Test
        void measuresEveryPositionOnTheOriginalText() {
            // The second edit does not shift by the length the first one added.
            assertThat(edited("ab", edit(0, 0, 0, 0, "XXXX"), edit(0, 1, 0, 2, "Y")))
                    .isEqualTo("XXXXaY");
        }

        @Test
        void acceptsTouchingEdits() {
            assertThat(edited("abcd", edit(0, 0, 0, 2, "X"), edit(0, 2, 0, 4, "Y")))
                    .isEqualTo("XY");
        }

        @Test
        void keepsTheOrderOfTheListForInsertionsAtTheSamePoint() {
            assertThat(edited("ab", edit(0, 1, 0, 1, "1"), edit(0, 1, 0, 1, "2")))
                    .isEqualTo("a12b");
            assertThat(edited("ab", edit(0, 1, 0, 1, "2"), edit(0, 1, 0, 1, "1")))
                    .isEqualTo("a21b");
        }

        @Test
        void insertsBeforeAReplacementThatStartsAtTheSamePoint() {
            assertThat(edited("abc", edit(0, 1, 0, 3, "Z"), edit(0, 1, 0, 1, "<")))
                    .isEqualTo("a<Z");
        }

        @Test
        void keepsTheBomOfTheFile() {
            TextFile file = new TextFile("class A {}", true);

            TextFile result = file.edit(FILE, List.of(edit(0, 0, 0, 0, "// first\n")));

            assertThat(result).isEqualTo(new TextFile("// first\nclass A {}", true));
        }

        @Test
        void givesBackAnEqualFileWhenTheEditsChangeNothing() {
            TextFile file = new TextFile("abc", false);

            assertThat(file.edit(FILE, List.of(edit(0, 1, 0, 2, "b")))).isEqualTo(file);
        }
    }

    @Nested
    class Overlap {

        @Test
        void refusesEditsThatShareText() {
            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> edited("abcdef", edit(0, 3, 0, 5, "x"), edit(0, 1, 0, 4, "y")))
                    .satisfies(exception -> {
                        assertThat(exception.key()).isEqualTo(EditingErrorKey.OVERLAPPING_EDITS);
                        assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
                        assertThat(exception.parameters())
                                .isEqualTo(Map.of("file", FILE.toString(), "first", "0:1-0:4", "second", "0:3-0:5"));
                    });
        }

        @Test
        void refusesAnEditInsideAnother() {
            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> edited("abcdef", edit(0, 1, 0, 5, "x"), edit(0, 2, 0, 3, "y")))
                    .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.OVERLAPPING_EDITS));
        }

        @Test
        void refusesAnInsertionInsideAReplacedRange() {
            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> edited("abcdef", edit(0, 1, 0, 5, "x"), edit(0, 3, 0, 3, "y")))
                    .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.OVERLAPPING_EDITS));
        }

        @Test
        void refusesTwoReplacementsOfTheSameRange() {
            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> edited("abcdef", edit(0, 1, 0, 3, "x"), edit(0, 1, 0, 3, "y")))
                    .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.OVERLAPPING_EDITS));
        }
    }

    @Nested
    class Staleness {

        @Test
        void refusesALineThatDoesNotExist() {
            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> edited("a\nb", edit(0, 0, 2, 0, "x")))
                    .satisfies(exception -> {
                        assertThat(exception.key()).isEqualTo(EditingErrorKey.STALE_EDIT);
                        assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
                        assertThat(exception.parameters())
                                .isEqualTo(Map.of("file", FILE.toString(), "line", "2", "column", "0"));
                    });
        }

        @Test
        void refusesAColumnPastTheEndOfTheLine() {
            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> edited("ab\ncd", edit(0, 3, 0, 3, "x")))
                    .satisfies(exception -> {
                        assertThat(exception.key()).isEqualTo(EditingErrorKey.STALE_EDIT);
                        assertThat(exception.parameters())
                                .isEqualTo(Map.of("file", FILE.toString(), "line", "0", "column", "3"));
                    });
        }

        @Test
        void acceptsTheEndOfALineAndOfTheText() {
            assertThat(edited("ab\ncd", edit(0, 2, 0, 2, "1"), edit(1, 2, 1, 2, "2")))
                    .isEqualTo("ab1\ncd2");
        }

        @Test
        void refusesAColumnInsideACrLfTerminator() {
            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> edited("ab\r\ncd", edit(0, 3, 0, 3, "x")))
                    .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.STALE_EDIT));
        }
    }

    @Nested
    class Columns {

        // U+1F600 is outside the BMP: two UTF-16 code units, so two columns.
        private static final String GRIN = "😀";

        @Test
        void countsACharacterOutsideTheBmpAsTwoColumns() {
            assertThat(edited("a" + GRIN + "bc", edit(0, 3, 0, 4, "X"))).isEqualTo("a" + GRIN + "Xc");
        }

        @Test
        void replacesACharacterOutsideTheBmpWithItsTwoColumns() {
            assertThat(edited("a" + GRIN + "b", edit(0, 1, 0, 3, "-"))).isEqualTo("a-b");
        }

        @Test
        void insertsRightAfterACharacterOutsideTheBmp() {
            assertThat(edited(GRIN, edit(0, 2, 0, 2, "!"))).isEqualTo(GRIN + "!");
        }

        @Test
        void refusesAColumnBetweenTheTwoHalvesOfASurrogatePair() {
            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> edited("a" + GRIN + "b", edit(0, 2, 0, 3, "x")))
                    .satisfies(exception -> {
                        assertThat(exception.key()).isEqualTo(EditingErrorKey.STALE_EDIT);
                        assertThat(exception.parameters())
                                .isEqualTo(Map.of("file", FILE.toString(), "line", "0", "column", "2"));
                    });
        }

        @Test
        void countsColumnsFromTheStartOfEachLine() {
            assertThat(edited(GRIN + "\n" + GRIN + "z", edit(1, 2, 1, 3, "Z"))).isEqualTo(GRIN + "\n" + GRIN + "Z");
        }
    }

    @Nested
    class LineEndings {

        @Test
        void keepsCrLfAndWritesTheReplacementWithCrLf() {
            assertThat(edited("a\r\nb\r\n", edit(1, 1, 1, 1, "x\ny\nz"))).isEqualTo("a\r\nbx\r\ny\r\nz\r\n");
        }

        @Test
        void keepsLfAndWritesTheReplacementWithLf() {
            assertThat(edited("a\nb\n", edit(1, 1, 1, 1, "x\r\ny\rz"))).isEqualTo("a\nbx\ny\nz\n");
        }

        @Test
        void followsTheFirstTerminatorOfTheFile() {
            assertThat(edited("a\r\nb\nc", edit(2, 1, 2, 1, "\n!"))).isEqualTo("a\r\nb\nc\r\n!");
        }

        @Test
        void countsALoneCrAsALineEnd() {
            assertThat(edited("a\rb\r", edit(1, 1, 1, 1, "x\ny"))).isEqualTo("a\rbx\ry\r");
        }

        @Test
        void takesTheReplacementAsItIsInAFileWithoutTerminator() {
            assertThat(edited("ab", edit(0, 1, 0, 1, "x\r\ny\nz"))).isEqualTo("ax\r\ny\nzb");
        }

        @Test
        void keepsTheTerminatorsOutsideTheEditsByteForByte() {
            assertThat(edited("a\r\nb\nc\rd", edit(3, 0, 3, 1, "D"))).isEqualTo("a\r\nb\nc\rD");
        }
    }

    @Nested
    class Encoding {

        @Test
        void decodesUtf8WithoutBom() {
            byte[] bytes = "café\n".getBytes(StandardCharsets.UTF_8);

            TextFile file = TextFile.decode(FILE, bytes);

            assertThat(file).isEqualTo(new TextFile("café\n", false));
            assertThat(file.encode(FILE)).isEqualTo(bytes);
        }

        @Test
        void keepsTheBomOutOfTheTextAndPutsItBackWhenEncoding() {
            byte[] bytes = withBom("class A {}");

            TextFile file = TextFile.decode(FILE, bytes);

            assertThat(file).isEqualTo(new TextFile("class A {}", true));
            assertThat(file.encode(FILE)).isEqualTo(bytes);
        }

        @Test
        void decodesAnEmptyFile() {
            assertThat(TextFile.decode(FILE, new byte[0])).isEqualTo(new TextFile("", false));
        }

        @Test
        void refusesBytesThatAreNotUtf8() {
            byte[] latin1 = "café".getBytes(StandardCharsets.ISO_8859_1);

            assertThat(TextFile.tryDecode(latin1)).isEmpty();
            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> TextFile.decode(FILE, latin1))
                    .satisfies(exception -> {
                        assertThat(exception.key()).isEqualTo(EditingErrorKey.UNSUPPORTED_ENCODING);
                        assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
                        assertThat(exception.parameters()).isEqualTo(Map.of("file", FILE.toString()));
                    });
        }

        @Test
        void refusesATextThatUtf8CannotEncode() {
            TextFile file = new TextFile("lone high surrogate \uD83D", false);

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> file.encode(FILE))
                    .satisfies(exception -> {
                        assertThat(exception.key()).isEqualTo(EditingErrorKey.UNSUPPORTED_ENCODING);
                        assertThat(exception.parameters()).isEqualTo(Map.of("file", FILE.toString()));
                    });
        }

        private byte[] withBom(String text) {
            byte[] body = text.getBytes(StandardCharsets.UTF_8);
            byte[] bytes = new byte[BOM.length + body.length];
            System.arraycopy(BOM, 0, bytes, 0, BOM.length);
            System.arraycopy(body, 0, bytes, BOM.length, body.length);

            return bytes;
        }
    }

    @Nested
    class FirstWord {

        private Optional<Position> firstWord(String text, String word) {
            return new TextFile(text, false).firstWord(word);
        }

        @Test
        void findsAWordInTheFirstLine() {
            assertThat(firstWord("int count = 1;", "count")).contains(new Position(0, 4));
        }

        @Test
        void countsTheColumnFromTheStartOfItsLine() {
            assertThat(firstWord("class A {\n    int count;\n}\n", "count")).contains(new Position(1, 8));
        }

        @Test
        void findsAWordAtTheStartAndAtTheEndOfTheText() {
            assertThat(firstWord("count", "count")).contains(new Position(0, 0));
            assertThat(firstWord("a = count", "count")).contains(new Position(0, 4));
        }

        @Test
        void takesTheFirstOccurrence() {
            assertThat(firstWord("x = count + count;", "count")).contains(new Position(0, 4));
        }

        @Test
        void findsAWordNextToPunctuation() {
            assertThat(firstWord("this.count(1)", "count")).contains(new Position(0, 5));
        }

        @Test
        void skipsALongerWordThatStartsWithIt() {
            assertThat(firstWord("greeting greet", "greet")).contains(new Position(0, 9));
        }

        @Test
        void skipsALongerWordThatEndsWithIt() {
            assertThat(firstWord("pregreet greet", "greet")).contains(new Position(0, 9));
        }

        @Test
        void countsTheDollarTheUnderscoreAndTheDigitsAsPartsOfAWord() {
            assertThat(firstWord("greet$ greet_ greet1 _greet 1greet greet", "greet"))
                    .contains(new Position(0, 35));
        }

        @Test
        void countsALetterOutsideTheBmpAsPartOfAWord() {
            String mathematicalBoldX = "𝐱";

            assertThat(firstWord(mathematicalBoldX + "name name" + mathematicalBoldX + " name", "name"))
                    .contains(new Position(0, 14));
        }

        @Test
        void countsTheColumnInUtf16CodeUnits() {
            assertThat(firstWord("😀 name", "name")).contains(new Position(0, 3));
        }

        @Test
        void countsTheLinesOfEveryTerminator() {
            assertThat(firstWord("a\r\nb\rc\nname", "name")).contains(new Position(3, 0));
        }

        @Test
        void saysNothingForAWordThatIsOnlyPartOfOthers() {
            assertThat(firstWord("greeting pregreet greet1", "greet")).isEmpty();
        }

        @Test
        void saysNothingForAWordThatIsNotThere() {
            assertThat(firstWord("", "greet")).isEmpty();
        }

        @Test
        void rejectsABlankWord() {
            assertThatExceptionOfType(MissingMandatoryValueException.class)
                    .isThrownBy(() -> firstWord("a b", " "))
                    .satisfies(exception -> assertThat(exception.field()).isEqualTo("word"));
        }
    }
}
