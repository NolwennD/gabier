package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.lsp4j.ClientCapabilities;
import org.junit.jupiter.api.Test;

/** What the client tells jdtls it can do, which is what makes jdtls offer the refactorings. */
class JdtlsCapabilitiesTest {

    private final ClientCapabilities capabilities = JdtlsCapabilities.forRefactoring();

    @Test
    void appliesTheEditsJdtlsSends() {
        assertThat(capabilities.getWorkspace().getApplyEdit()).isTrue();
    }

    @Test
    void understandsEditsAsDocumentChangesThatMayCreateRenameAndDeleteFiles() {
        var workspaceEdit = capabilities.getWorkspace().getWorkspaceEdit();

        assertThat(workspaceEdit.getDocumentChanges()).isTrue();
        // jdtls moves the file of a renamed type only for a client that declares all three operations.
        assertThat(workspaceEdit.getResourceOperations()).containsExactlyInAnyOrder("create", "rename", "delete");
    }

    @Test
    void asksForTheRangeOfTheSymbolBeforeARename() {
        assertThat(capabilities.getTextDocument().getRename().getPrepareSupport())
                .isTrue();
    }

    @Test
    void acceptsCodeActionsAsLiteralsOfTheKindsOfARefactoring() {
        var kinds = capabilities
                .getTextDocument()
                .getCodeAction()
                .getCodeActionLiteralSupport()
                .getCodeActionKind()
                .getValueSet();

        assertThat(kinds)
                .contains(
                        "quickfix",
                        "refactor",
                        "refactor.extract",
                        "refactor.inline",
                        "refactor.rewrite",
                        "source",
                        "source.organizeImports");
    }

    @Test
    void resolvesTheEditOfACodeActionAndKeepsItsData() {
        var codeAction = capabilities.getTextDocument().getCodeAction();

        assertThat(codeAction.getResolveSupport().getProperties()).containsExactly("edit");
        assertThat(codeAction.getDataSupport()).isTrue();
    }

    @Test
    void acceptsSnippetsInCompletions() {
        var item = capabilities.getTextDocument().getCompletion().getCompletionItem();

        assertThat(item.getSnippetSupport()).isTrue();
    }
}
