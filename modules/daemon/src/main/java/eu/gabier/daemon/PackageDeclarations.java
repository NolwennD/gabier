package eu.gabier.daemon;

import eu.gabier.editing.domain.Position;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Where the name of a package is in the text of a source file. jdtls renames a package from its name in the {@code
 * package} line, but declines to prepare that rename ({@code textDocument/prepareRename}: "Renaming this element is not
 * supported"), which is the answer it also gives for a blank or a keyword, so the adapter has to tell the two apart
 * itself.
 */
final class PackageDeclarations {

    /** A line that starts with {@code package} and a name: the name is the first group. */
    private static final Pattern DECLARATION = Pattern.compile("^\\s*package\\s+([\\p{L}\\p{N}_$.]+)");

    private PackageDeclarations() {}

    /**
     * The name in a {@code package} line, seen from a position on one of its characters.
     *
     * @param whole the whole name in the line
     * @param upToPosition the name up to the segment the position is on (a dot belongs to the segment before it): the
     *     package that jdtls renames
     */
    record Name(String whole, String upToPosition) {}

    /** The name in the {@code package} line when the position is on a character of it, else nothing. */
    static Optional<Name> nameAt(String text, Position position) {
        String[] lines = text.split("\\R", position.line() + 2);
        if (position.line() >= lines.length) {
            return Optional.empty();
        }
        var declaration = DECLARATION.matcher(lines[position.line()]);
        if (!declaration.find()
                || position.column() < declaration.start(1)
                || position.column() >= declaration.end(1)) {
            return Optional.empty();
        }
        String whole = declaration.group(1);
        int segmentEnd = whole.indexOf('.', position.column() - declaration.start(1));

        return Optional.of(new Name(whole, segmentEnd < 0 ? whole : whole.substring(0, segmentEnd)));
    }
}
