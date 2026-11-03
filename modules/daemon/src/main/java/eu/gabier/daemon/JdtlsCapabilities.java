package eu.gabier.daemon;

import java.util.List;
import java.util.Map;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CodeActionCapabilities;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionKindCapabilities;
import org.eclipse.lsp4j.CodeActionLiteralSupportCapabilities;
import org.eclipse.lsp4j.CodeActionResolveSupportCapabilities;
import org.eclipse.lsp4j.CompletionCapabilities;
import org.eclipse.lsp4j.CompletionItemCapabilities;
import org.eclipse.lsp4j.DidChangeWatchedFilesCapabilities;
import org.eclipse.lsp4j.RenameCapabilities;
import org.eclipse.lsp4j.ResourceOperationKind;
import org.eclipse.lsp4j.SemanticTokensCapabilities;
import org.eclipse.lsp4j.SemanticTokensClientCapabilitiesRequests;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceEditCapabilities;

/**
 * The capabilities the client declares to jdtls. A server offers a refactoring only to a client that says it can
 * receive it: these are the ones of the reference driver (section 7 of the design), which a refactoring needs. jdtls
 * puts the move of a file in the edit of a rename (the file of a renamed type) only for a client that declares all
 * three resource operations, create, rename and delete. It registers the files it wants watched, and so learns of the
 * edits made on disk, only for a client that declares the dynamic registration of {@code
 * workspace/didChangeWatchedFiles}. It offers the code action {@code source.organizeImports}, which computes its edit
 * only when it is resolved, to a client that declares the kind among its literals and {@code resolveSupport} for {@code
 * edit}; the preferences that decide what it writes are {@link JdtlsSettings#javaPreferences}, not capabilities. The
 * same list of kinds decides which code actions of a range it gives (see {@link #QUICK_ASSIST}). The semantic tokens
 * of a file are what tells a component of a record from a field ({@link RecordComponents}).
 */
final class JdtlsCapabilities {

    /**
     * The kind jdtls gives the rewrites it offers on a piece of code (a concatenation to a text block, a lambda to a
     * method reference): its own, not one of the Language Server Protocol, so LSP4J has no constant for it. jdtls
     * (ClientPreferences.isSupportedCodeActionKind) drops an action whose kind the client does not declare, by prefix.
     */
    static final String QUICK_ASSIST = "quickassist";

    private JdtlsCapabilities() {}

    /**
     * The capabilities of jdtls beyond the protocol ({@code initializationOptions.extendedClientCapabilities}, read by
     * its {@code ClientPreferences}). A type that several packages declare is left unimported, without a word, unless
     * the client says it can choose: {@code advancedOrganizeImportsSupport} makes {@code source.organizeImports} ask
     * for the choice, with {@code java.action.organizeImports.chooseImports}, and {@code executeClientCommandSupport}
     * lets jdtls send that command to the client ({@code workspace/executeClientCommand}).
     */
    static Map<String, Object> extended() {
        return Map.of("advancedOrganizeImportsSupport", true, "executeClientCommandSupport", true);
    }

    static ClientCapabilities forRefactoring() {
        var capabilities = new ClientCapabilities();
        capabilities.setWorkspace(workspace());
        capabilities.setTextDocument(textDocument());
        return capabilities;
    }

    private static WorkspaceClientCapabilities workspace() {
        var workspaceEdit = new WorkspaceEditCapabilities();
        workspaceEdit.setDocumentChanges(true);
        workspaceEdit.setResourceOperations(
                List.of(ResourceOperationKind.Create, ResourceOperationKind.Rename, ResourceOperationKind.Delete));
        var workspace = new WorkspaceClientCapabilities();
        workspace.setApplyEdit(true);
        // The files jdtls asks to have watched: it registers them only for a client that declares this.
        var watched = new DidChangeWatchedFilesCapabilities();
        watched.setDynamicRegistration(true);
        workspace.setDidChangeWatchedFiles(watched);
        workspace.setWorkspaceEdit(workspaceEdit);
        return workspace;
    }

    /**
     * The semantic tokens of a whole file: jdtls announces its provider, and its legend of token types (among them
     * {@code recordComponent}, which tells a component of a record from a field), only to a client that declares them.
     */
    private static SemanticTokensCapabilities semanticTokens() {
        var requests = new SemanticTokensClientCapabilitiesRequests();
        requests.setFull(true);

        return new SemanticTokensCapabilities(requests, List.of(), List.of(), List.of("relative"));
    }

    private static TextDocumentClientCapabilities textDocument() {
        var kinds = new CodeActionKindCapabilities(List.of(
                CodeActionKind.QuickFix,
                CodeActionKind.Refactor,
                CodeActionKind.RefactorExtract,
                CodeActionKind.RefactorInline,
                CodeActionKind.RefactorRewrite,
                CodeActionKind.Source,
                CodeActionKind.SourceOrganizeImports,
                QUICK_ASSIST));
        var codeAction = new CodeActionCapabilities();
        codeAction.setCodeActionLiteralSupport(new CodeActionLiteralSupportCapabilities(kinds));
        // The edit of a code action is computed when the action is chosen, not when the list is asked for.
        codeAction.setResolveSupport(new CodeActionResolveSupportCapabilities(List.of("edit")));
        codeAction.setDataSupport(true);
        // The creation of a type takes the snippet jdtls completes an empty file with.
        var completionItem = new CompletionItemCapabilities();
        completionItem.setSnippetSupport(true);
        var completion = new CompletionCapabilities();
        completion.setCompletionItem(completionItem);
        // A rename is asked for the range of the symbol first: that is how jdtls says there is nothing to rename.
        var rename = new RenameCapabilities();
        rename.setPrepareSupport(true);
        var textDocument = new TextDocumentClientCapabilities();
        textDocument.setSemanticTokens(semanticTokens());
        textDocument.setCodeAction(codeAction);
        textDocument.setCompletion(completion);
        textDocument.setRename(rename);
        return textDocument;
    }
}
