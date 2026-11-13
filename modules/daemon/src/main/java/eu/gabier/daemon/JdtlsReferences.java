package eu.gabier.daemon;

import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.Location;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.ReferenceLocations;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jspecify.annotations.Nullable;

/** The references of {@link JdtlsLanguageServer}: where a symbol is used. The facade holds the lock. */
final class JdtlsReferences {

    private final JdtlsLanguageServer server;

    JdtlsReferences(JdtlsLanguageServer server) {
        this.server = server;
    }

    /**
     * Where the symbol at the position is used, with {@code textDocument/references}. jdtls answers the references of
     * the type or the method that encloses a position where it finds no symbol (a blank, a comment: {@code
     * ReferencesHandler} falls back to the element at the offset), so the adapter asks {@code prepareRename} first,
     * as {@link JdtlsLanguageServer#rename} does, and takes what jdtls declines for no symbol. jdtls reads the files as it knows them
     * (nothing is opened), after it has been told of the changes on disk.
     *
     * <p>jdtls does not say which of the locations it gives with {@code includeDeclaration} is the declaration. So the
     * uses are asked for without it, and, when the declaration is wanted, asked for again with it: what the second
     * answer has more than the first is the declaration. A location that is not a file ({@code jdt:} for a class of
     * a library) is left out; jdtls gives none to a client that does not declare {@code classFileContentsSupport},
     * which gabier does not.
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project or there is no symbol that
     *     jdtls can rename at the position
     * @throws JdtlsException if jdtls dies or does not answer in time
     */
    ReferenceLocations references(Path file, Position position, boolean includeDeclaration) {
        JdtlsSession session = server.ready();
        Path path = JdtlsProject.normalized(file);
        JdtlsProject.textInProject(session, path);
        var document = new TextDocumentIdentifier(path.toUri().toString());
        var at = new org.eclipse.lsp4j.Position(position.line(), position.column());
        var service = session.server().getTextDocumentService();
        JdtlsWatch watch = session.newWatch();
        String what = "the references of the symbol at " + position + " of " + path;
        try {
            server.takeInChangesOnDisk(session, watch);
            var symbol = watch.await(
                    "whether there is a symbol at " + position + " of " + path,
                    () -> JdtlsRequests.unlessDeclined(service.prepareRename(new PrepareRenameParams(document, at))));
            if (symbol.isEmpty()) {
                throw LanguageServerException.noSuchSymbol(path, position);
            }
            List<Location> uses =
                    locations(watch.await(what, () -> service.references(references(document, at, false))));
            if (!includeDeclaration) {
                return new ReferenceLocations(List.of(), uses);
            }
            Set<Location> known = Set.copyOf(uses);
            List<Location> declarations = locations(watch.await(
                            what + " with the declaration", () -> service.references(references(document, at, true))))
                    .stream()
                    .filter(location -> !known.contains(location))
                    .toList();

            return new ReferenceLocations(declarations, uses);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException("Interrupted while waiting for " + what, interrupted);
        }
    }

    private static ReferenceParams references(
            TextDocumentIdentifier document, org.eclipse.lsp4j.Position at, boolean includeDeclaration) {
        return new ReferenceParams(document, at, new ReferenceContext(includeDeclaration));
    }

    /** The places of the answer that are files, in the terms of the domain; none for an answer of null. */
    private static List<Location> locations(@Nullable List<? extends org.eclipse.lsp4j.Location> answer) {
        if (answer == null) {
            return List.of();
        }

        return answer.stream()
                .filter(found -> found.getUri().startsWith("file:"))
                .map(found -> new Location(
                        Path.of(URI.create(found.getUri())),
                        new Position(
                                found.getRange().getStart().getLine(),
                                found.getRange().getStart().getCharacter())))
                .toList();
    }
}
