package fr.craft.gabier.editing.application;

import fr.craft.gabier.editing.domain.Diagnostic;
import fr.craft.gabier.editing.domain.JavaLanguageServer;
import fr.craft.gabier.shared.error.domain.Assert;
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
     * @return the diagnostics of the file, none when it is clean
     * @throws fr.craft.gabier.editing.domain.LanguageServerException if the server declines: it is not ready, or the
     *     file is not in its project
     */
    public List<Diagnostic> diagnose(Path file) {
        Assert.notNull("file", file);

        return server.diagnostics(file);
    }
}
