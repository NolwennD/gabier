package fr.craft.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The expected texts are the output of {@code diff -u} on the same files, minus the timestamps of its headers. */
class UnifiedDiffTest {

    private static final String NUMBERS = lines(1, 18);

    private static String lines(int first, int last) {
        return IntStream.rangeClosed(first, last).mapToObj(n -> n + "\n").collect(Collectors.joining());
    }

    private static String diff(String before, String after) {
        return UnifiedDiff.of("old", before, "new", after);
    }

    @Nested
    class Hunks {

        @Test
        void saysNothingButTheHeadersWhenTheTextsAreEqual() {
            assertThat(diff("a\nb\n", "a\nb\n")).isEqualTo("--- old\n+++ new\n");
        }

        @Test
        void showsThreeLinesOfContextAroundAChangeAndSplitsDistantChanges() {
            String after = NUMBERS.replace("\n3\n", "\nX\n").replace("\n17\n", "\nY\n");

            assertThat(diff(NUMBERS, after)).isEqualTo("""
                --- old
                +++ new
                @@ -1,6 +1,6 @@
                 1
                 2
                -3
                +X
                 4
                 5
                 6
                @@ -14,5 +14,5 @@
                 14
                 15
                 16
                -17
                +Y
                 18
                """);
        }

        @Test
        void mergesTheHunksWhoseContextsTouch() {
            String after = NUMBERS.replace("\n3\n", "\nX\n").replace("\n10\n", "\nY\n");

            assertThat(diff(NUMBERS, after)).isEqualTo("""
                --- old
                +++ new
                @@ -1,13 +1,13 @@
                 1
                 2
                -3
                +X
                 4
                 5
                 6
                 7
                 8
                 9
                -10
                +Y
                 11
                 12
                 13
                """);
        }

        @Test
        void keepsTwoHunksWhenOneUnchangedLineSeparatesTheContexts() {
            String after = NUMBERS.replace("\n3\n", "\nX\n").replace("\n11\n", "\nY\n");

            assertThat(diff(NUMBERS, after)).isEqualTo("""
                --- old
                +++ new
                @@ -1,6 +1,6 @@
                 1
                 2
                -3
                +X
                 4
                 5
                 6
                @@ -8,7 +8,7 @@
                 8
                 9
                 10
                -11
                +Y
                 12
                 13
                 14
                """);
        }

        @Test
        void showsAnInsertionAtTheStart() {
            assertThat(diff("1\n2\n", "z\n1\n2\n")).isEqualTo("""
                --- old
                +++ new
                @@ -1,2 +1,3 @@
                +z
                 1
                 2
                """);
        }

        @Test
        void showsInsertionsAndDeletionsTogether() {
            assertThat(diff("a\nb\nc\nd\ne\n", "a\nx\nc\ne\nf\n")).isEqualTo("""
                --- old
                +++ new
                @@ -1,5 +1,5 @@
                 a
                -b
                +x
                 c
                -d
                 e
                +f
                """);
        }

        @Test
        void marksALastLineWithoutTerminator() {
            assertThat(diff("a", "a\n")).isEqualTo("""
                --- old
                +++ new
                @@ -1 +1 @@
                -a
                \\ No newline at end of file
                +a
                """);
        }

        @Test
        void showsTheLinesWithoutTheirTerminatorWhateverItIs() {
            assertThat(diff("a\r\nb\r\n", "a\r\nc\r\n")).isEqualTo("""
                --- old
                +++ new
                @@ -1,2 +1,2 @@
                 a
                -b
                +c
                """);
        }

        @Test
        void splitsOnLoneCarriageReturnsToo() {
            assertThat(diff("a\rb\r", "a\rc\r")).isEqualTo("""
                --- old
                +++ new
                @@ -1,2 +1,2 @@
                 a
                -b
                +c
                """);
        }
    }

    @Nested
    class Labels {

        @Test
        void describesACreatedFile() {
            assertThat(UnifiedDiff.of("/dev/null", "", "q", "a\n")).isEqualTo("""
                --- /dev/null
                +++ q
                @@ -0,0 +1 @@
                +a
                """);
        }

        @Test
        void describesADeletedFile() {
            assertThat(UnifiedDiff.of("q", "a\n", "/dev/null", "")).isEqualTo("""
                --- q
                +++ /dev/null
                @@ -1 +0,0 @@
                -a
                """);
        }

        @Test
        void describesAnEmptyFileCreatedOrRenamedWithoutAHunk() {
            assertThat(UnifiedDiff.of("/dev/null", "", "q", "")).isEqualTo("--- /dev/null\n+++ q\n");
            assertThat(UnifiedDiff.of("p", "a\n", "q", "a\n")).isEqualTo("--- p\n+++ q\n");
        }
    }

    @Nested
    class BigChanges {

        @Test
        void givesUpTheMinimalDiffOfTwoTextsThatShareNothingAndAreTooFarApart() {
            String before =
                    IntStream.range(0, 1100).mapToObj(n -> "a" + n + "\n").collect(Collectors.joining());
            String after =
                    IntStream.range(0, 1100).mapToObj(n -> "b" + n + "\n").collect(Collectors.joining());

            String expected = "--- old\n+++ new\n@@ -1,1100 +1,1100 @@\n"
                    + IntStream.range(0, 1100).mapToObj(n -> "-a" + n + "\n").collect(Collectors.joining())
                    + IntStream.range(0, 1100).mapToObj(n -> "+b" + n + "\n").collect(Collectors.joining());
            assertThat(diff(before, after)).isEqualTo(expected);
        }

        @Test
        void staysMinimalOnALongFileWithFewChanges() {
            String before = lines(1, 5000);
            String after = before.replace("\n2500\n", "\nX\n");

            assertThat(diff(before, after)).isEqualTo("""
                --- old
                +++ new
                @@ -2497,7 +2497,7 @@
                 2497
                 2498
                 2499
                -2500
                +X
                 2501
                 2502
                 2503
                """);
        }
    }
}
