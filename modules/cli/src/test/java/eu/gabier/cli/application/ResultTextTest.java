package eu.gabier.cli.application;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.ChangedFiles;
import eu.gabier.protocol.ChangedFiles.Moved;
import eu.gabier.protocol.CodeActions;
import eu.gabier.protocol.CodeActions.Action;
import eu.gabier.protocol.FileDiagnostics;
import eu.gabier.protocol.FileDiagnostics.Diagnosis;
import eu.gabier.protocol.FileDiagnostics.Severity;
import eu.gabier.protocol.FoundReferences;
import eu.gabier.protocol.FoundReferences.Place;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ResultTextTest {

    private static final String ESCAPE = "\u001b[31m";
    private static final String ESCAPED = "\\u001b[31m";

    @Nested
    class OfChangedFiles {

        private static final ChangedFiles EMPTY = new ChangedFiles(List.of(), List.of(), List.of(), List.of(), "");

        @Test
        void saysNothingForAResultThatHoldsNothing() {
            assertThat(ResultText.lines(EMPTY)).isEmpty();
            assertThat(ResultText.warnings(EMPTY)).isEmpty();
        }

        @Test
        void listsEachKindOfChangeThenABlankLineAndTheDiff() {
            var changed = new ChangedFiles(
                    List.of(Path.of("/work/New.java")),
                    List.of(new Moved(Path.of("/work/Old.java"), Path.of("/work/Moved.java"))),
                    List.of(Path.of("/work/Gone.java")),
                    List.of(Path.of("/work/A.java"), Path.of("/work/B.java")),
                    "--- a\n+++ b\n@@ -1 +1 @@");

            assertThat(ResultText.lines(changed))
                    .containsExactly(
                            "modified /work/A.java",
                            "modified /work/B.java",
                            "created /work/New.java",
                            "renamed /work/Old.java -> /work/Moved.java",
                            "deleted /work/Gone.java",
                            "",
                            "--- a",
                            "+++ b",
                            "@@ -1 +1 @@");
        }

        @Test
        void writesOutWhatATerminalWouldObeyInAFileAndInTheDiff() {
            var changed = new ChangedFiles(
                    List.of(), List.of(), List.of(), List.of(Path.of("/work/" + ESCAPE + ".java")), "+" + ESCAPE);

            assertThat(ResultText.lines(changed))
                    .containsExactly("modified /work/" + ESCAPED + ".java", "", "+" + ESCAPED);
        }

        @Test
        void saysWarningsOneLineEachWithTheirTextWrittenOut() {
            var changed = EMPTY.withWarnings(List.of("two files disagree", "see " + ESCAPE));

            assertThat(ResultText.warnings(changed))
                    .containsExactly("warning: two files disagree", "warning: see " + ESCAPED);
        }
    }

    @Nested
    class OfDiagnostics {

        @Test
        void saysNothingForAFileWithNoProblem() {
            assertThat(ResultText.lines("Clean.java", new FileDiagnostics(List.of())))
                    .isEmpty();
        }

        @Test
        void givesOneLineForEachProblemWithTheFileAsGivenAndThePositionFromOne() {
            var diagnostics = new FileDiagnostics(List.of(
                    new Diagnosis(Severity.ERROR, 4, 15, "Type mismatch"),
                    new Diagnosis(Severity.WARNING, 0, 0, "Unused import")));

            assertThat(ResultText.lines("src/Broken.java", diagnostics))
                    .containsExactly(
                            "src/Broken.java:5:16: error: Type mismatch",
                            "src/Broken.java:1:1: warning: Unused import");
        }

        @Test
        void writesOutWhatATerminalWouldObeyInTheFileAndInTheMessage() {
            var diagnostics = new FileDiagnostics(List.of(new Diagnosis(Severity.ERROR, 0, 0, "bad " + ESCAPE)));

            assertThat(ResultText.lines("src/" + ESCAPE + ".java", diagnostics))
                    .containsExactly("src/" + ESCAPED + ".java:1:1: error: bad " + ESCAPED);
        }
    }

    @Nested
    class OfReferences {

        private static final Place DECLARATION = new Place("/work/Ledger.java", 9, 16, "    void deposit() {");
        private static final Place IN_BANK = new Place("/work/Bank.java", 7, 15, "\t\tledger.deposit(10);");

        @Test
        void saysThatNothingUsesTheSymbol() {
            assertThat(ResultText.lines(new FoundReferences(List.of(), List.of())))
                    .containsExactly("no references");
        }

        @Test
        void givesOneLineForEachPlaceWithItsPositionFromOneAndItsSourceWithoutIndentation() {
            var found = new FoundReferences(
                    List.of(), List.of(IN_BANK, new Place("/work/Teller.java", 0, 0, "  ledger.deposit(20);  ")));

            assertThat(ResultText.lines(found))
                    .containsExactly(
                            "/work/Bank.java:8:16: ledger.deposit(10);", "/work/Teller.java:1:1: ledger.deposit(20);");
        }

        @Test
        void listsTheDeclarationApartAndLabelsTheUses() {
            var found = new FoundReferences(List.of(DECLARATION), List.of(IN_BANK));

            assertThat(ResultText.lines(found))
                    .containsExactly(
                            "declaration:",
                            "/work/Ledger.java:10:17: void deposit() {",
                            "references:",
                            "/work/Bank.java:8:16: ledger.deposit(10);");
        }

        @Test
        void saysThatNothingUsesTheSymbolBelowItsDeclaration() {
            assertThat(ResultText.lines(new FoundReferences(List.of(DECLARATION), List.of())))
                    .containsExactly("declaration:", "/work/Ledger.java:10:17: void deposit() {", "no references");
        }

        @Test
        void writesOutWhatATerminalWouldObeyInTheFileAndInTheSource() {
            var found = new FoundReferences(List.of(), List.of(new Place("/work/" + ESCAPE, 0, 0, "x = " + ESCAPE)));

            assertThat(ResultText.lines(found)).containsExactly("/work/" + ESCAPED + ":1:1: x = " + ESCAPED);
        }
    }

    @Nested
    class OfCodeActions {

        @Test
        void saysThatNothingIsOffered() {
            assertThat(ResultText.lines(new CodeActions(List.of()))).containsExactly("no actions");
        }

        @Test
        void numbersTheActionsFromOneAndPointsToTheCommandThatDoesTheSame() {
            var offered = new CodeActions(List.of(
                    new Action("quickfix", "Import 'List' (java.util)"),
                    new Action("refactor.extract.variable", "Extract to local variable", "extract-variable"),
                    new Action("", "Untyped")));

            assertThat(ResultText.lines(offered))
                    .containsExactly(
                            "1 quickfix Import 'List' (java.util)",
                            "2 refactor.extract.variable Extract to local variable -> gabier extract-variable",
                            "3 - Untyped");
        }

        @Test
        void writesOutWhatATerminalWouldObeyInTheKindAndTheTitle() {
            var offered = new CodeActions(List.of(new Action("quick" + ESCAPE, "Fix " + ESCAPE)));

            assertThat(ResultText.lines(offered)).containsExactly("1 quick" + ESCAPED + " Fix " + ESCAPED);
        }
    }
}
