package eu.gabier.editing.domain;

import eu.gabier.shared.error.domain.Assert;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * A place in a file of the project: where a language server says a symbol is declared or used. Locations are ordered
 * by file, then by position.
 *
 * @param file the file
 * @param position where the name starts (see {@link Position}: 0-based line and column)
 */
public record Location(Path file, Position position) implements Comparable<Location> {

    private static final Comparator<Location> ORDER =
            Comparator.comparing(Location::file).thenComparing(Location::position);

    public Location {
        Assert.notNull("file", file);
        Assert.notNull("position", position);
    }

    @Override
    public int compareTo(Location other) {
        return ORDER.compare(this, other);
    }
}
