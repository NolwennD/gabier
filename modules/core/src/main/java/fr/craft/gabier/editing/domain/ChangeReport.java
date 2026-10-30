package fr.craft.gabier.editing.domain;

import fr.craft.gabier.shared.error.domain.Assert;
import java.nio.file.Path;
import java.util.List;

/**
 * What a set of changes does, or did. The lists follow the changes of the set, in their order: a file is listed under
 * the path it had when the change ran, and one that is created and then edited is both created and modified.
 *
 * @param created the files created
 * @param renamed the files moved
 * @param deleted the files deleted
 * @param modified the files whose text changed, each once; a file whose edits leave its text as it was is not listed
 * @param diff the unified diff of every change, in order (the format of {@code diff -u}, three lines of context): the
 *     headers of a created, renamed or deleted file, and the lines that an edit or a deletion changes. Paths are
 *     printed as they were given.
 */
public record ChangeReport(
        List<Path> created, List<Renamed> renamed, List<Path> deleted, List<Path> modified, String diff) {

    public ChangeReport {
        Assert.field("created", created).notNull().noNullElement();
        Assert.field("renamed", renamed).notNull().noNullElement();
        Assert.field("deleted", deleted).notNull().noNullElement();
        Assert.field("modified", modified).notNull().noNullElement();
        Assert.notNull("diff", diff);
        created = List.copyOf(created);
        renamed = List.copyOf(renamed);
        deleted = List.copyOf(deleted);
        modified = List.copyOf(modified);
    }

    /**
     * A file moved.
     *
     * @param from where the file was
     * @param to where it is
     */
    public record Renamed(Path from, Path to) {

        public Renamed {
            Assert.notNull("from", from);
            Assert.notNull("to", to);
        }
    }
}
