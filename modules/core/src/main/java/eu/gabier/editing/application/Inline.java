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
 * Inlines a local variable, a method or a constant: asks the language server what that changes, and applies the answer
 * to the project all or nothing, or only reports it. Whatever refuses, nothing has been written: the server (not ready,
 * file outside the project, nothing to inline there), or the application of the change set (a file that changed since
 * the server answered).
 */
public final class Inline {

    private final JavaLanguageServer server;
    private final ChangeApplier applier;

    public Inline(JavaLanguageServer server, FileStore store) {
        Assert.notNull("server", server);
        Assert.notNull("store", store);

        this.server = server;
        this.applier = new ChangeApplier(store);
    }

    /**
     * Inlines the symbol at the position, and writes the files.
     *
     * @param position on the name of a local variable, a method or a constant, at its declaration or at a use (see
     *     {@code Position}: 0-based line and column)
     * @return what was done
     * @throws eu.gabier.editing.domain.LanguageServerException if the server declines
     * @throws eu.gabier.editing.domain.EditingException if the change set of the server cannot be applied
     */
    public ChangeReport apply(Path file, Position position) {
        return applier.apply(changes(file, position));
    }

    /**
     * What the inlining would do, without writing anything.
     *
     * @return the files that would be changed, and the unified diff
     * @throws eu.gabier.editing.domain.LanguageServerException if the server declines
     * @throws eu.gabier.editing.domain.EditingException if the change set of the server cannot be applied
     */
    public ChangeReport dryRun(Path file, Position position) {
        return applier.dryRun(changes(file, position));
    }

    private ChangeSet changes(Path file, Position position) {
        Assert.notNull("file", file);
        Assert.notNull("position", position);

        return server.inline(file, position);
    }
}
