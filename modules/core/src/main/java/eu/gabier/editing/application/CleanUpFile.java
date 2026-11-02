package eu.gabier.editing.application;

import eu.gabier.editing.domain.ChangeApplier;
import eu.gabier.editing.domain.ChangeReport;
import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.CleanUp;
import eu.gabier.editing.domain.FileStore;
import eu.gabier.editing.domain.JavaLanguageServer;
import eu.gabier.shared.error.domain.Assert;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Applies clean-ups of the language server to a file: checks the identifiers (an unknown one is refused before the
 * server is asked), asks the server what that changes, and applies the answer to the file all or nothing, or only
 * reports it. A file with nothing to clean up is a report of no change, not a refusal. Whatever refuses, nothing has
 * been written: the identifiers, the server (not ready, file outside the project) or the application of the change set.
 */
public final class CleanUpFile {

    private static final ChangeReport NOTHING_TO_CHANGE =
            new ChangeReport(List.of(), List.of(), List.of(), List.of(), "");

    private final JavaLanguageServer server;
    private final ChangeApplier applier;

    public CleanUpFile(JavaLanguageServer server, FileStore store) {
        Assert.notNull("server", server);
        Assert.notNull("store", store);

        this.server = server;
        this.applier = new ChangeApplier(store);
    }

    /**
     * Applies the clean-ups to the file, in the order given, and writes it.
     *
     * @param ids the identifiers of the clean-ups, at least one
     * @return what was done
     * @throws eu.gabier.editing.domain.EditingException if an identifier is not a clean-up, or the change set of
     *     the server cannot be applied
     * @throws eu.gabier.editing.domain.LanguageServerException if the server declines
     */
    public ChangeReport apply(Path file, List<String> ids) {
        return changes(file, ids).map(applier::apply).orElse(NOTHING_TO_CHANGE);
    }

    /**
     * What the clean-ups would do to the file, without writing anything.
     *
     * @param ids the identifiers of the clean-ups, at least one
     * @return the file that would change, and the unified diff
     * @throws eu.gabier.editing.domain.EditingException if an identifier is not a clean-up, or the change set of
     *     the server cannot be applied
     * @throws eu.gabier.editing.domain.LanguageServerException if the server declines
     */
    public ChangeReport dryRun(Path file, List<String> ids) {
        return changes(file, ids).map(applier::dryRun).orElse(NOTHING_TO_CHANGE);
    }

    private Optional<ChangeSet> changes(Path file, List<String> ids) {
        Assert.notNull("file", file);
        Assert.field("ids", ids).notNull().notEmpty();

        return server.cleanUp(file, CleanUp.ofAll(ids));
    }
}
