package fr.craft.gabier.editing.application;

import fr.craft.gabier.editing.domain.ChangeApplier;
import fr.craft.gabier.editing.domain.ChangeReport;
import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.FileStore;
import fr.craft.gabier.editing.domain.JavaLanguageServer;
import fr.craft.gabier.editing.domain.TypeKind;
import fr.craft.gabier.editing.domain.TypeName;
import fr.craft.gabier.shared.error.domain.Assert;
import java.nio.file.Path;

/**
 * Creates a type in a new file: asks the language server for the text of the file (its {@code package} line comes from
 * the path), and applies the answer to the project all or nothing, or only reports it. Whatever refuses, nothing has
 * been written: the name of the file (it must name a type), the server (not ready, file outside the project), or the
 * application of the change set (a file that exists, a folder that does not).
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
     * @param file a path that does not exist, in an existing folder of a source folder of the project, named after the
     *     type with {@code .java}
     * @return what was done
     * @throws fr.craft.gabier.editing.domain.EditingException if the file cannot hold a type, exists, or has no folder
     * @throws fr.craft.gabier.editing.domain.LanguageServerException if the server declines
     */
    public ChangeReport apply(Path file, TypeKind kind) {
        return applier.apply(changes(file, kind));
    }

    /**
     * What the creation would do, without writing anything.
     *
     * @return the file that would be created, and the unified diff
     * @throws fr.craft.gabier.editing.domain.EditingException if the file cannot hold a type, exists, or has no folder
     * @throws fr.craft.gabier.editing.domain.LanguageServerException if the server declines
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
