package fr.craft.gabier.editing.domain;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * What gabier asks of a language server for Java, in its own terms: no protocol type crosses this port. It is the port
 * of the whole context to a server; the adapter of a real server lives at the edge, and the tests of the core use a
 * hand-written fake.
 *
 * <p>Files are given by their path; positions follow {@link Position}. A request the server declines is a {@link
 * LanguageServerException}, with its key and its parameters; nothing is written whatever the answer: the port only
 * says, applying is the business of {@link ChangeApplier}.
 */
public interface JavaLanguageServer {

    /**
     * The problems of a source file, as the server sees the project.
     *
     * @return the diagnostics, none when the file is clean
     * @throws LanguageServerException if the server is not ready or the file is not in its project
     */
    List<Diagnostic> diagnostics(Path file);

    /**
     * What renaming the symbol at a position to a new name changes, in the whole project.
     *
     * @return the change set to apply, which may create, edit and move files
     * @throws LanguageServerException if the server is not ready, the file is not in its project, there is no symbol at
     *     the position or the name is not valid for it
     */
    ChangeSet rename(Path file, Position position, String newName);

    /**
     * What extracting the expression of a selection into a new local variable changes: the expression is replaced by the
     * variable, which is declared before the statement that holds the expression and carries the given name, not one the
     * server would propose. Only the file itself is edited.
     *
     * @param selection the expression, from its first character to the end of its last
     * @return the change set to apply
     * @throws LanguageServerException if the server is not ready, the file is not in its project, the selection is not
     *     an expression that can be extracted or the name is not valid for the variable
     */
    ChangeSet extractVariable(Path file, Range selection, String name);

    /**
     * What organizing the imports of a file changes: the missing imports are added, the unused ones removed, and the
     * rest ordered as the server is configured to (for gabier, as the project keeps them, with no import collapsed into
     * a star). Only the file itself is edited.
     *
     * @return the change set to apply, none when the imports are already as they should be
     * @throws LanguageServerException if the server is not ready or the file is not in its project
     */
    Optional<ChangeSet> organizeImports(Path file);
}
