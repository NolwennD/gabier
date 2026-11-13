package eu.gabier.daemon;

import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.FileChange;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.OfferedAction;
import eu.gabier.editing.domain.OfferedActions;
import eu.gabier.editing.domain.Range;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

/** The code actions of {@link JdtlsLanguageServer}: what jdtls offers on a range, and the edit of one. The facade holds the lock. */
final class JdtlsCodeActions {

    private final JdtlsLanguageServer server;

    JdtlsCodeActions(JdtlsLanguageServer server) {
        this.server = server;
    }

    /**
     * The code actions of the range, with {@code textDocument/codeAction}. jdtls computes the fixes of a problem only for
     * the diagnostics the request carries (without them it offers none: {@code CodeActionHandler} reads the problems
     * from {@code context.diagnostics}), so the adapter opens the file, takes the diagnostics jdtls publishes for it as
     * {@link JdtlsLanguageServer#diagnostics} does, and sends those that touch the range. The {@code only} of the request names the
     * processors jdtls runs: {@code quickfix}, {@code refactor} and {@code quickassist}, which is jdtls's own kind for
     * the rewrites of a piece of code (a concatenation to a text block) and which it gives only to a client that
     * declares it ({@link JdtlsCapabilities#QUICK_ASSIST}); the {@code source} actions, which are about a whole file
     * (organize the imports, generate accessors) and are offered whatever the range, are left out, so that a range with
     * nothing to offer has an empty answer. A bare command, which a client without literal support is answered, is not
     * an action and is not listed.
     *
     * @throws LanguageServerException if jdtls is not ready or the file is not in its project
     * @throws JdtlsException if jdtls dies or does not answer in time
     */
    OfferedActions codeActions(Path file, Range range) {
        return new OfferedActions(offered(file, range).stream()
                .map(offer -> new OfferedAction(kindOf(offer), offer.getTitle()))
                .toList());
    }

    /**
     * The edit of the action, taken from the actions jdtls offers on the range now (the one of the same kind and title)
     * and resolved ({@code codeAction/resolve}) when jdtls does not give it with the action: the client declares {@code
     * codeAction.resolveSupport} for {@code edit}. An action that jdtls no longer offers, one that has no edit once
     * resolved (a command for the editor: {@code java.action.applyRefactoringCommand} for "Change signature", which
     * opens a dialog) and one whose edit changes nothing are one refusal, {@code not-applicable}.
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project, or there is no edit for
     *     the action
     * @throws JdtlsException if jdtls dies or does not answer in time
     */
    ChangeSet codeActionEdit(Path file, Range range, OfferedAction action) {
        Path path = JdtlsProject.normalized(file);
        Optional<CodeAction> found = offered(file, range).stream()
                .filter(offer ->
                        kindOf(offer).equals(action.kind()) && offer.getTitle().equals(action.title()))
                .findFirst();
        JdtlsSession session = server.ready();
        var service = session.server().getTextDocumentService();
        JdtlsWatch watch = session.newWatch();
        try {
            if (found.isPresent() && found.get().getEdit() == null) {
                var unresolved = found.get();
                found = watch.await(
                        "the edit of the action \"" + action.title() + "\" on " + range + " of " + path,
                        () -> JdtlsRequests.unlessDeclined(service.resolveCodeAction(unresolved)));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException(
                    "Interrupted while waiting for the edit of the action \"" + action.title() + "\" on " + range
                            + " of " + path,
                    interrupted);
        }
        List<FileChange> changes = found.map(CodeAction::getEdit)
                .map(edit -> WorkspaceEdits.of(edit, JdtlsProject::textOnDisk))
                .orElse(List.of());
        if (changes.isEmpty()) {
            throw LanguageServerException.notApplicable(path, range, action.title());
        }

        return new ChangeSet(changes);
    }

    private List<CodeAction> offered(Path file, Range range) {
        Path path = JdtlsProject.normalized(file);
        var asked = JdtlsPositions.lsp(range);
        List<org.eclipse.lsp4j.Diagnostic> problems = server.published(List.of(path)).getFirst().stream()
                .filter(problem -> touches(problem.getRange(), asked))
                .toList();
        JdtlsSession session = server.ready();
        var context = new CodeActionContext(
                problems, List.of(CodeActionKind.QuickFix, CodeActionKind.Refactor, JdtlsCapabilities.QUICK_ASSIST));
        var params =
                new CodeActionParams(new TextDocumentIdentifier(path.toUri().toString()), asked, context);
        var service = session.server().getTextDocumentService();
        JdtlsWatch watch = session.newWatch();
        try {
            server.prepare(session, watch);
            var offered = watch.await(
                    "the code actions on " + range + " of " + path,
                    () -> JdtlsRequests.unlessDeclined(service.codeAction(params)));
            return offered.stream()
                    .flatMap(List::stream)
                    .filter(Either::isRight)
                    .map(Either::getRight)
                    .toList();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException(
                    "Interrupted while waiting for the code actions on " + range + " of " + path, interrupted);
        }
    }

    /** Whether the problem is on the range or touches it: a point inside a problem is on it. */
    private static boolean touches(org.eclipse.lsp4j.Range problem, org.eclipse.lsp4j.Range range) {
        return JdtlsPositions.compare(problem.getStart(), range.getEnd()) <= 0
                && JdtlsPositions.compare(range.getStart(), problem.getEnd()) <= 0;
    }

    /** The kind of an action, empty when the server gave none: the protocol makes it optional. */
    private static String kindOf(CodeAction offer) {
        return Objects.requireNonNullElse(offer.getKind(), "");
    }
}
