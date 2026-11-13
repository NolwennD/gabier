package eu.gabier.daemon;

import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.CleanUp;
import eu.gabier.editing.domain.Diagnostic.Severity;
import eu.gabier.editing.domain.FileChange;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.SourceText;
import eu.gabier.editing.domain.TextEdit;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

/**
 * The imports and the clean-ups of {@link JdtlsLanguageServer}: the organization of the imports of a file, and the
 * clean-ups of jdtls, which end the same way (one edit of the file, judged before anything is written). The facade
 * holds the lock.
 */
final class JdtlsImports {

    private final JdtlsLanguageServer server;

    JdtlsImports(JdtlsLanguageServer server) {
        this.server = server;
    }

    /**
     * Asks jdtls for the {@code source.organizeImports} code action of the file, resolves it when its edit is not given
     * (jdtls computes it at that point, the client declares {@code codeAction.resolveSupport} for {@code edit}), and
     * gives the edit in the terms of the domain ({@link WorkspaceEdits}). jdtls reads the file as it knows it (nothing is
     * opened), after it has been told of the changes on disk. It has no edit for a file whose imports are as the
     * preferences of the project want them (the settings of {@link JdtlsSettings#imports}, given before the request: see
     * {@link JdtlsLanguageServer#followTheImportPreferences}): that is not a refusal, the answer is none.
     *
     * <p>jdtls does not always judge the imports, and gabier does not write what jdtls could not judge. A type that
     * several packages declare and that the file uses without importing it is left out by jdtls, which asks the client
     * to choose ({@code java.action.organizeImports.chooseImports}, enabled by {@link JdtlsCapabilities#extended()}):
     * the request is refused with the candidates. An import that jdtls removes, that the file still uses and that
     * jdtls reports an error on (it cannot resolve it) is refused too, with the import. The edit is judged before
     * anything is written, and the diagnostics are asked only when a used import is removed: an unused import is
     * removed at once.
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project, a type has several
     *     candidates or an import cannot be resolved
     * @throws JdtlsException if jdtls dies or does not answer in time
     */
    Optional<ChangeSet> organizeImports(Path file) {
        JdtlsSession session = server.ready();
        Path path = JdtlsProject.normalized(file);
        String original = JdtlsProject.textInProject(session, path);
        var params = new CodeActionParams(
                new TextDocumentIdentifier(path.toUri().toString()),
                new org.eclipse.lsp4j.Range(new org.eclipse.lsp4j.Position(0, 0), new org.eclipse.lsp4j.Position(0, 0)),
                new CodeActionContext(List.of(), List.of(CodeActionKind.SourceOrganizeImports)));
        var service = session.server().getTextDocumentService();
        JdtlsWatch watch = session.newWatch();
        try {
            server.prepare(session, watch);
            session.importChoices().clear();
            var offered = watch.await(
                    "the organization of the imports of " + path,
                    () -> JdtlsRequests.unlessDeclined(service.codeAction(params)));
            Optional<CodeAction> action = offered.stream()
                    .flatMap(List::stream)
                    .filter(Either::isRight)
                    .map(Either::getRight)
                    .findFirst();
            if (action.isPresent() && action.get().getEdit() == null) {
                var unresolved = action.get();
                action = watch.await(
                        "the edit of the organization of the imports of " + path,
                        () -> JdtlsRequests.unlessDeclined(service.resolveCodeAction(unresolved)));
            }
            refuseTheTypesJdtlsCannotChoose(session.importChoices(), path);
            List<FileChange> changes = action.map(CodeAction::getEdit)
                    .map(edit -> WorkspaceEdits.of(edit, JdtlsProject::textOnDisk))
                    .orElse(List.of());
            if (changes.isEmpty()) {
                return Optional.empty();
            }
            refuseTheImportsJdtlsCannotResolve(path, original, changes);

            return Optional.of(new ChangeSet(changes));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException(
                    "Interrupted while waiting for the organization of the imports of " + path, interrupted);
        }
    }

