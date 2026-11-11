package eu.gabier.editing.application;

import eu.gabier.editing.domain.ChangeApplier;
import eu.gabier.editing.domain.ChangeReport;
import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.FileStore;
import eu.gabier.editing.domain.JavaLanguageServer;
import eu.gabier.editing.domain.PackageInfoFile;
import eu.gabier.shared.error.domain.Assert;
import java.nio.file.Path;

/**
 * Creates the {@code package-info.java} of a package: asks the language server for the text of the file (the {@code
 * package} line comes from the folder, with the annotation that marks the package null-safe), and applies the answer
 * to the project all or nothing, or only reports it. Whatever refuses, nothing has been written: the name of the file
 * (it must be {@code package-info.java}), the server (not ready, file outside the project or outside the source
 * folders), or the application of the change set (a file that exists, a file where a folder would go).
 */
public final class CreatePackageInfo {

    private final JavaLanguageServer server;
    private final ChangeApplier applier;

    public CreatePackageInfo(JavaLanguageServer server, FileStore store) {
        Assert.notNull("server", server);
        Assert.notNull("store", store);

        this.server = server;
        this.applier = new ChangeApplier(store);
    }

    /**
     * Creates the file and writes it.
     *
     * @param file a path that does not exist, named {@code package-info.java}, in a folder of a source folder of the
     *     project (the folders are made if they do not exist)
     * @return what was done
     * @throws eu.gabier.editing.domain.EditingException if the file is not named {@code package-info.java}, exists,
     *     or has a file where a folder would go
     * @throws eu.gabier.editing.domain.LanguageServerException if the server declines
     */
    public ChangeReport apply(Path file) {
        return applier.apply(changes(file));
    }

    /**
     * What the creation would do, without writing anything.
     *
     * @return the file that would be created, and the unified diff
     * @throws eu.gabier.editing.domain.EditingException if the file is not named {@code package-info.java}, exists,
     *     or has a file where a folder would go
     * @throws eu.gabier.editing.domain.LanguageServerException if the server declines
     */
    public ChangeReport dryRun(Path file) {
        return applier.dryRun(changes(file));
    }

    private ChangeSet changes(Path file) {
        return server.createPackageInfo(PackageInfoFile.of(file).path());
    }
}
