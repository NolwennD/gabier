package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.domain.FileChange;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.editing.domain.Range;
import fr.craft.gabier.editing.domain.TextEdit;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
 * What jdtls answers with a {@code WorkspaceEdit} (a rename, a code action), as the changes of the domain. jdtls
 * answers a rename with {@code documentChanges} to a client that declares the three file operations ({@link
 * JdtlsCapabilities}): the edits of each file, and the move of the file of a renamed type, in the order to apply them.
 * It answers a code action (the organization of the imports) with {@code changes}, the edits of each file by its URI,
 * whatever the client declares (checked on the real jdtls, see {@code Scenarios}). Both forms are read; when an answer
 * has both, the {@code documentChanges} win, as the protocol says.
 *
 * <p>The domain is stricter than the protocol, and this class is where the two meet:
 *
 * <ul>
 *   <li>a {@code TextDocumentEdit}, or a file of {@code changes}, with no edit is legal in LSP and says nothing: it is
 *       dropped, since a change of the domain has at least one edit;
 *   <li>a position past the end of its line is clamped to the end of the line, and a line past the end of the text to
 *       the end of the text, as the protocol defines them; the domain refuses such positions, so the clamping is
 *       decided here, on the text of the file as it is on disk. A file that is not on disk (it is created by the set,
 *       or it has been moved) is not clamped: the domain refuses the edit if it does not fit.
 * </ul>
 */
final class WorkspaceEdits {

    private static final Pattern LINE_TERMINATOR = Pattern.compile("\\r\\n|\\r|\\n");

    private WorkspaceEdits() {}

    /**
     * Turns the answer of jdtls into a change set.
     *
     * @param texts the text of a file as it is on disk, none for a file that is not there
     * @return the changes in the order of the answer, none when the answer changes nothing
     * @throws JdtlsException for what the adapter did not ask jdtls for and cannot apply: a snippet edit, a file
     *     operation it does not know, a file that is not a {@code file:} URI
     */
    static List<FileChange> of(WorkspaceEdit edit, Function<Path, Optional<String>> texts) {
        List<Either<TextDocumentEdit, ResourceOperation>> documentChanges = edit.getDocumentChanges();
        List<FileChange> changes = new ArrayList<>();
        if (documentChanges == null) {
            Map<String, List<org.eclipse.lsp4j.TextEdit>> byFile =
                    edit.getChanges() == null ? Map.of() : edit.getChanges();
            byFile.forEach((uri, edits) -> {
                var plain = edits.stream()
                        .map(Either::<org.eclipse.lsp4j.TextEdit, SnippetTextEdit>forLeft)
                        .toList();
                editsOf(uri, plain, texts).ifPresent(changes::add);
            });
            return changes;
        }
        for (Either<TextDocumentEdit, ResourceOperation> change : documentChanges) {
            if (change.isLeft()) {
                TextDocumentEdit document = change.getLeft();
                editsOf(document.getTextDocument().getUri(), document.getEdits(), texts)
                        .ifPresent(changes::add);
            } else {
                changes.add(resource(change.getRight()));
            }
        }
        return changes;
    }

    /**
     * The changes of a move in a stable order, whatever the order jdtls answered them in (it varies from one run to the
     * next): first the edits (and the creations) of every file by path, then the moves of files by the path they leave,
     * then the removals by path. The edits of a file come before its move, which is what the answer of {@code java/move}
     * does (its edits are under the old path), so applying the set in order works; the edits of one path keep the order
     * of the answer, since each is applied to the result of the previous ones.
     */
    static List<FileChange> inOrderOfAMove(List<FileChange> changes) {
        return changes.stream()
                .sorted(Comparator.comparingInt(WorkspaceEdits::rankOfAMove).thenComparing(WorkspaceEdits::pathOfAMove))
                .toList();
    }

    private static int rankOfAMove(FileChange change) {
        return switch (change) {
            case FileChange.CreateFile _ -> 0;
            case FileChange.EditFile _ -> 0;
            case FileChange.RenameFile _ -> 1;
            case FileChange.DeleteFile _ -> 2;
        };
    }

    private static Path pathOfAMove(FileChange change) {
        return switch (change) {
            case FileChange.CreateFile create -> create.file();
            case FileChange.EditFile edit -> edit.file();
            case FileChange.RenameFile rename -> rename.from();
            case FileChange.DeleteFile delete -> delete.file();
        };
    }

    private static Optional<FileChange> editsOf(
            String uri,
            List<Either<org.eclipse.lsp4j.TextEdit, SnippetTextEdit>> proposed,
            Function<Path, Optional<String>> texts) {
        if (proposed.isEmpty()) {
            return Optional.empty();
        }
        Path file = path(uri);
        Function<org.eclipse.lsp4j.Position, Position> position = texts.apply(file)
                .map(WorkspaceEdits::lengthsOfLines)
                .<Function<org.eclipse.lsp4j.Position, Position>>map(lengths -> given -> clamped(given, lengths))
                .orElse(WorkspaceEdits::unclamped);
        List<TextEdit> edits =
                proposed.stream().map(edit -> edit(edit, position)).toList();

        return Optional.of(new FileChange.EditFile(file, edits));
    }

    private static TextEdit edit(
            Either<org.eclipse.lsp4j.TextEdit, SnippetTextEdit> either,
            Function<org.eclipse.lsp4j.Position, Position> position) {
        if (either.isRight()) {
            throw new JdtlsException("jdtls sent a snippet edit, which gabier did not ask for: " + either.getRight());
        }
        org.eclipse.lsp4j.TextEdit edit = either.getLeft();
        var range = new Range(
                position.apply(edit.getRange().getStart()),
                position.apply(edit.getRange().getEnd()));

        return new TextEdit(range, edit.getNewText());
    }

    /** The position of the protocol, as it is when the text of the file is not known. */
    private static Position unclamped(org.eclipse.lsp4j.Position position) {
        return new Position(position.getLine(), position.getCharacter());
    }

    /** The position of the protocol, clamped to the lines of the text whose lengths are given. */
    private static Position clamped(org.eclipse.lsp4j.Position position, List<Integer> lengths) {
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
