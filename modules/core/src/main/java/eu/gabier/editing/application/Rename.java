package eu.gabier.editing.application;

import eu.gabier.editing.application.RenameTarget.At;
import eu.gabier.editing.application.RenameTarget.Token;
import eu.gabier.editing.domain.ChangeApplier;
import eu.gabier.editing.domain.ChangeReport;
import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.FileChange;
import eu.gabier.editing.domain.FileStore;
import eu.gabier.editing.domain.JavaLanguageServer;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.TokenLocator;
import eu.gabier.shared.error.domain.Assert;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renames a symbol: finds it, asks the language server what the rename changes, and applies the answer to the files all
 * or nothing, or only reports it. Whatever refuses, nothing has been written: the server (not ready, no symbol, invalid
 * name, file outside the project), the lookup of an identifier, or the application of the change set. The name in a
 * {@code package} line is a symbol too: the files of the package move to the folder of the new name, and the folders
 * the move empties are removed with it (a dry run removes none).
 */
public final class Rename {

    private final JavaLanguageServer server;
    private final TokenLocator locator;
    private final ChangeApplier applier;

    public Rename(JavaLanguageServer server, FileStore store) {
        Assert.notNull("server", server);
        Assert.notNull("store", store);

        this.server = server;
        this.locator = new TokenLocator(store);
        this.applier = new ChangeApplier(store);
    }

    /**
     * Renames the symbol and writes the files.
     *
     * @return what was done
     * @throws eu.gabier.editing.domain.LanguageServerException if the server declines
     * @throws eu.gabier.editing.domain.EditingException if the identifier is not found, or the change set of the
     *     server cannot be applied
     */
    public ChangeReport apply(RenameTarget target, String newName) {
        ChangeSet changes = changes(target, newName);

        return applier.apply(changes, foldersLeft(changes));
    }

    /**
     * The folders the files that change folder leave, from the deepest: the folder of each moved file and its parents,
     * up to the folder it shares with the folder the file goes to (the renamed package's source folder, or a folder
     * below it). The rename of a package moves its files to the folder of the new name; those of them that the move
     * empties are removed, with the rest left alone. A file renamed in its own folder (a renamed type) leaves none.
     */
    private static List<Path> foldersLeft(ChangeSet changes) {
        Set<Path> left = new HashSet<>();
        for (FileChange change : changes.changes()) {
            if (change instanceof FileChange.RenameFile(Path from, Path to)) {
                Path destination = parent(to);
                for (Path folder = parent(from); !destination.startsWith(folder); folder = parent(folder)) {
                    left.add(folder);
                }
            }
        }

        return left.stream()
                .sorted(Comparator.comparingInt(Path::getNameCount).reversed().thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    /** The folder that holds the path, absolute and normalized. */
    private static Path parent(Path path) {
        return path.toAbsolutePath().resolve("..").normalize();
    }

    /**
     * What renaming the symbol would do, without writing anything.
     *
     * @return the files that would change, and the unified diff
     * @throws eu.gabier.editing.domain.LanguageServerException if the server declines
     * @throws eu.gabier.editing.domain.EditingException if the identifier is not found, or the change set of the
     *     server cannot be applied
     */
    public ChangeReport dryRun(RenameTarget target, String newName) {
        return applier.dryRun(changes(target, newName));
    }

    private ChangeSet changes(RenameTarget target, String newName) {
        Assert.notNull("target", target);
        Assert.notNull("newName", newName);

        Position position = switch (target) {
            case At at -> at.position();
            case Token token -> locator.firstOccurrence(token.file(), token.identifier());
        };

        return server.rename(target.file(), position, newName);
    }
}
