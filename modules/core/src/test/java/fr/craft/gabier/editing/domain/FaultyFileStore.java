package fr.craft.gabier.editing.domain;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link FileStore} on the real files of a delegate that fails where the test says so: not a mock of the file system,
 * only a way to make a given call fail whatever the rights of the user who runs the tests.
 */
final class FaultyFileStore implements FileStore {

    enum Operation {
        WRITE,
        MOVE,
        DELETE
    }

    enum Failure {
        /** An {@link IOException}, the file left as it was. */
        IO,
        /** The first half of the bytes is written, then an {@link IOException}: a disk that fills up. */
        HALF_WRITTEN,
        /** A runtime exception that is not the store's to throw. */
        RUNTIME
    }

    private record Fault(Operation operation, Path path, int call, Failure failure) {}

    private final FileStore delegate;
    private final List<Fault> faults = new ArrayList<>();
    private final Map<String, Integer> calls = new HashMap<>();

    FaultyFileStore(FileStore delegate) {
        this.delegate = delegate;
    }

    /** Makes the {@code call}-th (from 1) call of the operation on the path fail. */
    FaultyFileStore failing(Operation operation, Path path, int call, Failure failure) {
        faults.add(new Fault(operation, path, call, failure));

        return this;
    }

    /** Makes the first call of the operation on the path fail. */
    FaultyFileStore failing(Operation operation, Path path) {
        return failing(operation, path, 1, Failure.IO);
    }

    private Failure check(Operation operation, Path path) {
        int call = calls.merge(operation + " " + path, 1, Integer::sum);

        return faults.stream()
                .filter(fault -> fault.operation() == operation && fault.path().equals(path) && fault.call() == call)
                .map(Fault::failure)
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean exists(Path file) {
        return delegate.exists(file);
    }

    @Override
    public byte[] read(Path file) throws IOException {
        return delegate.read(file);
    }

    @Override
    public void write(Path file, byte[] content) throws IOException {
        Failure failure = check(Operation.WRITE, file);
        if (failure == Failure.HALF_WRITTEN) {
            delegate.write(file, Arrays.copyOf(content, content.length / 2));
        }
        fail(failure, file);
        delegate.write(file, content);
    }

    @Override
    public void move(Path from, Path to) throws IOException {
        fail(check(Operation.MOVE, to), to);
        delegate.move(from, to);
    }

    @Override
    public void delete(Path file) throws IOException {
        fail(check(Operation.DELETE, file), file);
        delegate.delete(file);
    }

    private static void fail(Failure failure, Path path) throws IOException {
        if (failure == Failure.RUNTIME) {
            throw new UncheckedIOException(new IOException("injected runtime failure on " + path));
        }
        if (failure != null) {
            throw new IOException("injected failure on " + path);
        }
    }
}
