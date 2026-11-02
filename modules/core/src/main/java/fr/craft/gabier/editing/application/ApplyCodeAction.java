package fr.craft.gabier.editing.application;

import fr.craft.gabier.editing.domain.ChangeApplier;
import fr.craft.gabier.editing.domain.ChangeReport;
import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.FileStore;
import fr.craft.gabier.editing.domain.JavaLanguageServer;
import fr.craft.gabier.editing.domain.OfferedAction;
import fr.craft.gabier.editing.domain.Range;
import fr.craft.gabier.shared.error.domain.Assert;
import java.nio.file.Path;

/**
 * Applies a code action of the language server, chosen by its title among the ones it offers on a range: asks what the
 * range offers, finds the action, asks the server what it changes, and applies the answer to the project all or
 * nothing, or only reports it. Whatever refuses, nothing has been written: the title (none offers it, or several do),
 * the server (not ready, file outside the project, an action that is a command for an editor and gives no edit), or the
 * application of the change set (a file that changed since the server answered).
 */
public final class ApplyCodeAction {

    private final JavaLanguageServer server;
    private final ChangeApplier applier;

    public ApplyCodeAction(JavaLanguageServer server, FileStore store) {
        Assert.notNull("server", server);
        Assert.notNull("store", store);

        this.server = server;
        this.applier = new ChangeApplier(store);
    }

    /**
     * Applies the action titled so on the range, and writes the files.
     *
     * @param range from the first character to the end of the last, the end excluded; a point is an empty range
     * @param title the title the server gives the action
     * @return what was done
     * @throws fr.craft.gabier.editing.domain.LanguageServerException if the server declines
     * @throws fr.craft.gabier.editing.domain.EditingException if no action, or several, have the title, or the change
     *     set of the server cannot be applied
     */
    public ChangeReport apply(Path file, Range range, String title) {
        return applier.apply(changes(file, range, title));
    }

    /**
     * What the action would do, without writing anything.
     *
     * @return the files that would be changed, and the unified diff
     * @throws fr.craft.gabier.editing.domain.LanguageServerException if the server declines
     * @throws fr.craft.gabier.editing.domain.EditingException if no action, or several, have the title, or the change
     *     set of the server cannot be applied
     */
    public ChangeReport dryRun(Path file, Range range, String title) {
        return applier.dryRun(changes(file, range, title));
    }

    private ChangeSet changes(Path file, Range range, String title) {
        Assert.notNull("file", file);
        Assert.notNull("range", range);
        Assert.notBlank("title", title);

        OfferedAction action = server.codeActions(file, range).titled(title);

        return server.codeActionEdit(file, range, action);
    }
}
