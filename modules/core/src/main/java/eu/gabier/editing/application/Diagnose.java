package eu.gabier.editing.application;

import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.editing.domain.JavaLanguageServer;
import eu.gabier.shared.error.domain.Assert;
import java.nio.file.Path;
import java.util.List;

/** The problems of a source file, as the language server sees them. */
public final class Diagnose {

    private final JavaLanguageServer server;

    public Diagnose(JavaLanguageServer server) {
        Assert.notNull("server", server);

        this.server = server;
    }

    /**
     * Asks the language server for the diagnostics of files, in one request.
     *
     * @return one list of diagnostics per file, in the order given, none for a file that is clean
     * @throws eu.gabier.editing.domain.LanguageServerException if the server declines: it is not ready, or one of the
     *     files is not in its project (the whole call is refused)
     */
    public List<List<Diagnostic>> diagnose(List<Path> files) {
        Assert.field("files", files).notNull().notEmpty().noNullElement();

        return server.diagnostics(files);
    }
}
