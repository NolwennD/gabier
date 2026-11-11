package eu.gabier.testing.domain;

import eu.gabier.shared.error.domain.Assert;
import java.nio.file.Path;
import java.util.List;

/**
 * What the build of the project gives the selection of tests, once it has compiled the files that changed: where the
 * classes are, the libraries they are compiled against, and the files the compiler found an error in.
 *
 * @param folders the source folders and their class folders, for every project of the build, the tests included
 * @param libraries the jars of the class path of the tests, where the classes of the test frameworks and the base
 *     classes that come from a library are
 * @param filesWithErrors the Java files of the project that have at least one error, as absolute paths
 */
public record BuiltProject(List<ClassFolder> folders, List<Path> libraries, List<Path> filesWithErrors) {

    public BuiltProject {
        Assert.field("folders", folders).notNull().noNullElement();
        Assert.field("libraries", libraries).notNull().noNullElement();
        Assert.field("filesWithErrors", filesWithErrors).notNull().noNullElement();

        folders = List.copyOf(folders);
        libraries = List.copyOf(libraries);
        filesWithErrors = List.copyOf(filesWithErrors);
    }
}
