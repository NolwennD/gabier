package eu.gabier.testing.domain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The files of the project, as far as the selection of tests is concerned: the compiled classes, the libraries, the
 * source files that were given and the file of filters. It is the one port of the context that touches the file system.
 * It deals in bytes and text; what they say is the business of the domain.
 */
public interface ProjectFiles {

    /**
     * The class files under a folder, whatever the depth, in the order of their paths.
     *
     * @return none when the folder does not exist
     */
    List<Path> classFilesIn(Path folder) throws IOException;

    byte[] read(Path classFile) throws IOException;

    /**
     * The bytes of a class of a library: a jar, or a folder of classes.
     *
     * @param binaryName the name of the class, with dots ({@code app.Outer$Inner})
     * @return none when the library has no such class, or does not exist
     */
    Optional<byte[]> readFromLibrary(Path library, String binaryName) throws IOException;

    /** Whether a regular file exists at the path. */
    boolean isFile(Path file);

    /**
     * The text of a file, read as UTF-8.
     *
     * @return none when there is no such file
     */
    Optional<String> textOf(Path file) throws IOException;
}
