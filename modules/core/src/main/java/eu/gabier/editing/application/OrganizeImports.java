package eu.gabier.editing.application;

import eu.gabier.editing.domain.ChangeApplier;
import eu.gabier.editing.domain.ChangeReport;
import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.FileStore;
import eu.gabier.editing.domain.JavaLanguageServer;
import eu.gabier.shared.error.domain.Assert;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Organizes the imports of a file: asks the language server what that changes, and applies the answer to the file all
 * or nothing, or only reports it. A file whose imports are already as they should be is a report of no change, not a
 * refusal. Whatever refuses, nothing has been written: the server (not ready, file outside the project) or the
 * application of the change set.
 */
public final class OrganizeImports {

    private static final ChangeReport NOTHING_TO_CHANGE =
            new ChangeReport(List.of(), List.of(), List.of(), List.of(), "");

    private final JavaLanguageServer server;
    private final ChangeApplier applier;

    public OrganizeImports(JavaLanguageServer server, FileStore store) {
        Assert.notNull("server", server);
        Assert.notNull("store", store);

        this.server = server;
        this.applier = new ChangeApplier(store);
    }

    /**
     * Organizes the imports of the file and writes it.
     *
     * @return what was done
     * @throws eu.gabier.editing.domain.LanguageServerException if the server declines
     * @throws eu.gabier.editing.domain.EditingException if the change set of the server cannot be applied
     */
    public ChangeReport apply(Path file) {
        return changes(file).map(applier::apply).orElse(NOTHING_TO_CHANGE);
    }

    /**
     * What organizing the imports of the file would do, without writing anything.
     *
     * @return the file that would change, and the unified diff
     * @throws eu.gabier.editing.domain.LanguageServerException if the server declines
     * @throws eu.gabier.editing.domain.EditingException if the change set of the server cannot be applied
     */
    public ChangeReport dryRun(Path file) {
        return changes(file).map(applier::dryRun).orElse(NOTHING_TO_CHANGE);
    }

    private Optional<ChangeSet> changes(Path file) {
        Assert.notNull("file", file);

        return server.organizeImports(file);
    }
}
