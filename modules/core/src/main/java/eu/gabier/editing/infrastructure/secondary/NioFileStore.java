package eu.gabier.editing.infrastructure.secondary;

import eu.gabier.editing.domain.FileStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/** The files of the machine, through {@code java.nio.file}. */
public final class NioFileStore implements FileStore {

    @Override
    public boolean exists(Path file) {
        return Files.exists(file, LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public boolean isFolder(Path path) {
        return Files.isDirectory(path);
    }

    @Override
    public byte[] read(Path file) throws IOException {
        return Files.readAllBytes(file);
    }

    @Override
    public void write(Path file, byte[] content) throws IOException {
        if (Files.exists(file)) {
            replace(file.toRealPath(), content);
        } else {
            Files.write(file, content);
        }
    }

    /**
     * Writes the new content next to the file, with its attributes (permissions included), then moves it over the file:
     * the file is whole before and whole after, whatever happens in between. {@code file} is the real file, not a link
     * to it.
     */
    private static void replace(Path file, byte[] content) throws IOException {
        Path temporary = Files.createTempFile(file.getParent(), ".gabier-", ".tmp");
        try {
            Files.copy(file, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            Files.write(temporary, content);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public void create(Path file, byte[] content) throws IOException {
        Files.write(file, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    @Override
    public void createFolder(Path folder) throws IOException {
        Files.createDirectory(folder);
    }

    @Override
    public Set<PosixFilePermission> permissions(Path file) throws IOException {
        return Files.getPosixFilePermissions(file);
    }

    @Override
    public void setPermissions(Path file, Set<PosixFilePermission> permissions) throws IOException {
        Files.setPosixFilePermissions(file, permissions);
    }

    @Override
    public void move(Path from, Path to) throws IOException {
        Files.move(from, to);
    }

    @Override
    public void delete(Path file) throws IOException {
        Files.delete(file);
    }
}
