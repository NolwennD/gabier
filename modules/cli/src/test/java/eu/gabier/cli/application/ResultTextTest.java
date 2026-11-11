package eu.gabier.cli.application;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.ChangedFiles;
import eu.gabier.protocol.ChangedFiles.Moved;
import eu.gabier.protocol.CodeActions;
import eu.gabier.protocol.CodeActions.Action;
import eu.gabier.protocol.DaemonStatus;
import eu.gabier.protocol.DiagnosedFiles;
import eu.gabier.protocol.FileDiagnostics;
import eu.gabier.protocol.FileDiagnostics.Diagnosis;
import eu.gabier.protocol.FileDiagnostics.Severity;
import eu.gabier.protocol.FoundReferences;
import eu.gabier.protocol.FoundReferences.Place;
import eu.gabier.protocol.IdleStop;
import eu.gabier.protocol.ImportSettings;
import eu.gabier.protocol.JdtlsState;
import eu.gabier.protocol.SelectedTests;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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
    class OfAReportOfChangedFiles {

        private static final ChangedFiles EMPTY = new ChangedFiles(List.of(), List.of(), List.of(), List.of(), "");
        private static final ChangedFiles RENAMED =
                new ChangedFiles(List.of(), List.of(), List.of(), List.of(Path.of("/work/A.java")), "--- a\n+++ b");

        @Test
        void givesTheFilesAndTheDiffOfAChangeThatWrote() {
            assertThat(ResultText.report(RENAMED, false, false))
                    .containsExactly("modified /work/A.java", "", "--- a", "+++ b");
        }

        @Test
        void saysADryRunBeforeTheRest() {
            assertThat(ResultText.report(RENAMED, true, false))
                    .containsExactly("dry run, nothing written", "modified /work/A.java", "", "--- a", "+++ b");
        }

        @Test
        void putsTheWarningsBeforeTheFiles() {
            var warned = RENAMED.withWarnings(List.of("two files disagree"));

            assertThat(ResultText.report(warned, false, false))
                    .containsExactly("warning: two files disagree", "modified /work/A.java", "", "--- a", "+++ b");
        }

        @Test
        void saysNothingForAChangeThatHoldsNothingWhenTheOperationDoesNotSayIt() {
            assertThat(ResultText.report(EMPTY, false, false)).isEmpty();
            assertThat(ResultText.report(EMPTY, true, false)).containsExactly("dry run, nothing written");
        }

        @Test
        void saysThereIsNothingToChangeForAChangeThatHoldsNothingWhenTheOperationSaysIt() {
            assertThat(ResultText.report(EMPTY, false, true)).containsExactly("nothing to change");
            assertThat(ResultText.report(EMPTY.withWarnings(List.of("careful")), true, true))
                    .containsExactly("dry run, nothing written", "warning: careful", "nothing to change");
        }

        @Test
        void doesNotSayThereIsNothingToChangeForAChangeThatHoldsSomething() {
            assertThat(ResultText.report(RENAMED, false, true)).doesNotContain("nothing to change");
        }
    }

    @Nested
    class OfTheStatus {

        private final DaemonStatus status = new DaemonStatus(
                "0.1",
                4242,
                Duration.ofMillis(65432),
                Path.of("/work/demo"),
                JdtlsState.STARTING,
                Optional.empty(),
                ImportSettings.DEFAULTS);

        @Test
        void givesTheLineOfTheDaemonThenTheSettingsOfTheImports() {
            var lines = ResultText.lines(status);

            assertThat(lines.getFirst())
                    .isEqualTo("gabier-daemon 0.1, pid 4242, up 65s, project /work/demo, jdtls starting");
            assertThat(lines.get(1)).isEqualTo("imports:");
            assertThat(lines.subList(2, lines.size()))
                    .isNotEmpty()
                    .allSatisfy(line -> assertThat(line).startsWith("  java."));
        }

        private DaemonStatus idling(Duration timeout, Duration left) {
            return new DaemonStatus(
                    "0.1",
                    4242,
                    Duration.ofMillis(65432),
                    Path.of("/work/demo"),
                    JdtlsState.READY,
                    Optional.empty(),
                    ImportSettings.DEFAULTS,
                    Optional.empty(),
                    Optional.of(new IdleStop(timeout, left)));
        }

        @Test
        void endsWithTheDelayAfterWhichTheDaemonStopsWhenIdleAndWhatIsLeftOfIt() {
            var lines = ResultText.lines(idling(Duration.ofMinutes(60), Duration.ofSeconds(35 * 60 + 12)));

            assertThat(lines.getLast()).isEqualTo("idle stop: after 60 min with no request, 35 min 12s left");
            assertThat(lines.get(1)).isEqualTo("imports:");
        }

        @Test
        void saysTheTimeLeftInWholeMinutesOrInSecondsWhenItIsRound() {
            assertThat(ResultText.lines(idling(Duration.ofMinutes(60), Duration.ofMinutes(35)))
                            .getLast())
                    .endsWith("no request, 35 min left");
            assertThat(ResultText.lines(idling(Duration.ofMinutes(60), Duration.ofSeconds(42)))
                            .getLast())
                    .endsWith("no request, 42s left");
            assertThat(ResultText.lines(idling(Duration.ofMinutes(60), Duration.ZERO))
                            .getLast())
                    .endsWith("no request, 0s left");
        }

        @Test
        void saysNothingOfAnIdleStopWhenTheDaemonNeverStopsWhenIdle() {
            assertThat(ResultText.lines(status)).noneMatch(line -> line.startsWith("idle stop"));
        }

        @Test
        void saysWhyJdtlsFailed() {
            var failed = new DaemonStatus(
                    "0.1",
                    4242,
                    Duration.ofSeconds(3),
                    Path.of("/work/demo"),
                    JdtlsState.FAILED,
                    Optional.of("no JDK"),
                    ImportSettings.DEFAULTS);

            assertThat(ResultText.lines(failed).getFirst()).endsWith("jdtls failed (no JDK)");
        }

        @Test
        void writesOutWhatATerminalWouldObeyInTheReason() {
            var failed = new DaemonStatus(
                    "0.1",
                    4242,
                    Duration.ofSeconds(3),
                    Path.of("/work/demo"),
                    JdtlsState.FAILED,
                    Optional.of("x" + ESCAPE + "\nforged"),
                    ImportSettings.DEFAULTS);

            assertThat(ResultText.lines(failed))
                    .hasSize(ResultText.lines(status).size());
            assertThat(ResultText.lines(failed).getFirst()).endsWith("(x" + ESCAPED + "\\u000aforged)");
        }
    }

    @Nested
    class OfDiagnostics {

        private static DiagnosedFiles diagnosed(FileDiagnostics... files) {
            return new DiagnosedFiles(List.of(files));
        }

        @Test
        void saysNothingForAFileWithNoProblem() {
            assertThat(ResultText.lines(
                            List.of("Clean.java"), diagnosed(new FileDiagnostics("/p/Clean.java", List.of()))))
                    .isEmpty();
        }

        @Test
        void givesOneLineForEachProblemWithTheFileAsGivenAndThePositionFromOne() {
            var diagnostics = diagnosed(new FileDiagnostics(
                    "/p/src/Broken.java",
                    List.of(
                            new Diagnosis(Severity.ERROR, 4, 15, "Type mismatch"),
                            new Diagnosis(Severity.WARNING, 0, 0, "Unused import"))));

            assertThat(ResultText.lines(List.of("src/Broken.java"), diagnostics))
                    .containsExactly(
                            "src/Broken.java:5:16: error: Type mismatch",
                            "src/Broken.java:1:1: warning: Unused import");
        }

        @Test
        void givesTheProblemsOfEachFileInTheOrderGivenWithTheFileAsTypedForTheElementAtTheSameIndex() {
            var diagnostics = diagnosed(
                    new FileDiagnostics("/p/B.java", List.of(new Diagnosis(Severity.ERROR, 1, 2, "in b"))),
                    new FileDiagnostics("/p/Clean.java", List.of()),
                    new FileDiagnostics("/p/B.java", List.of(new Diagnosis(Severity.ERROR, 1, 2, "in b"))),
                    new FileDiagnostics("/p/A.java", List.of(new Diagnosis(Severity.HINT, 0, 0, "in a"))));

            assertThat(ResultText.lines(List.of("./B.java", "Clean.java", "B.java", "a/../A.java"), diagnostics))
                    .containsExactly(
                            "./B.java:2:3: error: in b", "B.java:2:3: error: in b", "a/../A.java:1:1: hint: in a");
        }

        @Test
        void showsAnElementTheRequestHadNoNameForUnderTheNameTheResultGivesIt() {
            var diagnostics = diagnosed(
                    new FileDiagnostics("/p/A.java", List.of(new Diagnosis(Severity.ERROR, 0, 0, "a"))),
                    new FileDiagnostics("/p/B.java", List.of(new Diagnosis(Severity.ERROR, 0, 0, "b"))));

            assertThat(ResultText.lines(List.of("A.java"), diagnostics))
                    .containsExactly("A.java:1:1: error: a", "/p/B.java:1:1: error: b");
        }

        @Test
        void writesOutWhatATerminalWouldObeyInTheFileAndInTheMessage() {
            var diagnostics = diagnosed(
                    new FileDiagnostics("/p/a.java", List.of(new Diagnosis(Severity.ERROR, 0, 0, "bad " + ESCAPE))));

            assertThat(ResultText.lines(List.of("src/" + ESCAPE + ".java"), diagnostics))
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
    class OfSelectedTests {

        @Test
        void saysThatNoTestIsAffected() {
            assertThat(ResultText.lines(new SelectedTests(List.of()))).containsExactly("no affected tests");
        }

        @Test
        void givesOneLineForEachTestInTheOrderOfTheAnswer() {
            assertThat(ResultText.lines(new SelectedTests(List.of("demo.LedgerTest", "demo.app.BankTest$Nested"))))
                    .containsExactly("demo.LedgerTest", "demo.app.BankTest$Nested");
        }

        @Test
        void writesOutWhatATerminalWouldObeyInTheNameOfAClass() {
            assertThat(ResultText.lines(new SelectedTests(List.of("demo." + ESCAPE))))
                    .containsExactly("demo." + ESCAPED);
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
