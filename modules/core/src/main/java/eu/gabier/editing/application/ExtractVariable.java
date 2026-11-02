package eu.gabier.editing.application;

import eu.gabier.editing.domain.ChangeApplier;
import eu.gabier.editing.domain.ChangeReport;
import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.FileStore;
import eu.gabier.editing.domain.JavaLanguageServer;
import eu.gabier.editing.domain.Range;
import eu.gabier.shared.error.domain.Assert;
import java.nio.file.Path;

/**
 * Extracts an expression into a local variable: asks the language server what that changes, and applies the answer to
 * the file all or nothing, or only reports it. Whatever refuses, nothing has been written: the server (not ready, file
 * outside the project, a selection that is not an expression, a name that is not valid), or the application of the
 * change set.
 */
public final class ExtractVariable {

    private final JavaLanguageServer server;
    private final ChangeApplier applier;

    public ExtractVariable(JavaLanguageServer server, FileStore store) {
        Assert.notNull("server", server);
        Assert.notNull("store", store);

        this.server = server;
        this.applier = new ChangeApplier(store);
    }

    /**
     * Extracts the expression of the selection into a variable of the given name and writes the file.
     *
     * @param selection the expression, from its first character to the end of its last (see {@code Position}: 0-based
     *     line and column)
     * @return what was done
     * @throws eu.gabier.editing.domain.LanguageServerException if the server declines
     * @throws eu.gabier.editing.domain.EditingException if the change set of the server cannot be applied
     */
    public ChangeReport apply(Path file, Range selection, String name) {
        return applier.apply(changes(file, selection, name));
    }

    /**
     * What the extraction would do, without writing anything.
     *
     * @return the file that would change, and the unified diff
     * @throws eu.gabier.editing.domain.LanguageServerException if the server declines
     * @throws eu.gabier.editing.domain.EditingException if the change set of the server cannot be applied
     */
    public ChangeReport dryRun(Path file, Range selection, String name) {
        return applier.dryRun(changes(file, selection, name));
    }

    private ChangeSet changes(Path file, Range selection, String name) {
        Assert.notNull("file", file);
        Assert.notNull("selection", selection);
        Assert.notNull("name", name);

        return server.extractVariable(file, selection, name);
    }
}
