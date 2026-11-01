package fr.craft.gabier.editing.application;

import fr.craft.gabier.editing.domain.ChangeApplier;
import fr.craft.gabier.editing.domain.ChangeReport;
import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.FileStore;
import fr.craft.gabier.editing.domain.JavaLanguageServer;
import fr.craft.gabier.editing.domain.PackageName;
import fr.craft.gabier.shared.error.domain.Assert;
import java.nio.file.Path;
import java.util.List;

/**
 * Moves Java sources to a package: asks the language server what that changes, and applies the answer to the project
 * all or nothing, or only reports it. The folder of a package that does not exist is made by applying the answer.
 * Whatever refuses, nothing has been written and no folder has been made: the server (not ready, a file outside the
 * project, a file that cannot go to the package), or the application of the change set (a file appeared in the
 * package since the server answered).
 */
public final class MoveToPackage {

    private final JavaLanguageServer server;
    private final ChangeApplier applier;

    public MoveToPackage(JavaLanguageServer server, FileStore store) {
        Assert.notNull("server", server);
        Assert.notNull("store", store);

        this.server = server;
        this.applier = new ChangeApplier(store);
    }

    /**
     * Moves the files to the package, and writes the files.
     *
     * @return what was done
     * @throws fr.craft.gabier.editing.domain.LanguageServerException if the server declines
     * @throws fr.craft.gabier.editing.domain.EditingException if the change set of the server cannot be applied
     */
    public ChangeReport apply(List<Path> files, PackageName target) {
        return applier.apply(changes(files, target));
    }

    /**
     * What the move would do, without writing anything.
     *
     * @return the files that would be moved and changed, and the unified diff
     * @throws fr.craft.gabier.editing.domain.LanguageServerException if the server declines
     * @throws fr.craft.gabier.editing.domain.EditingException if the change set of the server cannot be applied
     */
    public ChangeReport dryRun(List<Path> files, PackageName target) {
        return applier.dryRun(changes(files, target));
    }

    private ChangeSet changes(List<Path> files, PackageName target) {
        Assert.field("files", files).notNull().notEmpty().noNullElement();
        Assert.notNull("target", target);

        return server.moveToPackage(files, target);
    }
}
