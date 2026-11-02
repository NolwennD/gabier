package fr.craft.gabier.editing.fixtures;

import fr.craft.gabier.editing.domain.ChangeApplier;
import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.FileChange;
import fr.craft.gabier.editing.infrastructure.secondary.NioFileStore;
import fr.craft.gabier.shared.error.domain.Assert;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/**
 * What a change set does to a project: the text of its sources once the set is applied. A replay compares this, the
 * effect, and not the edits that a server chose to write it, so that another server that writes the same text with
 * other edits passes the same scenarios.
 */
public final class Effect {

    private static final Path SOURCES = Path.of("src");

    private Effect() {}

    /**
     * The sources of the project (the files under {@code src}) once the change set is applied to a copy of it. The
     * project is not touched.
     *
     * @param root the folder of the project, which holds {@code src}
     * @param changes the changes, with the paths under the root
     * @return the text of each source, by its path relative to the root; a moved file is at its new path only
     * @throws fr.craft.gabier.editing.domain.EditingException if the set cannot be applied
     */
    public static Map<Path, String> of(Path root, ChangeSet changes) throws IOException {
        Assert.notNull("root", root);
        Assert.notNull("changes", changes);

        Path copy = Files.createTempDirectory("gabier-effect-").toRealPath();
        try {
            copySources(root, copy);
            new ChangeApplier(new NioFileStore()).apply(rebased(changes, root, copy));

            return sources(copy);
        } finally {
            delete(copy);
        }
    }

    private static void copySources(Path from, Path to) throws IOException {
        try (var files = Files.walk(from.resolve(SOURCES))) {
            for (Path file : files.toList()) {
                Path target = to.resolve(from.relativize(file));
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(file, target);
                }
            }
        }
    }

    private static Map<Path, String> sources(Path root) throws IOException {
        Map<Path, String> found = new TreeMap<>();
        try (var files = Files.walk(root.resolve(SOURCES))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                found.put(root.relativize(file), Files.readString(file));
            }
        }

        return found;
    }

    private static ChangeSet rebased(ChangeSet changes, Path root, Path copy) {
        return new ChangeSet(changes.changes().stream()
                .<FileChange>map(change -> switch (change) {
                    case FileChange.EditFile edit ->
                        new FileChange.EditFile(rebased(edit.file(), root, copy), edit.edits());
                    case FileChange.CreateFile create -> new FileChange.CreateFile(rebased(create.file(), root, copy));
                    case FileChange.RenameFile rename ->
                        new FileChange.RenameFile(rebased(rename.from(), root, copy), rebased(rename.to(), root, copy));
                    case FileChange.DeleteFile delete -> new FileChange.DeleteFile(rebased(delete.file(), root, copy));
                })
                .toList());
    }

    private static Path rebased(Path file, Path root, Path copy) {
        return copy.resolve(root.relativize(file));
    }

    private static void delete(Path folder) throws IOException {
        try (var files = Files.walk(folder)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
    }
}
