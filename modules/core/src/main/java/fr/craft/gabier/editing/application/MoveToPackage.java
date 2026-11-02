package fr.craft.gabier.editing.application;

import fr.craft.gabier.editing.domain.ChangeApplier;
import fr.craft.gabier.editing.domain.ChangeReport;
import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.FileChange;
import fr.craft.gabier.editing.domain.FileStore;
import fr.craft.gabier.editing.domain.JavaLanguageServer;
import fr.craft.gabier.editing.domain.PackageName;
import fr.craft.gabier.shared.error.domain.Assert;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Moves Java sources to a package: asks the language server what that changes, and applies the answer to the project
 * all or nothing, or only reports it. The folder of a package that does not exist is made by applying the answer, and
 * the folders the move leaves empty, from the folder of a moved file up to its source folder, are removed with it (a
 * dry run removes none).
 * Whatever refuses, nothing has been written and no folder has been made: the server (not ready, a file outside the
 * project, a file that cannot go to the package), or the application of the change set (a file appeared in the
 * package since the server answered).
 */
public final class MoveToPackage {

    private final JavaLanguageServer server;
    private final ChangeApplier applier;

    public MoveToPackage(JavaLanguageServer server, FileStore store) {
        Assert.notNull("server", server);
        Assert.notNull("store", store);

        this.server = server;
        this.applier = new ChangeApplier(store);
    }

    /**
     * Moves the files to the package, and writes the files.
     *
     * @return what was done
     * @throws fr.craft.gabier.editing.domain.LanguageServerException if the server declines
     * @throws fr.craft.gabier.editing.domain.EditingException if the change set of the server cannot be applied
     */
    public ChangeReport apply(List<Path> files, PackageName target) {
        ChangeSet changes = changes(files, target);

        return applier.apply(changes, foldersLeft(changes, target));
    }

    /**
     * The folders the moved files leave, from the deepest: the folder of each file and its parents, up to the source
     * folder, which is the folder the file goes to less the folders of the package. Those that the move empties are
     * removed.
     */
    private static List<Path> foldersLeft(ChangeSet changes, PackageName target) {
        int depth = target.folder().getNameCount();
        Set<Path> left = new HashSet<>();
        for (FileChange change : changes.changes()) {
            if (change instanceof FileChange.RenameFile(Path from, Path to)) {
                Path sourceFolder = to;
                for (int level = 0; level <= depth; level++) {
                    sourceFolder = parent(sourceFolder);
                }
                // The file leaves a folder of the source folder it goes to: the server moves the files inside one
                // source folder (the files of several are refused before it is asked).
                for (Path folder = parent(from);
                        folder.getNameCount() > sourceFolder.getNameCount();
                        folder = parent(folder)) {
                    left.add(folder);
                }
            }
        }

        return left.stream()
                .sorted(Comparator.comparingInt(Path::getNameCount).reversed().thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    /** The folder that holds the path, absolute and normalized; the root of the file system for the root. */
    private static Path parent(Path path) {
        return path.toAbsolutePath().resolve("..").normalize();
    }

    /**
     * What the move would do, without writing anything.
     *
     * @return the files that would be moved and changed, and the unified diff
     * @throws fr.craft.gabier.editing.domain.LanguageServerException if the server declines
     * @throws fr.craft.gabier.editing.domain.EditingException if the change set of the server cannot be applied
     */
    public ChangeReport dryRun(List<Path> files, PackageName target) {
        return applier.dryRun(changes(files, target));
    }

    private ChangeSet changes(List<Path> files, PackageName target) {
        Assert.field("files", files).notNull().notEmpty().noNullElement();
        Assert.notNull("target", target);

        return server.moveToPackage(files, target);
    }
}
