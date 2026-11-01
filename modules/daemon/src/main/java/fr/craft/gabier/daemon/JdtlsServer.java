package fr.craft.gabier.daemon;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jspecify.annotations.Nullable;

/**
 * The language server of the protocol plus the requests of jdtls that are not in it: {@code java/getRefactorEdit}, the
 * edit of a refactoring that jdtls offers as a code action, asked for by its name, together with the place where the
 * client is expected to rename what the refactoring introduced; {@code java/move}; and {@code java/cleanup}.
 */
interface JdtlsServer extends LanguageServer {

    /** The edit of a refactoring; jdtls answers null when it cannot do it where it is asked. */
    @JsonRequest("java/getRefactorEdit")
    CompletableFuture<RefactorEdit> getRefactorEdit(RefactorEditParams params);

    /**
     * The clean-ups of a file, the ones of the setting {@code java.cleanup.actions} in the order of the list: one edit
     * of the whole text, none (or a null answer) when nothing changes. jdtls offers no way to name the clean-ups in the
     * request.
     */
    @JsonRequest("java/cleanup")
    CompletableFuture<WorkspaceEdit> cleanup(TextDocumentIdentifier file);

    /**
     * The edit of a move, in the same shape as a refactoring: when jdtls cannot do it, there is no edit and a message
     * (or it answers null).
     */
    @JsonRequest("java/move")
    CompletableFuture<RefactorEdit> move(MoveParams params);

    /**
     * What is asked of {@code java/move}, which jdtls reads by the kind of the move: one request type for every kind
     * (LSP4J maps a method name once), so a field is null for the kinds that do not read it, and Gson leaves it out.
     *
     * @param moveKind the move: {@code moveTypeToNewFile} for a nested type, {@code moveResource} for files
     * @param params the file and the position (an empty range) of the type to move; for {@code moveTypeToNewFile}
     * @param updateReferences whether the references to what is moved are updated
     * @param sourceUris the files to move, as URIs; for {@code moveResource}
     * @param destination the URI of the folder of the package they go to, which jdtls must know as a package; for
     *     {@code moveResource}
     */
    record MoveParams(
            String moveKind,
            @Nullable CodeActionParams params,
            boolean updateReferences,
            @Nullable List<String> sourceUris,
            @Nullable String destination) {

        /** The move of the nested type at the position of {@code params} to a file of its own. */
        static MoveParams typeToNewFile(CodeActionParams params) {
            return new MoveParams("moveTypeToNewFile", params, true, null, null);
        }

        /** The move of files to the package whose folder is given, the references updated. */
        static MoveParams resources(List<String> sourceUris, String destination) {
            return new MoveParams("moveResource", null, true, sourceUris, destination);
        }
    }

    /**
     * What is asked of {@code java/getRefactorEdit}.
     *
     * @param command the refactoring, for instance {@code extractVariable}
     * @param context the file and the selection the refactoring applies to
     */
    record RefactorEditParams(String command, CodeActionParams context) {}

    /**
     * What jdtls answers: the edit, and the command the client then runs. For a refactoring that introduces a name, the
     * command is {@code java.action.rename} and its argument is where the name is in the text the edit gives.
     *
     * @param edit the edit of the refactoring
     * @param command the follow-up, none when the refactoring introduces no name
     * @param errorMessage why jdtls cannot do the refactoring, none when it can
     */
    record RefactorEdit(
            @Nullable WorkspaceEdit edit,
            @Nullable FollowUp command,
            @Nullable String errorMessage) {}

    /**
     * A command jdtls asks the client to run once it has applied the edit.
     *
     * @param command its name, {@code java.action.rename} for a rename
     * @param arguments where the rename takes place
     */
    record FollowUp(String command, List<RenamePosition> arguments) {}

    /**
     * Where the name introduced by a refactoring is, in the text after the edit.
     *
     * @param uri the file
     * @param offset where the name starts, in characters from the start of the text
     * @param length the length of the name
     */
    record RenamePosition(String uri, int offset, int length) {}
}
