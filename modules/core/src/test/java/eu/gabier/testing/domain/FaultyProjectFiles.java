package eu.gabier.testing.domain;

import eu.gabier.testing.infrastructure.secondary.NioProjectFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The real files, except for the one call that the test makes fail with an {@link IOException}: not a mock of the file
 * system, only a way to make a read fail whatever the rights of the user who runs the tests.
 */
final class FaultyProjectFiles implements ProjectFiles {

    enum Operation {
        LIST,
        READ,
        READ_FROM_LIBRARY,
        TEXT
    }

    private final ProjectFiles delegate = new NioProjectFiles();
    private final Operation failing;

    FaultyProjectFiles(Operation failing) {
        this.failing = failing;
    }

    @Override
    public List<Path> classFilesIn(Path folder) throws IOException {
        fail(Operation.LIST);

        return delegate.classFilesIn(folder);
    }

    @Override
    public byte[] read(Path classFile) throws IOException {
        fail(Operation.READ);

        return delegate.read(classFile);
    }

    @Override
    public Optional<byte[]> readFromLibrary(Path library, String binaryName) throws IOException {
        fail(Operation.READ_FROM_LIBRARY);

        return delegate.readFromLibrary(library, binaryName);
    }

    @Override
    public boolean isFile(Path file) {
        return delegate.isFile(file);
    }

    @Override
    public Optional<String> textOf(Path file) throws IOException {
        fail(Operation.TEXT);

        return delegate.textOf(file);
    }

    private void fail(Operation operation) throws IOException {
        if (operation == failing) {
            throw new IOException("disk failure");
        }
    }
}
