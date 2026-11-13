package eu.gabier.daemon;

import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.FileChange;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.PackageName;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.Range;
import eu.gabier.editing.domain.TextEdit;
import eu.gabier.editing.domain.TypeKind;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;

/**
 * The creations of {@link JdtlsLanguageServer}: a type in a new file, and the {@code package-info.java} of a package.
 * The facade holds the lock and calls these with it held.
 */
final class JdtlsCreations {

    /** The annotation that marks a package null-safe, written in full so that a {@code package-info.java} needs no import. */
    private static final String NULL_MARKED = "@org.jspecify.annotations.NullMarked";

    private final JdtlsLanguageServer server;

    JdtlsCreations(JdtlsLanguageServer server) {
        this.server = server;
    }

    /**
     * Creates a type of the kind in a file that need not exist: the file is opened in jdtls with no text (as an editor
     * opens a new file, never written to disk), and the snippet of the kind that jdtls completes the empty text with
     * ({@code textDocument/completion} at 0:0, the client declaring {@code snippetSupport}, see {@link
     * JdtlsCapabilities}) is the text of the file: jdtls derives the {@code package} line from the folder of the file
     * in its source folder, writes the name of the file as the name of the type and, with the preferences of {@link
     * JdtlsSettings#javaPreferences}, no comment. {@link NewTypeText} turns the snippet into the text of a file.
     *
     * <p>jdtls offers the snippet whatever the file: for one that is under no source folder it has no package to
     * write, and gives the text of the default package. So the adapter asks which folders are source folders ({@code
     * java.project.listSourcePaths}) and refuses a file that is under none. A kind jdtls has no snippet for (a record,
     * before Java 14) is refused the same way, and jdtls does not say which of the two it is.
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project, the file is under no
     *     source folder or jdtls has no snippet of this kind
     * @throws JdtlsException if jdtls dies or does not answer in time
     */
    ChangeSet createType(Path file, TypeKind kind) {
        JdtlsSession session = server.ready();
        Path path = JdtlsProject.normalized(file);
        JdtlsProject.requireInProject(session, path);
        String what = "the snippet of a " + kind.keyword() + " for " + path;
        JdtlsWatch watch = session.newWatch();
        try {
            server.prepare(session, watch);
            if (JdtlsProject.sourceFolders(session, watch).stream().noneMatch(path::startsWith)) {
                throw LanguageServerException.notCreatable(path, kind);
            }
            String snippet = snippetOfKind(session, watch, path, kind, what)
                    .orElseThrow(() -> LanguageServerException.notCreatable(path, kind));
            var start = new Position(0, 0);
            var text = new TextEdit(new Range(start, start), NewTypeText.of(snippet));

            return new ChangeSet(
                    List.of(new FileChange.CreateFile(path), new FileChange.EditFile(path, List.of(text))));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException("Interrupted while waiting for " + what, interrupted);
        }
    }

    /**
     * Creates the {@code package-info.java} of a package, which jdtls offers no snippet for ({@code
     * textDocument/completion} at the start of an empty {@code package-info.java} proposes the keyword {@code package}
     * only, with no text to insert). So the adapter writes the text itself, as a light workaround of that gap of
     * jdtls: the package is the folder of the file under its source folder
     * ({@code java.project.listSourcePaths}, the innermost one when they nest), the same derivation jdtls makes for a
     * type, and the text is the annotation {@code @org.jspecify.annotations.NullMarked}, written in full so that the
     * file has no import, the {@code package} line, and nothing else. A folder whose name is not an identifier is
     * refused as the name of a package, and nothing is written.
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project, or it is under no source
     *     folder or straight under one
     * @throws eu.gabier.editing.domain.EditingException if a folder of the package is not an identifier
     * @throws JdtlsException if jdtls dies or does not answer in time
     */
    ChangeSet createPackageInfo(Path file) {
        JdtlsSession session = server.ready();
        Path path = JdtlsProject.normalized(file);
        JdtlsProject.requireInProject(session, path);
        JdtlsWatch watch = session.newWatch();
        try {
            server.takeInChangesOnDisk(session, watch);
            PackageName name = packageOfFolder(path, JdtlsProject.sourceFolders(session, watch));
            var start = new Position(0, 0);
            var text = new TextEdit(new Range(start, start), NULL_MARKED + "\npackage " + name.value() + ";\n");

            return new ChangeSet(
                    List.of(new FileChange.CreateFile(path), new FileChange.EditFile(path, List.of(text))));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException("Interrupted while waiting for the source folders for " + path, interrupted);
        }
    }

    /** The package of the folder of the file under the innermost source folder that holds it. */
    private static PackageName packageOfFolder(Path file, List<Path> sourceFolders) {
        Path folder = sourceFolders.stream()
                .filter(file::startsWith)
                .max(Comparator.comparingInt(Path::getNameCount))
                .orElseThrow(() -> LanguageServerException.notCreatablePackageInfo(file));
        Path inside = folder.relativize(file.getParent());
        if (inside.toString().isEmpty()) {
            throw LanguageServerException.notCreatablePackageInfo(file);
        }

        return PackageName.of(inside.toString().replace(inside.getFileSystem().getSeparator(), "."));
    }

    /** The snippet of the kind that jdtls completes the empty text of the file with; the file is closed afterwards. */
    private Optional<String> snippetOfKind(
            JdtlsSession session, JdtlsWatch watch, Path path, TypeKind kind, String what) throws InterruptedException {
        var uri = path.toUri().toString();
        var service = session.server().getTextDocumentService();
        var params = new CompletionParams(new TextDocumentIdentifier(uri), new org.eclipse.lsp4j.Position(0, 0));
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "java", 1, "")));
        try {
            return watch
                    .await(what, () -> JdtlsRequests.unlessDeclined(service.completion(params)))
                    .map(completion -> completion.isLeft()
                            ? completion.getLeft()
                            : completion.getRight().getItems())
                    .stream()
                    .flatMap(List::stream)
                    .filter(item -> item.getKind() == CompletionItemKind.Snippet
                            && kind.keyword().equals(item.getLabel()))
                    .map(CompletionItem::getInsertText)
                    .filter(Objects::nonNull)
                    .findFirst();
        } finally {
            JdtlsLanguageServer.close(session, List.of(path));
        }
    }
}
