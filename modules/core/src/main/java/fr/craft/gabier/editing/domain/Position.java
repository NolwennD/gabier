package fr.craft.gabier.editing.domain;

import fr.craft.gabier.shared.error.domain.Assert;

/**
 * A place in a text. This is the one position convention of gabier, chosen once:
 *
 * <ul>
 *   <li>{@code line} counts from <b>0</b>; a line ends at {@code \n}, {@code \r\n} or {@code \r}. The empty line after
 *       the last terminator of a text is a line of its own.
 *   <li>{@code column} counts from <b>0</b>, in <b>UTF-16 code units</b>: the unit of a Java {@code String} index, so
 *       a character outside the BMP (an emoji, for example) takes two columns. The column of the end of a line is the
 *       length of that line without its terminator.
 * </ul>
 *
 * <p>This is the default encoding of the Language Server Protocol ({@code position.line} and {@code
 * position.character}, 0-based, UTF-16): the LSP adapter copies both numbers as they are. It converts only if a server
 * negotiated another position encoding (UTF-8 or UTF-32), using the text of the document. The CLI shows {@code
 * line:column} from 1: it adds one to both numbers for display and subtracts one from what the user types.
 *
 * <p>A column that splits a surrogate pair is not a position in the text: the edit that uses it is refused when it is
 * applied.
 *
 * @param line the line, from 0
 * @param column the column in UTF-16 code units, from 0
 */
public record Position(int line, int column) implements Comparable<Position> {

    public Position {
        Assert.field("line", line).positive();
        Assert.field("column", column).positive();
    }

    @Override
    public int compareTo(Position other) {
        int byLine = Integer.compare(line, other.line);

        return byLine != 0 ? byLine : Integer.compare(column, other.column);
    }
}
