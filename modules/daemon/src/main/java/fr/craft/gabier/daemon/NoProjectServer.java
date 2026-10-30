package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.Diagnostic;
import fr.craft.gabier.editing.domain.JavaLanguageServer;
import fr.craft.gabier.editing.domain.LanguageServerException;
import fr.craft.gabier.editing.domain.Position;
import java.nio.file.Path;
import java.util.List;

/**
 * The language server of a daemon that serves no project yet: it declines every request as not ready. The daemon
 * started by hand has no other until it starts jdtls for a project (a ticket of its own); a client that asks for the
 * diagnostics of a file is told so, as a problem object, instead of getting an answer that means nothing.
 */
final class NoProjectServer implements JavaLanguageServer {

    @Override
    public List<Diagnostic> diagnostics(Path file) {
        throw LanguageServerException.notReady();
    }

    @Override
    public ChangeSet rename(Path file, Position position, String newName) {
        throw LanguageServerException.notReady();
    }
}
