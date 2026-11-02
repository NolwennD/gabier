package eu.gabier.editing.domain;

import eu.gabier.shared.error.domain.Assert;

/**
 * The replacement of a range of a text. An empty replacement deletes the range, an empty range inserts the replacement.
 *
 * @param range what is replaced
 * @param newText what takes its place
 */
public record TextEdit(Range range, String newText) {

    public TextEdit {
        Assert.notNull("range", range);
        Assert.notNull("newText", newText);
    }
}
