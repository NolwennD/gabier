package fr.craft.gabier.editing.domain;

import fr.craft.gabier.shared.error.domain.GeneratorException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Every refusal of the editing context. Its {@link #key()} says which one, its {@link #parameters()} give the values a
 * report shows; positions in parameters are written {@code line:column}, 0-based, as in {@link Position}.
 */
public final class EditingException extends GeneratorException {

    private EditingException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    static EditingException invalidRange(Position start, Position end) {
        return new EditingException(GeneratorException.badRequest(EditingErrorKey.INVALID_RANGE)
                .message("The range ends before it starts")
                .addParameter("start", format(start))
                .addParameter("end", format(end)));
    }

    static EditingException unsupportedEncoding(Path file, String reason) {
        return new EditingException(GeneratorException.badRequest(EditingErrorKey.UNSUPPORTED_ENCODING)
                .message("The file " + file + " " + reason + ": gabier edits UTF-8 files only")
                .addParameter("file", file.toString()));
    }

    static EditingException overlappingEdits(Path file, Range first, Range second) {
        return new EditingException(GeneratorException.badRequest(EditingErrorKey.OVERLAPPING_EDITS)
                .message("Two edits of " + file + " share text")
                .addParameter("file", file.toString())
                .addParameter("first", format(first))
                .addParameter("second", format(second)));
    }

    static EditingException staleEdit(Path file, Position position) {
        return new EditingException(GeneratorException.badRequest(EditingErrorKey.STALE_EDIT)
                .message("The position " + format(position) + " is not in " + file + ": the file has changed")
                .addParameter("file", file.toString())
                .addParameter("line", String.valueOf(position.line()))
                .addParameter("column", String.valueOf(position.column())));
    }

    static EditingException tokenNotFound(Path file, String token) {
        return new EditingException(GeneratorException.badRequest(EditingErrorKey.TOKEN_NOT_FOUND)
                .message("\"" + token + "\" does not occur as a whole word in " + file)
                .addParameter("file", file.toString())
                .addParameter("token", token));
    }

    static EditingException fileMissing(Path file) {
        return new EditingException(GeneratorException.badRequest(EditingErrorKey.FILE_MISSING)
                .message("The file " + file + " does not exist")
                .addParameter("file", file.toString()));
    }

    static EditingException fileExists(Path file) {
        return new EditingException(GeneratorException.badRequest(EditingErrorKey.FILE_EXISTS)
                .message("Something already exists at " + file)
                .addParameter("file", file.toString()));
    }

    static EditingException folderMissing(Path file, Path folder) {
        return new EditingException(GeneratorException.badRequest(EditingErrorKey.FOLDER_MISSING)
                .message("There is no folder " + folder + ": " + file + " cannot go there")
                .addParameter("file", file.toString())
                .addParameter("folder", folder.toString()));
    }

    static EditingException notAPackageName(String text) {
        return new EditingException(GeneratorException.badRequest(EditingErrorKey.NOT_A_PACKAGE_NAME)
                .message("\"" + text + "\" is not the name of a package: it must be Java identifiers that are not"
                        + " keywords, separated by dots")
                .addParameter("package", text));
    }

    static EditingException notATypeName(Path file) {
        return new EditingException(GeneratorException.badRequest(EditingErrorKey.NOT_A_TYPE_NAME)
                .message("The file " + file + " cannot hold a type: its name must be a Java identifier that is not"
                        + " a keyword, followed by .java")
                .addParameter("file", file.toString()));
    }

    static EditingException readFailed(Path file, IOException cause) {
        return new EditingException(GeneratorException.internalServerError(EditingErrorKey.READ_FAILED)
                .message("The file " + file + " could not be read")
                .cause(cause)
                .addParameter("file", file.toString()));
    }

    /** A write failed and the files already written were put back. */
    static EditingException writeFailed(Path file, Exception cause) {
        return new EditingException(GeneratorException.internalServerError(EditingErrorKey.WRITE_FAILED)
                .message("The file " + file + " could not be written, nothing has changed")
                .cause(cause)
                .addParameter("file", file.toString()));
    }

    /** A write failed and some of the files already written could not be put back, listed from the last tried. */
    static EditingException rollbackIncomplete(Path file, List<Path> notRestored, Exception cause) {
        String paths = notRestored.stream().map(Path::toString).collect(Collectors.joining(", "));

        return new EditingException(GeneratorException.internalServerError(EditingErrorKey.ROLLBACK_INCOMPLETE)
                .message("The file " + file + " could not be written, and these could not be put back: " + paths)
                .cause(cause)
                .addParameter("file", file.toString())
                .addParameter("notRestored", paths));
    }

    private static String format(Range range) {
        return format(range.start()) + "-" + format(range.end());
    }

    private static String format(Position position) {
        return position.line() + ":" + position.column();
    }
}
