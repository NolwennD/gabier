package eu.gabier.editing.application;

import eu.gabier.editing.domain.ChangeApplier;
import eu.gabier.editing.domain.ChangeReport;
import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.FileStore;
import eu.gabier.editing.domain.JavaLanguageServer;
import eu.gabier.editing.domain.Position;
import eu.gabier.shared.error.domain.Assert;
import java.nio.file.Path;

/**
 * Makes a nested type a top-level type in a file of its own: asks the language server what that changes, and applies
 * the answer to the project all or nothing, or only reports it. Whatever refuses, nothing has been written: the server
 * (not ready, file outside the project, no nested type to move), or the application of the change set (the new file
 * exists).
 */
public final class MoveTypeToFile {

    private final JavaLanguageServer server;
    private final ChangeApplier applier;

    public MoveTypeToFile(JavaLanguageServer server, FileStore store) {
        Assert.notNull("server", server);
        Assert.notNull("store", store);

        this.server = server;
        this.applier = new ChangeApplier(store);
    }

    /**
     * Moves the type that encloses the position to a file of its own, and writes the files.
     *
     * @param position inside the type (see {@code Position}: 0-based line and column)
     * @return what was done
     * @throws eu.gabier.editing.domain.LanguageServerException if the server declines
     * @throws eu.gabier.editing.domain.EditingException if the change set of the server cannot be applied
     */
    public ChangeReport apply(Path file, Position position) {
        return applier.apply(changes(file, position));
    }

    /**
     * What the move would do, without writing anything.
     *
     * @return the files that would be created and changed, and the unified diff
     * @throws eu.gabier.editing.domain.LanguageServerException if the server declines
     * @throws eu.gabier.editing.domain.EditingException if the change set of the server cannot be applied
     */
    public ChangeReport dryRun(Path file, Position position) {
        return applier.dryRun(changes(file, position));
    }

    private ChangeSet changes(Path file, Position position) {
        Assert.notNull("file", file);
        Assert.notNull("position", position);

        return server.moveTypeToFile(file, position);
    }
}
