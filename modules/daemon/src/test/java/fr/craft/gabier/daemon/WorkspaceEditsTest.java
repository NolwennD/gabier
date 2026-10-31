package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.gabier.editing.domain.FileChange;
import fr.craft.gabier.editing.domain.FileChange.CreateFile;
import fr.craft.gabier.editing.domain.FileChange.DeleteFile;
import fr.craft.gabier.editing.domain.FileChange.EditFile;
import fr.craft.gabier.editing.domain.FileChange.RenameFile;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.editing.domain.Range;
import fr.craft.gabier.editing.domain.TextEdit;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.SnippetTextEdit;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

/**
 * The answer of jdtls to a rename in the terms of the domain: what is dropped, what is clamped, what is refused. The
 * answers are made by hand, as jdtls sends them; no jdtls is started.
 */
class WorkspaceEditsTest {

    private static final Path GREETER = Path.of("/project/Greeter.java");
    private static final Path WELCOMER = Path.of("/project/Welcomer.java");

    /** The text of the files on disk. */
    private Map<Path, String> disk = Map.of(GREETER, "class Greeter {\n    int x;\n}\n");

    private List<FileChange> of(WorkspaceEdit edit) {
        return WorkspaceEdits.of(edit, file -> Optional.ofNullable(disk.get(file)));
    }

    private static WorkspaceEdit workspaceEdit(Either<TextDocumentEdit, ResourceOperation>... changes) {
        return new WorkspaceEdit(Arrays.asList(changes));
    }

    private static Either<TextDocumentEdit, ResourceOperation> edits(Path file, org.eclipse.lsp4j.TextEdit... edits) {
        return Either.forLeft(new TextDocumentEdit(
                new VersionedTextDocumentIdentifier(file.toUri().toString(), 1),
                Arrays.stream(edits)
                        .map(Either::<org.eclipse.lsp4j.TextEdit, SnippetTextEdit>forLeft)
                        .toList()));
    }

    private static org.eclipse.lsp4j.TextEdit lsp(int startLine, int startColumn, int endLine, int endColumn) {
        return new org.eclipse.lsp4j.TextEdit(
                new org.eclipse.lsp4j.Range(
                        new org.eclipse.lsp4j.Position(startLine, startColumn),
                        new org.eclipse.lsp4j.Position(endLine, endColumn)),
                "new");
    }

    private static TextEdit domain(int startLine, int startColumn, int endLine, int endColumn) {
        return new TextEdit(new Range(new Position(startLine, startColumn), new Position(endLine, endColumn)), "new");
    }

    @Test
    void keepsTheChangesInTheOrderOfTheAnswer() {
        var move = new org.eclipse.lsp4j.RenameFile(
                GREETER.toUri().toString(), WELCOMER.toUri().toString());

        assertThat(of(workspaceEdit(edits(GREETER, lsp(0, 6, 0, 13)), Either.forRight(move))))
                .containsExactly(
                        new EditFile(GREETER, List.of(domain(0, 6, 0, 13))), new RenameFile(GREETER, WELCOMER));
    }

    @Test
    void keepsEveryEditOfAFileInTheOrderOfTheAnswer() {
        assertThat(of(workspaceEdit(edits(GREETER, lsp(1, 8, 1, 9), lsp(0, 6, 0, 13)))))
                .containsExactly(new EditFile(GREETER, List.of(domain(1, 8, 1, 9), domain(0, 6, 0, 13))));
    }

    @Test
    void convertsTheCreationAndTheDeletionOfAFile() {
        var create = new org.eclipse.lsp4j.CreateFile(WELCOMER.toUri().toString());
        var delete = new org.eclipse.lsp4j.DeleteFile(GREETER.toUri().toString());

        assertThat(of(workspaceEdit(Either.forRight(create), Either.forRight(delete))))
                .containsExactly(new CreateFile(WELCOMER), new DeleteFile(GREETER));
    }

    @Test
    void putsThePathsOfFileUrisInTheirNormalForm() {
        var spelled = Path.of("/project/src/../Greeter.java").toUri().toString();
        var delete = new org.eclipse.lsp4j.DeleteFile(spelled);

        assertThat(of(workspaceEdit(Either.forRight(delete)))).containsExactly(new DeleteFile(GREETER));
    }

    @Test
    void saysNothingForAnAnswerWithoutDocumentChanges() {
        assertThat(of(new WorkspaceEdit())).isEmpty();
    }

    @Test
    void saysNothingForAnAnswerWithNeitherChangesNorDocumentChanges() {
        var edit = new WorkspaceEdit();
        edit.setChanges(null);

        assertThat(of(edit)).isEmpty();
    }

    @Test
    void saysNothingForAnAnswerWithEmptyChanges() {
        assertThat(of(new WorkspaceEdit(Map.of()))).isEmpty();
    }

    @Test
    void convertsAnAnswerThatGivesItsEditsAsChanges() {
        var edit = new WorkspaceEdit(Map.of(GREETER.toUri().toString(), List.of(lsp(0, 6, 0, 13), lsp(1, 4, 1, 5))));

        assertThat(of(edit)).containsExactly(new EditFile(GREETER, List.of(domain(0, 6, 0, 13), domain(1, 4, 1, 5))));
    }

