package fr.craft.gabier.editing.domain;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

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

    /** Replaces the bytes of a file, and gives them back as they were on undo. */
    record Write(Path target, byte[] content, byte[] previous) implements Step {

        @Override
        public void apply(FileStore store) throws IOException {
            store.write(target, content);
        }

        @Override
        public void undo(FileStore store) throws IOException {
            store.write(target, previous);
        }
    }

    /**
     * Creates a file, which never replaces one. It is deleted on undo only if this step created it: a file that
     * appeared at the target since the plan stays, and so does the target when the step did not even try.
     */
    final class Create implements Step {

        private final Path target;
        private final byte[] content;
        private boolean foreign;

        Create(Path target, byte[] content) {
            this.target = target;
            this.content = content;
        }

        @Override
        public Path target() {
            return target;
        }

        @Override
        public void apply(FileStore store) throws IOException {
            try {
                store.create(target, content);
            } catch (FileAlreadyExistsException alreadyThere) {
                foreign = true;
                throw alreadyThere;
            }
        }

        @Override
        public void undo(FileStore store) throws IOException {
            if (!foreign && store.exists(target)) {
                store.delete(target);
            }
        }
    }

    /** Moves a file. It is moved back only if this step moved it: a target that is not its work stays. */
    final class Move implements Step {

        private final Path from;
        private final Path target;
        private boolean moved;

        Move(Path from, Path target) {
            this.from = from;
            this.target = target;
        }

        @Override
        public Path target() {
            return target;
        }

        @Override
        public void apply(FileStore store) throws IOException {
            store.move(from, target);
            moved = true;
        }

        @Override
        public void undo(FileStore store) throws IOException {
            if (moved) {
                store.move(target, from);
            }
        }
    }

    /**
     * Deletes a file, and gives it back with its content and its permissions as they were when it was deleted (not when
     * the set was planned: an earlier change of the set may have created or moved the file).
     */
    final class Delete implements Step {

        private final Path target;
        private final byte[] previous;
        private Set<PosixFilePermission> permissions = Set.of();

        Delete(Path target, byte[] previous) {
            this.target = target;
            this.previous = previous;
        }

        @Override
        public Path target() {
            return target;
        }

        @Override
        public void apply(FileStore store) throws IOException {
            permissions = store.permissions(target);
            store.delete(target);
        }

        @Override
        public void undo(FileStore store) throws IOException {
            if (!store.exists(target)) {
                store.write(target, previous);
                store.setPermissions(target, permissions);
            }
        }
    }
}
