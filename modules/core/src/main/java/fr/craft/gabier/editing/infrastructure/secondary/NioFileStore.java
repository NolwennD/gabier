package fr.craft.gabier.editing.infrastructure.secondary;

import fr.craft.gabier.editing.domain.FileStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** The files of the machine, through {@code java.nio.file}. */
public final class NioFileStore implements FileStore {

    @Override
    public boolean exists(Path file) {
        return Files.exists(file, LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public byte[] read(Path file) throws IOException {
        return Files.readAllBytes(file);
    }

    @Override
    public void write(Path file, byte[] content) throws IOException {
        Files.write(file, content);
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
