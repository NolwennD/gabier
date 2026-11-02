package eu.gabier.editing.application;

import eu.gabier.editing.domain.ChangeApplier;
import eu.gabier.editing.domain.ChangeReport;
import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.FileStore;
import eu.gabier.editing.domain.JavaLanguageServer;
import eu.gabier.editing.domain.TypeKind;
import eu.gabier.editing.domain.TypeName;
import eu.gabier.shared.error.domain.Assert;
import java.nio.file.Path;

/**
 * Creates a type in a new file: asks the language server for the text of the file (its {@code package} line comes from
 * the path), and applies the answer to the project all or nothing, or only reports it. Whatever refuses, nothing has
 * been written: the name of the file (it must name a type), the server (not ready, file outside the project), or the
 * application of the change set (a file that exists, a file where a folder would go).
 */
public final class CreateType {

    private final JavaLanguageServer server;
    private final ChangeApplier applier;

    public CreateType(JavaLanguageServer server, FileStore store) {
        Assert.notNull("server", server);
        Assert.notNull("store", store);

        this.server = server;
        this.applier = new ChangeApplier(store);
    }

    /**
     * Creates the file with an empty type of the kind, and writes it.
     *
     * @param file a path that does not exist, in a source folder of the project (its folders are made if they do not
     *     exist), named after the type with {@code .java}
     * @return what was done
     * @throws eu.gabier.editing.domain.EditingException if the file cannot hold a type, exists, or has a file where a folder would go
     * @throws eu.gabier.editing.domain.LanguageServerException if the server declines
     */
    public ChangeReport apply(Path file, TypeKind kind) {
        return applier.apply(changes(file, kind));
    }

    /**
     * What the creation would do, without writing anything.
     *
     * @return the file that would be created, and the unified diff
     * @throws eu.gabier.editing.domain.EditingException if the file cannot hold a type, exists, or has a file where a folder would go
     * @throws eu.gabier.editing.domain.LanguageServerException if the server declines
     */
    public ChangeReport dryRun(Path file, TypeKind kind) {
        return applier.dryRun(changes(file, kind));
    }

    private ChangeSet changes(Path file, TypeKind kind) {
        Assert.notNull("file", file);
        Assert.notNull("kind", kind);
        TypeName.of(file);

        return server.createType(file, kind);
    }
}
