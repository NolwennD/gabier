package eu.gabier.editing.domain;

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
     * The problems of source files, as the server sees the project, asked together: what a request costs is the wait for
     * the server to be quiet, which the files share.
     *
     * @return one list of diagnostics per file, in the order given (none for a file that is clean), the same list for a
     *     file that is given twice
     * @throws LanguageServerException if the server is not ready or one of the files is not a file of its project
     *     (outside it, missing or a folder): the whole call is refused
     */
    List<List<Diagnostic>> diagnostics(List<Path> files);

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
     * @throws LanguageServerException if the server is not ready, the file is not a file of its project (outside it, missing or a folder), the selection cannot
     *     be extracted or the name is not valid for the variable
     */
    ChangeSet extractVariable(Path file, Range selection, String name);

    /**
     * What extracting the expression of a selection into a new constant ({@code private static final}, declared at the
     * top of the class) changes: the selection is replaced by the constant, which carries the given name and not one
     * the server would propose. Only the file itself is edited.
     *
     * @param selection the expression, from its first character to the end of its last
     * @return the change set to apply
     * @throws LanguageServerException if the server is not ready, the file is not a file of its project (outside it,
     *     missing or a folder), the selection cannot be
     *     extracted or the name is not valid for the constant
     */
    ChangeSet extractConstant(Path file, Range selection, String name);

    /**
     * What extracting the expression of a selection into a new field of the class changes: the selection is replaced by
     * the field, which carries the given name and not one the server would propose, and the server declares it and
     * initialises it where it proposes (by default in the current method, before the statement that holds the
     * expression). Only the file itself is edited.
     *
     * @param selection the expression, from its first character to the end of its last
     * @return the change set to apply
     * @throws LanguageServerException if the server is not ready, the file is not a file of its project (outside it,
     *     missing or a folder), the selection cannot be
     *     extracted or the name is not valid for the field
     */
    ChangeSet extractField(Path file, Range selection, String name);

    /**
     * What extracting the statements, or the expression, of a selection into a new method changes: the selection is
     * replaced by a call, and the method, which carries the given name and not one the server would propose, is
     * declared in the class; the server works out its parameters and its return value. Only the file itself is edited.
     *
     * @param selection the statements or the expression, from the first character to the end of the last
     * @return the change set to apply
     * @throws LanguageServerException if the server is not ready, the file is not a file of its project (outside it, missing or a folder), the selection cannot
     *     be extracted or the name is not valid for the method
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
     * What moving files to a package changes: each file goes to the folder of the package under its own source folder,
     * keeping its name, its {@code package} line is rewritten, and the imports and the references of the project are
     * updated. The folder of a package that does not exist yet is not created by the server: the change set moves the
     * files into it. The files must be Java sources of one source folder of the project, none of them in the package
     * already or of the name of a file the package holds.
     *
     * @return the change set to apply, which edits files and moves the files
     * @throws LanguageServerException if the server is not ready, a file is not a file of its project (outside it,
     *     missing or a folder), or the files cannot be moved to the package (see {@link NotMovableReason})
     */
    ChangeSet moveToPackage(List<Path> files, PackageName target);

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
     * What creating a type of a kind in a new file changes: the file is created, with the {@code package} line its path
     * gives (none in the default package), the declaration of an empty type of that kind named after the file, and no
     * comment. The file need not exist, and the server does not write it.
     *
     * @return the change set to apply, which creates the file and gives it its text
     * @throws LanguageServerException if the server is not ready or the file is not in the project
     */
    ChangeSet createType(Path file, TypeKind kind);

    /**
     * Where the symbol at a position (a type, a method, a field, a local variable or a parameter of the project) is
     * used, in every file of the project, the test sources included. Nothing is written. A symbol that nothing uses has
     * no reference, which is an answer and not a refusal.
     *
     * @param includeDeclaration whether the place where the symbol is declared is answered too, apart from the uses
     * @return the places of the uses, and of the declaration when it was asked for
     * @throws LanguageServerException if the server is not ready, the file is not a file of its project (outside it,
     *     missing or a folder) or there is no symbol of the project at the position (a blank, a comment, a keyword, a
     *     type of the JDK or of a library)
     */
    ReferenceLocations references(Path file, Position position, boolean includeDeclaration);

    /**
     * The code actions the server offers on a range: the fixes of the problems the range holds (a type that is not
     * imported, a method to create) and the rewrites of the code it covers (a concatenation to a text block, an
     * extraction). The actions of the whole file (organizing the imports, generating accessors) are not about a range
     * and are not listed. A range with nothing offered is an empty answer, not a refusal. Nothing is written.
     *
     * @param range the range, from its first character to the end of its last; a point is an empty range
     * @return what the server offers, in its order
     * @throws LanguageServerException if the server is not ready or the file is not a file of its project (outside it,
     *     missing or a folder)
     */
    OfferedActions codeActions(Path file, Range range);

    /**
     * What applying one of the actions offered on a range changes. The action is one that {@link #codeActions} gave
     * for the same file and range, and the server is asked again for it: it computes the edit when the action is chosen.
     *
     * @return the change set to apply
     * @throws LanguageServerException if the server is not ready, the file is not a file of its project (outside it,
     *     missing or a folder), the server does not offer the action on the range any more, or it gives no edit for it
     *     (it is a command for an editor: a dialog to fill in, a place to choose)
     */
    ChangeSet codeActionEdit(Path file, Range range, OfferedAction action);

    /**
     * What organizing the imports of a file changes: the missing imports are added, the unused ones removed, and the
     * rest ordered as the server is configured to (for gabier, as the project says it in its import settings, which
     * the adapter gives the server). Only the file itself is edited.
     *
     * @return the change set to apply, none when the imports are already as they should be
     * @throws LanguageServerException if the server is not ready or the file is not a file of its project (outside it, missing or a folder)
     */
    Optional<ChangeSet> organizeImports(Path file);

    /**
     * What applying clean-ups to a file changes: each clean-up rewrites the text as the one before left it, in the order
     * given (a clean-up is a rewrite of the whole file, for instance of string concatenations into text blocks). Only
     * the file itself is edited.
     *
     * @param cleanUps the clean-ups to apply, at least one
     * @return the change set to apply, none when the file has nothing to clean up for them
     * @throws LanguageServerException if the server is not ready or the file is not a file of its project (outside it,
     *     missing or a folder)
     */
    Optional<ChangeSet> cleanUp(Path file, List<CleanUp> cleanUps);
}
