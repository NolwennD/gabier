package fr.craft.gabier.editing.application;

import fr.craft.gabier.editing.domain.FileStore;
import fr.craft.gabier.editing.domain.JavaLanguageServer;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.editing.domain.ReferenceReader;
import fr.craft.gabier.shared.error.domain.Assert;
import java.nio.file.Path;

/**
 * Finds where a symbol is used: asks the language server for the places, then reads the line of source of each. It only
 * reads: whatever refuses (the server, or a file that changed since it answered), nothing has been written.
 */
public final class FindReferences {

    private final JavaLanguageServer server;
    private final ReferenceReader reader;

    public FindReferences(JavaLanguageServer server, FileStore store) {
        Assert.notNull("server", server);
        Assert.notNull("store", store);

        this.server = server;
        this.reader = new ReferenceReader(store);
    }

    /**
     * Finds the uses of the symbol at the position, and its declaration when asked.
     *
     * @param position on the name of a type, a method, a field or a variable, at its declaration or at a use (see
     *     {@code Position}: 0-based line and column)
     * @param includeDeclaration whether the declaration is listed, apart from the uses
     * @return the places, in the order of the files then of the positions; none for a symbol that nothing uses
     * @throws fr.craft.gabier.editing.domain.LanguageServerException if the server declines
     * @throws fr.craft.gabier.editing.domain.EditingException if a file of the answer is missing, cannot be read or
     *     has changed since the server answered
     */
    public SymbolReferences find(Path file, Position position, boolean includeDeclaration) {
        Assert.notNull("file", file);
        Assert.notNull("position", position);

        var locations = server.references(file, position, includeDeclaration);

        return new SymbolReferences(reader.read(locations.declarations()), reader.read(locations.references()));
    }
}
