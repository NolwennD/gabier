package fr.craft.gabier.editing.domain;

import fr.craft.gabier.shared.error.domain.GeneratorException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Every refusal of a language server, as the port {@link JavaLanguageServer} reports it: the adapter of a real server
 * and the fake of the tests throw these and nothing else for a request the server declines. Its {@link #key()} says
 * which one, its {@link #parameters()} give the values a report shows; positions in parameters are the {@code line} and
 * the {@code column}, 0-based, as in {@link Position}.
 *
 * <p>All of them are refusals of a request, not failures of gabier: their status is a bad request.
 */
public final class LanguageServerException extends GeneratorException {

    private LanguageServerException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    /** The server is still importing the project and cannot answer yet. */
    public static LanguageServerException notReady() {
        return new LanguageServerException(GeneratorException.badRequest(LanguageServerErrorKey.NOT_READY)
                .message("The language server is still importing the project: try again later"));
    }

    /** There is nothing to act on at the position. */
    public static LanguageServerException noSuchSymbol(Path file, Position position) {
        return new LanguageServerException(positioned(LanguageServerErrorKey.NO_SUCH_SYMBOL, file, position)
                .message("There is no symbol at " + format(position) + " in " + file));
    }

    /** The selection is not an expression the server can act on (nothing selected, a statement, a type, a void call). */
    public static LanguageServerException notAnExpression(Path file, Range selection) {
        return new LanguageServerException(positioned(LanguageServerErrorKey.NOT_AN_EXPRESSION, file, selection.start())
                .message("The selection " + format(selection.start()) + "-" + format(selection.end()) + " of " + file
                        + " is not an expression")
                .addParameter("endLine", String.valueOf(selection.end().line()))
                .addParameter("endColumn", String.valueOf(selection.end().column())));
    }

    /**
     * The selection is not statements or an expression the server can extract into a method (half a statement, a
     * {@code return} of a branch that the rest of the method needs, a type).
     */
    public static LanguageServerException notExtractable(Path file, Range selection) {
        return new LanguageServerException(positioned(LanguageServerErrorKey.NOT_EXTRACTABLE, file, selection.start())
                .message("The selection " + format(selection.start()) + "-" + format(selection.end()) + " of " + file
                        + " is not statements or an expression to extract")
                .addParameter("endLine", String.valueOf(selection.end().line()))
                .addParameter("endColumn", String.valueOf(selection.end().column())));
    }

    /**
     * The selection is not an expression the server can extract into a constant: it reads a local variable or an
     * instance member, or it is not an expression. The server does not tell which.
     */
    public static LanguageServerException notAConstant(Path file, Range selection) {
        return new LanguageServerException(positioned(LanguageServerErrorKey.NOT_EXTRACTABLE, file, selection.start())
                .message("The selection " + format(selection.start()) + "-" + format(selection.end()) + " of " + file
                        + " is not an expression that can be a constant (it reads no local variable and no instance"
                        + " member)")
                .addParameter("endLine", String.valueOf(selection.end().line()))
                .addParameter("endColumn", String.valueOf(selection.end().column())));
    }

    /**
     * The server cannot move to a file of its own the type at the position: there is no type there, it is a top-level
     * type, or the file of its name exists in its package (the server does not tell which).
     */
    public static LanguageServerException notMovable(Path file, Position position) {
        return new LanguageServerException(positioned(LanguageServerErrorKey.NOT_MOVABLE, file, position)
                .message("No nested type at " + format(position) + " in " + file
                        + " can be moved to a file of its own: the position is outside any type, the type is a"
                        + " top-level type, or a file of its name exists in its package"));
    }

    /**
     * Files cannot be moved to the package, for a reason gabier decides: the server does not refuse them itself.
     *
     * @param files the files that cannot be moved: the offending ones, or all of them when the reason is about their set
     */
    public static LanguageServerException notMovableToPackage(
            List<Path> files, PackageName target, NotMovableReason reason) {
        String names = files.stream().map(Path::toString).collect(Collectors.joining(", "));

        return new LanguageServerException(GeneratorException.badRequest(LanguageServerErrorKey.NOT_MOVABLE_TO_PACKAGE)
                .message("Cannot move " + names + " to the package " + target.value() + ": " + reason.sentence())
                .addParameter("files", names)
                .addParameter("package", target.value())
                .addParameter("reason", reason.get()));
    }

    /**
     * The server has nothing to inline at the position: it is not on a local variable, a method or a constant, or the
     * server refuses the inlining (a variable assigned more than once, a method that is recursive or abstract, a
     * declaration that nothing uses). The server does not tell which.
     */
    public static LanguageServerException notInlinable(Path file, Position position) {
        return new LanguageServerException(positioned(LanguageServerErrorKey.NOT_INLINABLE, file, position)
                .message("Nothing at " + format(position) + " in " + file
                        + " can be inlined: the position is not on a local variable, a method or a constant, or the"
                        + " server refuses it (a variable assigned more than once, a method that is recursive or"
                        + " abstract, a declaration that nothing uses)"));
    }

    /**
     * The server offers no type of this kind to create in the file: the file is outside every source folder of the
     * project (so no package can be derived for it), or the Java level of the project has no such type (a record
     * before Java 14). The server does not tell which.
     */
    public static LanguageServerException notCreatable(Path file, TypeKind kind) {
        return new LanguageServerException(GeneratorException.badRequest(LanguageServerErrorKey.NOT_CREATABLE)
                .message("A " + kind.keyword() + " cannot be created in " + file
                        + ": the file is outside every source folder of the project, or the Java level of the project"
                        + " has no such type")
                .addParameter("file", file.toString())
                .addParameter("kind", kind.keyword()));
    }

    /** The server does not accept the name for the symbol at the position. */
    public static LanguageServerException invalidName(Path file, Position position, String name) {
        return new LanguageServerException(positioned(LanguageServerErrorKey.INVALID_NAME, file, position)
                .message("\"" + name + "\" is not a valid name for the symbol at " + format(position) + " in " + file)
                .addParameter("name", name));
    }

    /** The file is not one of the project the server was started on. */
    public static LanguageServerException fileOutsideProject(Path file) {
        return new LanguageServerException(GeneratorException.badRequest(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT)
                .message("The file " + file + " is not in the project of the language server")
                .addParameter("file", file.toString()));
    }

    /** The file is under the project but there is nothing at its path. */
    public static LanguageServerException fileMissing(Path file) {
        return new LanguageServerException(GeneratorException.badRequest(LanguageServerErrorKey.FILE_MISSING)
                .message("The file " + file + " does not exist")
                .addParameter("file", file.toString()));
    }

    /** The path is under the project but it is a folder, not a source file. */
    public static LanguageServerException notAFile(Path file) {
        return new LanguageServerException(GeneratorException.badRequest(LanguageServerErrorKey.NOT_A_FILE)
                .message(file + " is not a file")
                .addParameter("file", file.toString()));
    }

    /**
     * Imports of the file that the server cannot resolve: it would delete them, so nothing is changed.
     *
     * @param imports the qualified names of the imports, as written in the file
     */
    public static LanguageServerException unresolvedImports(Path file, List<String> imports) {
        String names = String.join(", ", imports);

        return new LanguageServerException(GeneratorException.badRequest(LanguageServerErrorKey.UNRESOLVED_IMPORTS)
                .message("The language server cannot resolve the imports " + names + " of " + file
                        + ": organizing them would delete them")
                .addParameter("file", file.toString())
                .addParameter("imports", names));
    }

    /**
     * Types the file uses without importing them and that several packages declare: choosing is up to the caller.
     *
     * @param candidates the qualified names of every type the server offers, the candidates for one simple name being
     *     together
     */
    public static LanguageServerException ambiguousImports(Path file, List<String> candidates) {
        Map<String, List<String>> bySimpleName = new LinkedHashMap<>();
        for (String candidate : candidates) {
            bySimpleName
                    .computeIfAbsent(candidate.substring(candidate.lastIndexOf('.') + 1), name -> new ArrayList<>())
                    .add(candidate);
        }
        String types = bySimpleName.entrySet().stream()
                .map(type -> type.getKey() + " (" + String.join(", ", type.getValue()) + ")")
                .collect(Collectors.joining("; "));

        return new LanguageServerException(GeneratorException.badRequest(LanguageServerErrorKey.AMBIGUOUS_IMPORTS)
                .message("The file " + file + " uses types that several packages declare, so none was imported: "
                        + types)
                .addParameter("file", file.toString())
                .addParameter("candidates", String.join(", ", candidates)));
    }

    private static GeneratorExceptionBuilder positioned(LanguageServerErrorKey key, Path file, Position position) {
        return GeneratorException.badRequest(key)
                .addParameter("file", file.toString())
                .addParameter("line", String.valueOf(position.line()))
                .addParameter("column", String.valueOf(position.column()));
    }

    private static String format(Position position) {
        return position.line() + ":" + position.column();
    }
}
