package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
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
    void letsJdtlsRegisterTheFilesItWantsWatchedSoThatItLearnsOfTheEditsMadeOnDisk() {
        // Without it jdtls registers no watcher: it reads a file once and never again.
        assertThat(capabilities.getWorkspace().getDidChangeWatchedFiles().getDynamicRegistration())
                .isTrue();
    }

    @Test
    void asksForTheRangeOfTheSymbolBeforeARename() {
        assertThat(capabilities.getTextDocument().getRename().getPrepareSupport())
                .isTrue();
    }

    @Test
    void asksForTheSemanticTokensOfAWholeFileSoThatJdtlsSaysWhichDeclarationsAreRecordComponents() {
        // Without it jdtls announces no provider of semantic tokens, and so no legend with the type recordComponent.
        var tokens = capabilities.getTextDocument().getSemanticTokens();

        assertThat(tokens.getRequests().getFull().getLeft()).isTrue();
        assertThat(tokens.getFormats()).containsExactly("relative");
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
    void declaresTheKindOfTheRewritesOfJdtlsWhichItDropsOtherwise() {
        var kinds = capabilities
                .getTextDocument()
                .getCodeAction()
                .getCodeActionLiteralSupport()
                .getCodeActionKind()
                .getValueSet();

        assertThat(kinds).contains("quickassist");
        assertThat(JdtlsCapabilities.QUICK_ASSIST).isEqualTo("quickassist");
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

    @Test
    void sayThatItCanBeAskedToChooseAnImportAndRunACommandOfJdtls() {
        // Without them jdtls leaves a type that several packages declare unimported, without a word.
        assertThat(JdtlsCapabilities.extended())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("advancedOrganizeImportsSupport", true, "executeClientCommandSupport", true));
    }
}
