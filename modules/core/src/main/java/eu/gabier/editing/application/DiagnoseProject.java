package eu.gabier.editing.application;

import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.editing.domain.JavaLanguageServer;
import eu.gabier.shared.error.domain.Assert;
import java.util.List;

/** The problems of every Java file of the project, as the language server sees them. */
public final class DiagnoseProject {

    private final JavaLanguageServer server;

    public DiagnoseProject(JavaLanguageServer server) {
        Assert.notNull("server", server);

        this.server = server;
    }

    /**
     * Asks the language server for the diagnostics of the whole project, in one request: the server decides which files
     * are the project's.
     *
     * @return one element per file, in the order of the paths, with no diagnostics for a file that is clean
     * @throws eu.gabier.editing.domain.LanguageServerException if the server is not ready
     */
    public List<DiagnosedFile> diagnose() {
        return server.projectDiagnostics();
    }
}
