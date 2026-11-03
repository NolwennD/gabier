package eu.gabier.daemon;

import eu.gabier.editing.domain.Position;
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

    /** Whether the position is on a character of the name in a {@code package} line of the text. */
    static boolean nameIsAt(String text, Position position) {
        String[] lines = text.split("\\R", position.line() + 2);
        if (position.line() >= lines.length) {
            return false;
        }
        var declaration = DECLARATION.matcher(lines[position.line()]);

        return declaration.find()
                && position.column() >= declaration.start(1)
                && position.column() < declaration.end(1);
    }
}
