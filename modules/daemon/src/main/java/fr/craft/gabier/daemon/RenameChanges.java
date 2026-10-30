package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.domain.FileChange;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.editing.domain.Range;
import fr.craft.gabier.editing.domain.TextEdit;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.eclipse.lsp4j.CreateFile;
import org.eclipse.lsp4j.DeleteFile;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.SnippetTextEdit;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

/**
 * What jdtls answers to a rename ({@code WorkspaceEdit}), as the changes of the domain. jdtls answers with {@code
 * documentChanges} to a client that declares the three file operations ({@link JdtlsCapabilities}): the edits of each
 * file, and the move of the file of a renamed type, in the order to apply them.
 *
 * <p>The domain is stricter than the protocol, and this class is where the two meet:
 *
 * <ul>
 *   <li>a {@code TextDocumentEdit} with no edit is legal in LSP and says nothing: it is dropped, since a change of the
 *       domain has at least one edit;
 *   <li>a position past the end of its line is clamped to the end of the line, and a line past the end of the text to
 *       the end of the text, as the protocol defines them; the domain refuses such positions, so the clamping is
 *       decided here, on the text of the file as it is on disk. A file that is not on disk (it is created by the set,
 *       or it has been moved) is not clamped: the domain refuses the edit if it does not fit.
 * </ul>
 */
final class RenameChanges {

    private static final Pattern LINE_TERMINATOR = Pattern.compile("\\r\\n|\\r|\\n");

    private RenameChanges() {}

    /**
     * @param texts the text of a file as it is on disk, none for a file that is not there
     * @return the changes in the order of the answer, none when the answer changes nothing
     * @throws JdtlsException for what the adapter did not ask jdtls for and cannot apply: edits given as {@code
     *     changes}, a snippet edit, a file operation it does not know, a file that is not a {@code file:} URI
     */
    static List<FileChange> of(WorkspaceEdit edit, Function<Path, Optional<String>> texts) {
        List<Either<TextDocumentEdit, ResourceOperation>> documentChanges = edit.getDocumentChanges();
        List<FileChange> changes = new ArrayList<>();
        if (documentChanges == null) {
            if (edit.getChanges() != null && !edit.getChanges().isEmpty()) {
                throw new JdtlsException("jdtls sent its edits as changes, not as documentChanges, which the client"
                        + " declared: " + edit.getChanges().keySet());
            }
            return changes;
        }
        for (Either<TextDocumentEdit, ResourceOperation> change : documentChanges) {
            if (change.isLeft()) {
                editsOf(change.getLeft(), texts).ifPresent(changes::add);
            } else {
                changes.add(resource(change.getRight()));
            }
        }
        return changes;
    }

    private static Optional<FileChange> editsOf(TextDocumentEdit document, Function<Path, Optional<String>> texts) {
        if (document.getEdits().isEmpty()) {
            return Optional.empty();
        }
        Path file = path(document.getTextDocument().getUri());
        Optional<List<Integer>> lines = texts.apply(file).map(RenameChanges::lengthsOfLines);
        List<TextEdit> edits =
                document.getEdits().stream().map(edit -> edit(edit, lines)).toList();

        return Optional.of(new FileChange.EditFile(file, edits));
    }

    private static TextEdit edit(
            Either<org.eclipse.lsp4j.TextEdit, SnippetTextEdit> either, Optional<List<Integer>> lines) {
        if (either.isRight()) {
            throw new JdtlsException("jdtls sent a snippet edit, which gabier did not ask for: " + either.getRight());
        }
        org.eclipse.lsp4j.TextEdit edit = either.getLeft();
        var range = new Range(
                position(edit.getRange().getStart(), lines),
                position(edit.getRange().getEnd(), lines));

        return new TextEdit(range, edit.getNewText());
    }

    /** The position of the protocol, clamped to the text when it is known. */
    private static Position position(org.eclipse.lsp4j.Position position, Optional<List<Integer>> lines) {
        if (lines.isEmpty()) {
            return new Position(position.getLine(), position.getCharacter());
        }
        List<Integer> lengths = lines.get();
        int last = lengths.size() - 1;
        if (position.getLine() > last) {
            return new Position(last, lengths.get(last));
        }

        return new Position(position.getLine(), Math.min(position.getCharacter(), lengths.get(position.getLine())));
    }

    /** The length of each line of the text without its terminator, the empty line after the last terminator included. */
    private static List<Integer> lengthsOfLines(String text) {
        return Arrays.stream(LINE_TERMINATOR.split(text, -1))
                .map(String::length)
                .toList();
    }

    private static FileChange resource(ResourceOperation operation) {
        return switch (operation) {
            case CreateFile create -> new FileChange.CreateFile(path(create.getUri()));
            case RenameFile rename -> new FileChange.RenameFile(path(rename.getOldUri()), path(rename.getNewUri()));
            case DeleteFile delete -> new FileChange.DeleteFile(path(delete.getUri()));
            default ->
                throw new JdtlsException(
                        "jdtls sent the file operation " + operation.getKind() + ", which gabier does not know");
        };
    }

    private static Path path(String uri) {
        URI parsed = URI.create(uri);
        if (!"file".equalsIgnoreCase(parsed.getScheme())) {
            throw new JdtlsException("jdtls sent a change for " + uri + ", which is not a file");
        }

        return Path.of(parsed).toAbsolutePath().normalize();
    }
}
