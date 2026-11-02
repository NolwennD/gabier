package eu.gabier.editing.application;

import eu.gabier.editing.domain.JavaLanguageServer;
import eu.gabier.editing.domain.OfferedActions;
import eu.gabier.editing.domain.Range;
import eu.gabier.shared.error.domain.Assert;
import java.nio.file.Path;

/**
 * Lists the code actions the language server offers on a range of a file: the fixes of the problems the range holds and
 * the rewrites of the code it covers. Nothing is written.
 */
public final class ListCodeActions {

    private final JavaLanguageServer server;

    public ListCodeActions(JavaLanguageServer server) {
        Assert.notNull("server", server);

        this.server = server;
    }

    /**
     * What the server offers on the range.
     *
     * @param range from the first character to the end of the last, the end excluded; a point is an empty range
     * @return the actions in the server's order, none when it offers nothing there
     * @throws eu.gabier.editing.domain.LanguageServerException if the server declines (not ready, file outside
     *     the project)
     */
    public OfferedActions list(Path file, Range range) {
        Assert.notNull("file", file);
        Assert.notNull("range", range);

        return server.codeActions(file, range);
    }
}
