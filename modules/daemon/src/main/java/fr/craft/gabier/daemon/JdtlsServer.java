package fr.craft.gabier.daemon;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jspecify.annotations.Nullable;

/**
 * The language server of the protocol plus the requests of jdtls that are not in it. The only one gabier uses is {@code
 * java/getRefactorEdit}: the edit of a refactoring that jdtls offers as a code action, asked for by its name, together
 * with the place where the client is expected to rename what the refactoring introduced.
 */
interface JdtlsServer extends LanguageServer {

    /** The edit of a refactoring; jdtls answers null when it cannot do it where it is asked. */
    @JsonRequest("java/getRefactorEdit")
    CompletableFuture<RefactorEdit> getRefactorEdit(RefactorEditParams params);

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
