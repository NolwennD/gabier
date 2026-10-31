package fr.craft.gabier.editing.domain;

import fr.craft.gabier.shared.error.domain.GeneratorException;
import java.nio.file.Path;

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
