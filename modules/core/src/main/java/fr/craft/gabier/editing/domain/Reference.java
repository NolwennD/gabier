package fr.craft.gabier.editing.domain;

import fr.craft.gabier.shared.error.domain.Assert;
import java.nio.file.Path;

/**
 * A place where a symbol is declared or used, with the line of source that holds it.
 *
 * @param file the file
 * @param position where the name starts (see {@link Position}: 0-based line and column)
 * @param source the line of the file that holds the position, as written (indentation included, no terminator)
 */
public record Reference(Path file, Position position, String source) {

    public Reference {
        Assert.notNull("file", file);
        Assert.notNull("position", position);
        Assert.notNull("source", source);
    }
}