    /**
     * Applies the clean-ups to the file with {@code java/cleanup}, which runs the identifiers of the setting {@code
     * java.cleanup.actions} in the order of the list: the adapter sets it to the identifiers asked for ({@link
     * JdtlsSettings#withCleanUps}), and back to none once jdtls has answered, whatever the answer. jdtls reads the file
     * as it knows it (nothing is opened), after it has been told of the changes on disk, and answers one edit that
     * covers the whole text, or none when no clean-up changes it: that is not a refusal, the answer is none. It does not
     * refuse an identifier it does not know (it logs it), which is why {@link CleanUp} decides before the adapter is
     * asked.
     *
     * @throws LanguageServerException if jdtls is not ready or the file is not in its project (outside it, missing or a
     *     folder)
     * @throws JdtlsException if jdtls dies or does not answer in time
     */
    Optional<ChangeSet> cleanUp(Path file, List<CleanUp> cleanUps) {
        JdtlsSession session = server.ready();
        Path path = JdtlsProject.normalized(file);
        JdtlsProject.textInProject(session, path);
        var identifier = new TextDocumentIdentifier(path.toUri().toString());
        JdtlsWatch watch = session.newWatch();
        try {
            server.prepare(session, watch);
            session.configure(JdtlsSettings.withCleanUps(
                    cleanUps.stream().map(CleanUp::id).toList()));
            Optional<WorkspaceEdit> answer;
            try {
                answer = watch.await(
                        "the clean-ups of " + path,
                        () -> JdtlsRequests.unlessDeclined(session.server().cleanup(identifier)));
            } finally {
                session.configure(JdtlsSettings.withCleanUps(List.of()));
            }
            List<FileChange> changes = answer.map(edit -> WorkspaceEdits.of(edit, JdtlsProject::textOnDisk))
                    .orElse(List.of());

            return changes.isEmpty() ? Optional.empty() : Optional.of(new ChangeSet(changes));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException("Interrupted while waiting for the clean-ups of " + path, interrupted);
        }
    }

    /**
     * jdtls leaves a type that several packages declare unimported, and asks the client to choose (see {@link
     * JdtlsClient#executeClientCommand}); gabier does not choose for the agent, so it refuses and names the candidates.
     */
    private static void refuseTheTypesJdtlsCannotChoose(ImportChoices choices, Path path) {
        List<String> candidates = choices.offered();
        if (!candidates.isEmpty()) {
            throw LanguageServerException.ambiguousImports(
                    path,
                    candidates.stream()
                            .sorted(Comparator.comparing(JdtlsImports::simpleName)
                                    .thenComparing(Comparator.naturalOrder()))
                            .toList());
        }
    }

    private static String simpleName(String qualified) {
        return qualified.substring(qualified.lastIndexOf('.') + 1);
    }

    /**
     * jdtls deletes the imports it cannot resolve along with the ones nothing uses: for a file whose imports it does not
     * see (a source set it has no classpath for: "The package p is not accessible") that is every import, used or not.
     * An import that the edit removes, that the file still uses and that jdtls reports an error on is one it cannot
     * judge: the request is refused, names it, and nothing is written. Only then can code that is used be lost, so
     * the diagnostics, which cost a wait for jdtls to be quiet, are asked only for the removed imports whose simple name
     * still appears in the file outside the import lines ({@link #stillUsed}); an import nothing uses is removed
     * as the edit says, even when it cannot be resolved (the class was deleted).
     */
    private void refuseTheImportsJdtlsCannotResolve(Path path, String original, List<FileChange> changes) {
        List<String> before = original.lines().toList();
        String after = SourceText.edited(path, original, editsOnTheFile(path, changes));
        List<String> kept = after.lines().toList();
        String code =
                before.stream().filter(line -> !line.startsWith("import ")).collect(Collectors.joining("\n"));
        Map<Integer, String> atRisk = new LinkedHashMap<>();
        for (int line = 0; line < before.size(); line++) {
            String text = before.get(line);
            if (text.startsWith("import ") && !kept.contains(text) && stillUsed(importedName(text), code)) {
                atRisk.put(line, importedName(text));
            }
        }
        if (atRisk.isEmpty()) {
            return;
        }
        List<String> unresolved = server.diagnostics(path).stream()
                .filter(found -> found.severity() == Severity.ERROR)
                .map(found -> atRisk.get(found.position().line()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!unresolved.isEmpty()) {
            throw LanguageServerException.unresolvedImports(path, unresolved);
        }
    }

    /**
     * Whether the simple name of an imported name ({@code Set} of {@code java.util.Set}, {@code entry} of {@code
     * java.util.Map.entry}) is a word of the code. A star import has no name to look for, so it counts as used: what it
     * brought in cannot be told.
     */
    private static boolean stillUsed(String imported, String code) {
        String name = imported.substring(imported.lastIndexOf('.') + 1);

        return name.equals("*")
                || Pattern.compile("(?<![\\w$])" + Pattern.quote(name) + "(?![\\w$])")
                        .matcher(code)
                        .find();
    }

    /** {@code import static a.B.c;} is {@code a.B.c}. */
    private static String importedName(String declaration) {
        return declaration
                .substring("import ".length(), declaration.lastIndexOf(';'))
                .replaceFirst("^static\\s+", "")
                .strip();
    }

    private static List<TextEdit> editsOnTheFile(Path path, List<FileChange> changes) {
        return changes.stream()
                .filter(FileChange.EditFile.class::isInstance)
                .map(FileChange.EditFile.class::cast)
                .filter(edits -> edits.file().equals(path))
                .flatMap(edits -> edits.edits().stream())
                .toList();
    }
}
