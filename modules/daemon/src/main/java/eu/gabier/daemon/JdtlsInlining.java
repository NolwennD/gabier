package eu.gabier.daemon;

import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.FileChange;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.Position;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

/** The inlining of {@link JdtlsLanguageServer}: a variable, a method or a constant replaced by its value. The facade holds the lock. */
final class JdtlsInlining {

    private final JdtlsLanguageServer server;

    JdtlsInlining(JdtlsLanguageServer server) {
        this.server = server;
    }

    /**
     * Inlines the local variable, the method or the constant at the position, with the code action {@code
     * refactor.inline} asked for an empty range at the position (jdtls offers it when the position is on the name of
     * what it can inline: {@code RefactorProcessor.getInlineProposal}). The {@code only} of the request chooses
     * the processors jdtls runs, not the actions it returns (the refactor processor answers all of its {@code refactor.*}
     * actions), so the action taken is the one of the kind {@code refactor.inline} that is titled "Inline ..." ("Inline
     * Method", "Inline Constant", "Inline local variable"): jdtls gives "Make static" the same kind.
     *
     * <p>The edit is computed when the action is resolved, as for the organization of the imports, and jdtls gives none
     * when the conditions of the refactoring fail: a variable assigned more than once, a method that is recursive.
     * Nothing is offered for a position on no variable, method or constant, an abstract method, or a declaration that
     * nothing uses; all of these are one refusal, {@code not-inlinable}, as jdtls does not say which. On a variable,
     * jdtls inlines every use and removes the declaration, whatever the position; on a method or a constant, a position
     * on the declaration inlines every use and removes it, a position on a use inlines that use only.
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project or jdtls has nothing to
     *     inline there
     * @throws JdtlsException if jdtls dies or does not answer in time
     */
    ChangeSet inline(Path file, Position position) {
        JdtlsSession session = server.ready();
        Path path = JdtlsProject.normalized(file);
        JdtlsProject.textInProject(session, path);
        var at = new org.eclipse.lsp4j.Position(position.line(), position.column());
        var params = new CodeActionParams(
                new TextDocumentIdentifier(path.toUri().toString()),
                new org.eclipse.lsp4j.Range(at, at),
                new CodeActionContext(List.of(), List.of(CodeActionKind.RefactorInline)));
        var service = session.server().getTextDocumentService();
        JdtlsWatch watch = session.newWatch();
        try {
            server.prepare(session, watch);
            var offered = watch.await(
                    "the inlining of the symbol at " + position + " of " + path,
                    () -> JdtlsRequests.unlessDeclined(service.codeAction(params)));
            Optional<CodeAction> action = offered.stream()
                    .flatMap(List::stream)
                    .filter(Either::isRight)
                    .map(Either::getRight)
                    .filter(JdtlsInlining::isAnInlining)
                    .findFirst();
            if (action.isPresent() && action.get().getEdit() == null) {
                var unresolved = action.get();
                action = watch.await(
                        "the edit of the inlining of the symbol at " + position + " of " + path,
                        () -> JdtlsRequests.unlessDeclined(service.resolveCodeAction(unresolved)));
            }
            List<FileChange> changes = action.map(CodeAction::getEdit)
                    .map(edit -> WorkspaceEdits.of(edit, JdtlsProject::textOnDisk))
                    .orElse(List.of());
            if (changes.isEmpty()) {
                throw LanguageServerException.notInlinable(path, position);
            }

            return new ChangeSet(changes);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException(
                    "Interrupted while waiting for the inlining of the symbol at " + position + " of " + path,
                    interrupted);
        }
    }

    private static boolean isAnInlining(CodeAction offer) {
        return CodeActionKind.RefactorInline.equals(offer.getKind())
                && offer.getTitle().startsWith("Inline ");
    }
}
