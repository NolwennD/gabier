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
     * @throws LanguageServerException if the server is not ready or the file is not a file of its project (outside it, missing or a folder)
     */
    List<Diagnostic> diagnostics(Path file);

    /**
     * What renaming the symbol at a position to a new name changes, in the whole project.
     *
     * @return the change set to apply, which may create, edit and move files
     * @throws LanguageServerException if the server is not ready, the file is not a file of its project (outside it, missing or a folder), there is no symbol at
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
     * @throws LanguageServerException if the server is not ready, the file is not a file of its project (outside it, missing or a folder), the selection is not
     *     an expression that can be extracted or the name is not valid for the variable
     */
    ChangeSet extractVariable(Path file, Range selection, String name);

    /**
     * What extracting the statements, or the expression, of a selection into a new method changes: the selection is
     * replaced by a call, and the method, which carries the given name and not one the server would propose, is
     * declared in the class; the server works out its parameters and its return value. Only the file itself is edited.
     *
     * @param selection the statements or the expression, from the first character to the end of the last
     * @return the change set to apply
     * @throws LanguageServerException if the server is not ready, the file is not a file of its project (outside it, missing or a folder), the selection cannot
     *     be extracted (half a statement, a {@code return} of a branch) or the name is not valid for the method
     */
    ChangeSet extractMethod(Path file, Range selection, String name);

    /**
     * What making the nested type that encloses a position a top-level type in a file of its own changes: the file is
     * created in the package of the type, named after it, and the references and the imports of the project are
     * updated. A type that uses the instance of the class that encloses it gets a field and a constructor parameter of
     * that class.
     *
     * @return the change set to apply, which creates a file and edits the others
     * @throws LanguageServerException if the server is not ready, the file is not a file of its project (outside it,
     *     missing or a folder) or the type cannot be moved (there is none at the position, it is a top-level type)
     */
    ChangeSet moveTypeToFile(Path file, Position position);

    /**
     * What inlining the local variable, the method or the constant at a position changes: every use is replaced by its
     * value (the body of a method) and the declaration is removed, in this file and in the others that use it. On a
     * method or a constant, a position on a use and not on the declaration inlines that use only, and the declaration
     * stays.
     *
     * @return the change set to apply, which only edits files
     * @throws LanguageServerException if the server is not ready, the file is not a file of its project (outside it,
     *     missing or a folder) or the server has nothing to inline there (not on a variable, a method or a constant, a
     *     variable assigned more than once, a method that is recursive or abstract, a declaration that nothing uses)
     */
    ChangeSet inline(Path file, Position position);

    /**
     * What organizing the imports of a file changes: the missing imports are added, the unused ones removed, and the
     * rest ordered as the server is configured to (for gabier, as the project keeps them, with no import collapsed into
     * a star). Only the file itself is edited.
     *
     * @return the change set to apply, none when the imports are already as they should be
     * @throws LanguageServerException if the server is not ready or the file is not a file of its project (outside it, missing or a folder)
     */
    Optional<ChangeSet> organizeImports(Path file);
}
