package eu.gabier.testing.infrastructure.secondary;

import eu.gabier.testing.domain.ProjectFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** The files of the machine, through {@code java.nio.file} and {@code java.util.zip}. */
public final class NioProjectFiles implements ProjectFiles {

    @Override
    public List<Path> classFilesIn(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(file -> file.toString().endsWith(".class") && Files.isRegularFile(file))
                    .sorted()
                    .toList();
        }
    }

    @Override
    public byte[] read(Path classFile) throws IOException {
        return Files.readAllBytes(classFile);
    }

    @Override
    public Optional<byte[]> readFromLibrary(Path library, String binaryName) throws IOException {
        String entry = binaryName.replace('.', '/') + ".class";
        if (Files.isDirectory(library)) {
            Path file = library.resolve(entry);

            return Files.isRegularFile(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
        }
        try (ZipFile jar = new ZipFile(library.toFile())) {
            ZipEntry found = jar.getEntry(entry);
            if (found == null) {
                return Optional.empty();
            }
            try (var content = jar.getInputStream(found)) {
                return Optional.of(content.readAllBytes());
            }
        } catch (NoSuchFileException missing) {
            return Optional.empty();
        }
    }

    @Override
    public boolean isFile(Path file) {
        return Files.isRegularFile(file);
    }

    @Override
    public Optional<String> textOf(Path file) throws IOException {
        return Files.isRegularFile(file) ? Optional.of(Files.readString(file)) : Optional.empty();
    }
}
