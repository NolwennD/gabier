package eu.gabier.editing.domain;

import eu.gabier.shared.error.domain.Assert;
import java.util.List;

/**
 * What a language server asks for, as one unit: it is applied entirely or not at all. The changes are applied one after
 * the other, in the order of the list, each on the result of the previous ones: a file is created before it is edited,
 * and a file that is renamed is edited under its new path afterwards (and under its old path before).
 *
 * @param changes at least one change, in the order they are applied
 */
public record ChangeSet(List<FileChange> changes) {

    public ChangeSet {
        Assert.field("changes", changes).notEmpty().noNullElement();
        changes = List.copyOf(changes);
    }
}