    @Test
    void keepsTheFilesOfChangesInTheOrderOfTheAnswerAndDropsThoseWithNoEdit() {
        var changes = new LinkedHashMap<String, List<org.eclipse.lsp4j.TextEdit>>();
        changes.put(WELCOMER.toUri().toString(), List.of(lsp(0, 0, 0, 1)));
        changes.put(Path.of("/project/Empty.java").toUri().toString(), List.of());
        changes.put(GREETER.toUri().toString(), List.of(lsp(0, 6, 0, 13)));

        assertThat(of(new WorkspaceEdit(changes)))
                .containsExactly(
                        new EditFile(WELCOMER, List.of(domain(0, 0, 0, 1))),
                        new EditFile(GREETER, List.of(domain(0, 6, 0, 13))));
    }

    @Test
    void clampsTheEditsGivenAsChangesLikeTheOthers() {
        var edit = new WorkspaceEdit(Map.of(GREETER.toUri().toString(), List.of(lsp(0, 6, 0, 99))));

        assertThat(of(edit)).containsExactly(new EditFile(GREETER, List.of(domain(0, 6, 0, 15))));
    }

    @Test
    void refusesChangesToSomethingThatIsNotAFile() {
        var edit = new WorkspaceEdit(Map.of("jdt://contents/java.base/String.class", List.of(lsp(0, 0, 0, 1))));

        assertThatThrownBy(() -> of(edit)).isInstanceOf(JdtlsException.class).hasMessageContaining("not a file");
    }

    @Test
    void prefersTheDocumentChangesWhenTheAnswerGivesBoth() {
        var edit = workspaceEdit(edits(GREETER, lsp(0, 6, 0, 13)));
        edit.setChanges(Map.of(WELCOMER.toUri().toString(), List.of(lsp(0, 0, 0, 1))));

        assertThat(of(edit)).containsExactly(new EditFile(GREETER, List.of(domain(0, 6, 0, 13))));
    }

    @Test
    void dropsTheEditsOfADocumentWithNoEdit() {
        assertThat(of(workspaceEdit(edits(GREETER), edits(GREETER, lsp(0, 6, 0, 13)), edits(WELCOMER))))
                .containsExactly(new EditFile(GREETER, List.of(domain(0, 6, 0, 13))));
    }

    @Test
    void clampsAColumnPastTheEndOfItsLineToTheEndOfTheLine() {
        // "class Greeter {" has 15 columns, "    int x;" has 10.
        assertThat(of(workspaceEdit(edits(GREETER, lsp(0, 6, 0, 99), lsp(1, 40, 1, 41)))))
                .containsExactly(new EditFile(GREETER, List.of(domain(0, 6, 0, 15), domain(1, 10, 1, 10))));
    }

    @Test
    void clampsALineAfterTheLastToTheEndOfTheText() {
        // The text ends with a newline: its last line is the empty one.
        assertThat(of(workspaceEdit(edits(GREETER, lsp(1, 4, 7, 3)))))
                .containsExactly(new EditFile(GREETER, List.of(domain(1, 4, 3, 0))));
    }

    @Test
    void measuresTheLinesWithoutTheirTerminators() {
        disk = Map.of(GREETER, "ab\r\ncd\ref\n");

        // "ab" ends at column 2 though a CR LF follows it, and "cd" too though a CR does.
        assertThat(of(workspaceEdit(edits(GREETER, lsp(0, 0, 0, 4), lsp(1, 1, 1, 9), lsp(2, 0, 2, 5)))))
                .containsExactly(
                        new EditFile(GREETER, List.of(domain(0, 0, 0, 2), domain(1, 1, 1, 2), domain(2, 0, 2, 2))));
    }

    @Test
    void leavesAPositionInsideTheTextAsItIs() {
        assertThat(of(workspaceEdit(edits(GREETER, lsp(0, 6, 0, 13)))))
                .containsExactly(new EditFile(GREETER, List.of(domain(0, 6, 0, 13))));
    }

    @Test
    void doesNotClampTheEditsOfAFileThatIsNotOnDisk() {
        // The domain refuses what does not fit, once it knows the file.
        assertThat(of(workspaceEdit(edits(WELCOMER, lsp(0, 6, 0, 99)))))
                .containsExactly(new EditFile(WELCOMER, List.of(domain(0, 6, 0, 99))));
    }

    @Test
    void refusesASnippetEdit() {
        var snippet = new TextDocumentEdit(
                new VersionedTextDocumentIdentifier(GREETER.toUri().toString(), 1),
                List.of(Either.forRight(new SnippetTextEdit())));

        assertThatThrownBy(() -> of(workspaceEdit(Either.forLeft(snippet))))
                .isInstanceOf(JdtlsException.class)
                .hasMessageContaining("snippet edit");
    }

    @Test
    void refusesAFileOperationItDoesNotKnow() {
        var unknown = new ResourceOperation("copy") {};

        assertThatThrownBy(() -> of(workspaceEdit(Either.forRight(unknown))))
                .isInstanceOf(JdtlsException.class)
                .hasMessageContaining("the file operation copy");
    }

    @Test
    void refusesAChangeToSomethingThatIsNotAFile() {
        var delete = new org.eclipse.lsp4j.DeleteFile("jdt://contents/java.base/String.class");

        assertThatThrownBy(() -> of(workspaceEdit(Either.forRight(delete))))
                .isInstanceOf(JdtlsException.class)
                .hasMessageContaining("jdt://contents/java.base/String.class")
                .hasMessageContaining("not a file");
    }
}
