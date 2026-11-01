package fr.craft.gabier.editing.domain;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/**
 * Where the files are, as far as editing is concerned: the one port of the context, which is the only thing that
 * touches a file system. It deals in bytes; the text, its encoding and its lines are the business of the domain. A
 * method that fails on the file system throws an {@link IOException} and leaves the file as it was.
 */
public interface FileStore {

    /** Whether a file, a directory or a link exists at the path (a link counts, even if its target is gone). */
    boolean exists(Path file);

    /** Whether the path is a folder, or a link to one. */
    boolean isFolder(Path path);

    byte[] read(Path file) throws IOException;

    /**
     * Replaces the whole content of the file, or creates the file; its directory must exist. A file that exists is
     * replaced as a whole, never left half written, and keeps its permissions; a link is written through.
     */
    void write(Path file, byte[] content) throws IOException;

    /**
     * Creates a file at a path where nothing exists, a link included, and writes the bytes in it; its directory must
     * exist. It never replaces what is there, even what appeared since the caller looked: it fails with a {@link
     * java.nio.file.FileAlreadyExistsException} and leaves it as it was.
     */
    void create(Path file, byte[] content) throws IOException;

    /**
     * Creates one folder at a path where nothing exists, a link included; its parent must exist. It never makes the
     * parents, so that each folder made is one that can be removed again: it fails with a {@link
     * java.nio.file.FileAlreadyExistsException} if something is there, and with a {@link
     * java.nio.file.NoSuchFileException} if the parent is not.
     */
    void createFolder(Path folder) throws IOException;

    /** The POSIX permissions of the file, which must exist. */
    Set<PosixFilePermission> permissions(Path file) throws IOException;

    /** Gives the file, which must exist, these POSIX permissions. */
    void setPermissions(Path file, Set<PosixFilePermission> permissions) throws IOException;

    /** Moves a file to a path where nothing exists; its directory must exist. */
    void move(Path from, Path to) throws IOException;

    /** Deletes a file, or a folder that is empty. */
    void delete(Path file) throws IOException;
}
