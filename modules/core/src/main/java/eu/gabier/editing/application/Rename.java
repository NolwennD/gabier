package eu.gabier.editing.application;

import eu.gabier.editing.application.RenameTarget.At;
import eu.gabier.editing.application.RenameTarget.Token;
import eu.gabier.editing.domain.ChangeApplier;
import eu.gabier.editing.domain.ChangeReport;
import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.FileStore;
import eu.gabier.editing.domain.JavaLanguageServer;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.TokenLocator;
import eu.gabier.shared.error.domain.Assert;

/**
 * Renames a symbol: finds it, asks the language server what the rename changes, and applies the answer to the files all
 * or nothing, or only reports it. Whatever refuses, nothing has been written: the server (not ready, no symbol, invalid
 * name, file outside the project), the lookup of an identifier, or the application of the change set.
 */
public final class Rename {

    private final JavaLanguageServer server;
    private final TokenLocator locator;
    private final ChangeApplier applier;

    public Rename(JavaLanguageServer server, FileStore store) {
        Assert.notNull("server", server);
        Assert.notNull("store", store);

        this.server = server;
        this.locator = new TokenLocator(store);
        this.applier = new ChangeApplier(store);
    }

    /**
     * Renames the symbol and writes the files.
     *
     * @return what was done
     * @throws eu.gabier.editing.domain.LanguageServerException if the server declines
     * @throws eu.gabier.editing.domain.EditingException if the identifier is not found, or the change set of the
     *     server cannot be applied
     */
    public ChangeReport apply(RenameTarget target, String newName) {
        return applier.apply(changes(target, newName));
    }

    /**
     * What renaming the symbol would do, without writing anything.
     *
     * @return the files that would change, and the unified diff
     * @throws eu.gabier.editing.domain.LanguageServerException if the server declines
     * @throws eu.gabier.editing.domain.EditingException if the identifier is not found, or the change set of the
     *     server cannot be applied
     */
    public ChangeReport dryRun(RenameTarget target, String newName) {
        return applier.dryRun(changes(target, newName));
    }

    private ChangeSet changes(RenameTarget target, String newName) {
        Assert.notNull("target", target);
        Assert.notNull("newName", newName);

        Position position = switch (target) {
            case At at -> at.position();
            case Token token -> locator.firstOccurrence(token.file(), token.identifier());
        };

        return server.rename(target.file(), position, newName);
    }
}
