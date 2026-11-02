package eu.gabier.editing.domain;

import eu.gabier.shared.error.domain.Assert;
import java.nio.file.Path;
import java.util.List;

/**
 * One change of a {@link ChangeSet}, to a file or to the existence of a file. Paths are the paths of regular files,
 * in any spelling: {@code x}, {@code ./x} and {@code a/../x} are one file (see {@link ChangeApplier}).
 */
public sealed interface FileChange {

    /**
     * Text edits of one file. They all refer to the text the file has when this change is applied, in whatever order
     * they are listed, and must not overlap (see {@link Position} for the convention).
     *
     * @param file the file to edit, which must exist
     * @param edits at least one edit
     */
    record EditFile(Path file, List<TextEdit> edits) implements FileChange {

        public EditFile {
            Assert.notNull("file", file);
            Assert.field("edits", edits).notEmpty().noNullElement();
            edits = List.copyOf(edits);
        }
    }

    /**
     * The creation of an empty file, to which a later {@link EditFile} of the same set gives its text, as in the
     * Language Server Protocol.
     *
     * @param file the file to create, which must not exist; its directory is made if it does not, but no file may be
     *     where a directory of the path would go
     */
    record CreateFile(Path file) implements FileChange {

        public CreateFile {
            Assert.notNull("file", file);
        }
    }

    /**
     * The move of a file, with its content, to a path where no file exists.
     *
     * @param from the file to move, which must exist
     * @param to where it goes, which must not exist; its directory is made if it does not, as for {@link CreateFile}
     */
    record RenameFile(Path from, Path to) implements FileChange {

        public RenameFile {
            Assert.notNull("from", from);
            Assert.notNull("to", to);
        }
    }

    /**
     * The removal of a file.
     *
     * @param file the file to delete, which must exist
     */
    record DeleteFile(Path file) implements FileChange {

        public DeleteFile {
            Assert.notNull("file", file);
        }
    }
}
