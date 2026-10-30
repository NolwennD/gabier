package fr.craft.gabier.editing.domain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * One operation on the {@link FileStore}, with what it takes to undo it. A step is undone in the state it may have
 * left behind, including the state of a step that failed halfway: undoing a step that did nothing does nothing.
 */
@SuppressWarnings("ArrayRecordComponent") // Steps are never compared nor hashed: the bytes are only written back.
sealed interface Step {

    /** The file the step is about, for the reports. */
    Path target();

    void apply(FileStore store) throws IOException;

    void undo(FileStore store) throws IOException;

    /** Writes a file: creates it when {@code previous} is empty, replaces its bytes otherwise. */
    record Write(Path target, byte[] content, Optional<byte[]> previous) implements Step {

        @Override
        public void apply(FileStore store) throws IOException {
            store.write(target, content);
        }

        @Override
        public void undo(FileStore store) throws IOException {
            if (previous.isPresent()) {
                store.write(target, previous.get());
            } else if (store.exists(target)) {
                store.delete(target);
            }
        }
    }

    record Move(Path from, Path target) implements Step {

        @Override
        public void apply(FileStore store) throws IOException {
            store.move(from, target);
        }

        @Override
        public void undo(FileStore store) throws IOException {
            if (store.exists(target)) {
                store.move(target, from);
            }
        }
    }

    record Delete(Path target, byte[] previous) implements Step {

        @Override
        public void apply(FileStore store) throws IOException {
            store.delete(target);
        }

        @Override
        public void undo(FileStore store) throws IOException {
            if (!store.exists(target)) {
                store.write(target, previous);
            }
        }
    }
}
