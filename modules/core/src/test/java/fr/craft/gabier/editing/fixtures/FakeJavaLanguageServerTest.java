package fr.craft.gabier.editing.fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.Diagnostic;
import fr.craft.gabier.editing.domain.FileChange;
import fr.craft.gabier.editing.domain.LanguageServerErrorKey;
import fr.craft.gabier.editing.domain.LanguageServerException;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.editing.domain.Range;
import fr.craft.gabier.editing.domain.TextEdit;
import fr.craft.gabier.editing.fixtures.Scenario.Answer;
import fr.craft.gabier.editing.fixtures.Scenario.Request;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FakeJavaLanguageServerTest {

    private static final Path ROOT = Path.of("/project");
    private static final Path MAIN = ROOT.resolve("src/main/java/demo/Main.java");
    private static final Path GREETER = ROOT.resolve("src/main/java/demo/Greeter.java");
    private static final Path TOTALS = ROOT.resolve("src/main/java/demo/Totals.java");
    private static final Path PRICES = ROOT.resolve("src/main/java/demo/Prices.java");
    private static final Path SHAPES = ROOT.resolve("src/main/java/demo/Shapes.java");
    private static final Path BROKEN = ROOT.resolve("src/main/java/demo/Broken.java");
    private static final Path UNTIDY = ROOT.resolve("src/main/java/demo/Untidy.java");
    private static final Path TIDY = ROOT.resolve("src/main/java/demo/Tidy.java");

    private FakeJavaLanguageServer server;

    @BeforeEach
    void createServer() {
        server = new FakeJavaLanguageServer(ROOT);
    }

    @Test
    void answersTheDiagnosticsOfAScenario() {
        server.scripted(Scenarios.CLEAN_FILE).scripted(Scenarios.FILE_WITH_AN_ERROR);

        assertThat(server.diagnostics(MAIN)).isEmpty();
        assertThat(server.diagnostics(BROKEN))
                .containsExactly(new Diagnostic(
                        Diagnostic.Severity.ERROR,
                        new Position(4, 15),
                        "Type mismatch: cannot convert from String to int"));
    }

    @Test
    void answersTheChangeSetOfARenameScenarioWithPathsUnderItsRoot() {
        server.scripted(Scenarios.RENAME_LOCAL_VARIABLE);

        ChangeSet changes = server.rename(GREETER, new Position(6, 15), "text");

        assertThat(changes)
                .isEqualTo(((Answer.Renamed)
                                Scenarios.RENAME_LOCAL_VARIABLE.inside(ROOT).answer())
                        .changes());
        assertThat(changes.changes())
                .singleElement()
                .isEqualTo(new FileChange.EditFile(
                        GREETER,
                        List.of(new TextEdit(
                                new Range(new Position(6, 15), new Position(7, 22)),
                                "text = greeting + \", \" + name;\n        return text"))));
    }

    @Test
    void answersTheChangeSetOfAnOrganizeImportsScenarioWithPathsUnderItsRoot() {
        server.scripted(Scenarios.ORGANIZE_IMPORTS);

        Optional<ChangeSet> changes = server.organizeImports(UNTIDY);

        assertThat(changes)
                .contains(((Answer.Organized)
                                Scenarios.ORGANIZE_IMPORTS.inside(ROOT).answer())
                        .changes());
        assertThat(changes.orElseThrow().changes())
                .singleElement()
                .isInstanceOfSatisfying(
                        FileChange.EditFile.class,
                        edit -> assertThat(edit.file()).isEqualTo(UNTIDY));
    }

    @Test
    void answersTheChangeSetOfAnExtractVariableScenarioWithPathsUnderItsRoot() {
        server.scripted(Scenarios.EXTRACT_VARIABLE);

        ChangeSet changes =
                server.extractVariable(GREETER, new Range(new Position(6, 25), new Position(6, 47)), "joined");

        assertThat(changes)
                .isEqualTo(((Answer.Extracted)
                                Scenarios.EXTRACT_VARIABLE.inside(ROOT).answer())
                        .changes());
        assertThat(changes.changes())
                .singleElement()
                .isInstanceOfSatisfying(
                        FileChange.EditFile.class,
                        edit -> assertThat(edit.file()).isEqualTo(GREETER));
    }

    @Test
    void throwsTheRefusalOfAnExtractVariableScenario() {
        server.scripted(Scenarios.EXTRACT_VARIABLE_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION)
                .scripted(Scenarios.EXTRACT_VARIABLE_TO_AN_INVALID_NAME);

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() ->
                        server.extractVariable(GREETER, new Range(new Position(7, 8), new Position(7, 14)), "joined"))
                .satisfies(
                        exception -> assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NOT_AN_EXPRESSION));
        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() ->
                        server.extractVariable(GREETER, new Range(new Position(6, 25), new Position(6, 47)), "1st"))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.INVALID_NAME));
    }

    @Test
    void answersTheChangeSetOfAnExtractMethodScenarioWithPathsUnderItsRoot() {
        server.scripted(Scenarios.EXTRACT_METHOD_FROM_AN_EXPRESSION);

        ChangeSet changes =
                server.extractMethod(TOTALS, new Range(new Position(29, 15), new Position(29, 28)), "scaled");

        assertThat(changes)
                .isEqualTo(((Answer.Extracted) Scenarios.EXTRACT_METHOD_FROM_AN_EXPRESSION
                                .inside(ROOT)
                                .answer())
                        .changes());
        assertThat(changes.changes())
                .singleElement()
                .isInstanceOfSatisfying(
                        FileChange.EditFile.class,
                        edit -> assertThat(edit.file()).isEqualTo(TOTALS));
    }

    @Test
    void throwsTheRefusalOfAnExtractMethodScenario() {
        server.scripted(Scenarios.EXTRACT_METHOD_FROM_HALF_A_STATEMENT);

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() ->
                        server.extractMethod(TOTALS, new Range(new Position(5, 30), new Position(6, 20)), "partial"))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NOT_EXTRACTABLE));
    }

    @Test
    void answersTheChangeSetOfAMoveScenarioWithPathsUnderItsRoot() {
        server.scripted(Scenarios.MOVE_AN_INNER_TYPE_THAT_USES_THE_OUTER_INSTANCE);

        ChangeSet changes = server.moveTypeToFile(SHAPES, new Position(17, 17));

        assertThat(changes)
                .isEqualTo(((Answer.Moved) Scenarios.MOVE_AN_INNER_TYPE_THAT_USES_THE_OUTER_INSTANCE
                                .inside(ROOT)
                                .answer())
                        .changes());
        assertThat(changes.changes())
                .contains(new FileChange.CreateFile(ROOT.resolve("src/main/java/demo/Label.java")));
    }

    @Test
    void throwsTheRefusalOfAMoveScenario() {
        server.scripted(Scenarios.MOVE_A_TOP_LEVEL_TYPE);

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> server.moveTypeToFile(SHAPES, new Position(2, 13)))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NOT_MOVABLE));
        assertThat(server.asked()).containsExactly(new Request.MoveTypeToFile(SHAPES, new Position(2, 13)));
    }

    @Test
    void answersTheChangeSetOfAnInlineScenarioWithPathsUnderItsRoot() {
        server.scripted(Scenarios.INLINE_A_CONSTANT);

        ChangeSet changes = server.inline(PRICES, new Position(3, 21));

        assertThat(changes)
                .isEqualTo(((Answer.Inlined)
                                Scenarios.INLINE_A_CONSTANT.inside(ROOT).answer())
                        .changes());
        assertThat(server.asked()).containsExactly(new Request.Inline(PRICES, new Position(3, 21)));
    }

    @Test
    void throwsTheRefusalOfAnInlineScenario() {
        server.scripted(Scenarios.INLINE_A_VARIABLE_ASSIGNED_TWICE);

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> server.inline(PRICES, new Position(20, 12)))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NOT_INLINABLE));
    }

    @Test
    void answersNothingForAFileWhoseImportsAreInOrder() {
        server.scripted(Scenarios.ORGANIZE_IMPORTS_IN_ORDER);

        assertThat(server.organizeImports(TIDY)).isEmpty();
    }

    @Test
    void throwsTheRefusalOfAnOrganizeImportsScenario() {
        server.scripted(Scenarios.ORGANIZE_IMPORTS_WHILE_THE_SERVER_IMPORTS)
                .scripted(Scenarios.ORGANIZE_IMPORTS_OUTSIDE_THE_PROJECT);

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> server.organizeImports(UNTIDY))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> server.organizeImports(Path.of("/outside/Elsewhere.java")))
                .satisfies(exception ->
                        assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));
    }

    @Test
    void throwsTheRefusalOfAScenarioWithItsKeyAndItsParameters() {
        server.scripted(Scenarios.RENAME_TO_AN_INVALID_NAME)
                .scripted(Scenarios.RENAME_WHERE_THERE_IS_NO_SYMBOL)
                .scripted(Scenarios.DIAGNOSTICS_WHILE_THE_SERVER_IMPORTS)
                .scripted(Scenarios.DIAGNOSTICS_OUTSIDE_THE_PROJECT);

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> server.rename(GREETER, new Position(6, 15), "1st"))
                .satisfies(exception -> {
                    assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.INVALID_NAME);
                    assertThat(exception.parameters())
                            .containsEntry("file", GREETER.toString())
                            .containsEntry("name", "1st");
                });
        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> server.rename(GREETER, new Position(4, 0), "anything"))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NO_SUCH_SYMBOL));
        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> server.diagnostics(MAIN))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> server.diagnostics(Path.of("/outside/Elsewhere.java")))
                .satisfies(exception -> {
                    assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT);
                    assertThat(exception.parameters()).containsEntry("file", "/outside/Elsewhere.java");
                });
    }

    @Test
    void recognizesARequestWhateverTheSpellingOfItsPath() {
        server.scripted(Scenarios.CLEAN_FILE);

        assertThat(server.diagnostics(ROOT.resolve("src/main/../main/java/demo/./Main.java")))
                .isEmpty();
    }

    @Test
    void failsOnARequestNoScenarioScripted() {
        server.scripted(Scenarios.RENAME_LOCAL_VARIABLE);

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> server.rename(GREETER, new Position(6, 15), "another"))
                .withMessageContaining("not scripted")
                .withMessageContaining("another");
        assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> server.diagnostics(MAIN));
    }

    @Test
    void remembersEveryRequestInOrderTheUnscriptedOnesIncluded() {
        server.scripted(Scenarios.CLEAN_FILE);

        server.diagnostics(MAIN);
        assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> server.diagnostics(GREETER));

        assertThat(server.asked()).containsExactly(new Request.Diagnostics(MAIN), new Request.Diagnostics(GREETER));
    }

    @Test
    void refusesToScriptTwoAnswersToTheSameRequest() {
        server.scripted(Scenarios.CLEAN_FILE);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> server.scripted(Scenarios.DIAGNOSTICS_WHILE_THE_SERVER_IMPORTS));
    }

    @Test
    void listsWhatItWasScriptedWithInTheFailure() {
        server.scripted(Scenarios.CLEAN_FILE);

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> server.diagnostics(GREETER))
                .withMessageContaining(MAIN.toString());
    }
}
