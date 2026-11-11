package eu.gabier.testing.domain;

import eu.gabier.shared.error.domain.Assert;
import java.nio.file.Path;

/**
 * A source folder of the project and the folder where the build writes the classes compiled from it.
 *
 * @param sources the source folder, as an absolute path
 * @param classes the folder of the classes, as an absolute path: several source folders (the main sources and the
 *     resources of a Maven project) may share it
 */
public record ClassFolder(Path sources, Path classes) {

    public ClassFolder {
        Assert.notNull("sources", sources);
        Assert.notNull("classes", classes);
    }
}
