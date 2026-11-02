package eu.gabier.editing.domain;

import eu.gabier.shared.error.domain.Assert;

/**
 * A part of a text, from {@code start} included to {@code end} excluded (see {@link Position} for the convention). An
 * empty range, where both are equal, is a point: replacing it inserts.
 *
 * @param start where the range starts
 * @param end where the range ends, not before {@code start}
 */
public record Range(Position start, Position end) {

    public Range {
        Assert.notNull("start", start);
        Assert.notNull("end", end);
        if (end.compareTo(start) < 0) {
            throw EditingException.invalidRange(start, end);
        }
    }
}
