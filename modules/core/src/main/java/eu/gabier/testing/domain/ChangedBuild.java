package eu.gabier.testing.domain;

import eu.gabier.shared.error.domain.Assert;
import java.nio.file.Path;
import java.util.List;

/**
 * What the build gives once it has compiled what changed, with the Java sources that changed since the last time the
 * changes were answered for: the ones that were created or edited on disk, and still exist.
 *
 * @param built where the classes are, the libraries and the files with an error
 * @param sources the Java files that changed and were not answered for yet, as absolute paths, in the order of the paths
 * @param acknowledgement what to run once the tests of these files were answered: it forgets these changes, and only
 *     them (a file changed again since stays among the changes)
 */
public record ChangedBuild(BuiltProject built, List<Path> sources, Runnable acknowledgement) {

    public ChangedBuild {
        Assert.notNull("built", built);
        Assert.field("sources", sources).notNull().noNullElement();
        Assert.notNull("acknowledgement", acknowledgement);

        sources = List.copyOf(sources);
    }

    /** The tests of these sources were answered: the next call lists only what changes after them. */
    public void answered() {
        acknowledgement.run();
    }
}
