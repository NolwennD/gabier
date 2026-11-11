package eu.gabier.editing.domain;

import eu.gabier.shared.error.domain.Assert;
import java.nio.file.Path;

/**
 * The file that describes a package: a file named {@code package-info.java}, whatever folder it is in.
 *
 * @param path the file
 */
public record PackageInfoFile(Path path) {

    private static final String NAME = "package-info.java";

    public PackageInfoFile {
        Assert.notNull("path", path);
    }

    /**
     * The file that describes a package, if the path names one.
     *
     * @throws EditingException if the name of the file is not {@code package-info.java}
     */
    public static PackageInfoFile of(Path file) {
        Assert.notNull("file", file);
        if (!NAME.equals(String.valueOf(file.getFileName()))) {
            throw EditingException.notAPackageInfo(file);
        }

        return new PackageInfoFile(file);
    }
}
