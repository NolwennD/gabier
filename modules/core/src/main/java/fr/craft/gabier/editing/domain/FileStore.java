package fr.craft.gabier.editing.domain;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Where the files are, as far as editing is concerned: the one port of the context, which is the only thing that
 * touches a file system. It deals in bytes; the text, its encoding and its lines are the business of the domain. A
 * method that fails on the file system throws an {@link IOException} and leaves the file as it was.
 */
public interface FileStore {

    /** Whether a file, a directory or a link exists at the path (a link counts, even if its target is gone). */
    boolean exists(Path file);

    byte[] read(Path file) throws IOException;

    /** Replaces the whole content of the file, or creates the file; its directory must exist. */
    void write(Path file, byte[] content) throws IOException;

    /** Moves a file to a path where nothing exists; its directory must exist. */
    void move(Path from, Path to) throws IOException;

    void delete(Path file) throws IOException;
}
