package fr.craft.gabier.editing.domain;

import fr.craft.gabier.editing.domain.ChangeReport.Renamed;
import fr.craft.gabier.editing.domain.FileChange.CreateFile;
import fr.craft.gabier.editing.domain.FileChange.DeleteFile;
import fr.craft.gabier.editing.domain.FileChange.EditFile;
import fr.craft.gabier.editing.domain.FileChange.RenameFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Runs a {@link ChangeSet} on a picture of the files held in memory, change after change, so that every refusal comes
 * before anything is written. The picture starts as the files of the {@link FileStore} and is read from it only for the
 * files the changes touch.
 */
final class PlanBuilder {

    private static final String NOTHING = "/dev/null";

    private final FileStore store;
    /** The files the changes have read or written: their bytes, or empty for a file that is not there any more. */
    private final Map<Path, Optional<byte[]>> files = new HashMap<>();

    private final List<Step> steps = new ArrayList<>();
    private final List<Path> created = new ArrayList<>();
    private final List<Renamed> renamed = new ArrayList<>();
    private final List<Path> deleted = new ArrayList<>();
    private final Set<Path> modified = new LinkedHashSet<>();
    private final StringBuilder diff = new StringBuilder();

    PlanBuilder(FileStore store) {
        this.store = store;
    }

    ChangePlan plan(ChangeSet changes) {
        changes.changes().forEach(this::plan);

        return new ChangePlan(
                steps, new ChangeReport(created, renamed, deleted, List.copyOf(modified), diff.toString()));
    }

    private void plan(FileChange change) {
        switch (change) {
            case EditFile edit -> edit(edit);
            case CreateFile create -> create(normalized(create.file()));
            case RenameFile rename -> rename(normalized(rename.from()), normalized(rename.to()));
            case DeleteFile delete -> delete(normalized(delete.file()));
        }
    }

    private void edit(EditFile change) {
        Path file = normalized(change.file());
        byte[] bytes = content(file);
        TextFile before = TextFile.decode(file, bytes);
        TextFile after = before.edit(file, change.edits());
        if (after.equals(before)) {
            return;
        }
        byte[] edited = after.encode(file);
        steps.add(new Step.Write(file, edited, Optional.of(bytes)));
        files.put(file, Optional.of(edited));
        modified.add(file);
        diff.append(UnifiedDiff.of(file.toString(), before.text(), file.toString(), after.text()));
    }

    private void create(Path file) {
        if (exists(file)) {
            throw EditingException.fileExists(file);
        }
        requireFolder(file);
        byte[] empty = new byte[0];
        steps.add(new Step.Write(file, empty, Optional.empty()));
        files.put(file, Optional.of(empty));
        created.add(file);
        diff.append(UnifiedDiff.of(NOTHING, "", file.toString(), ""));
    }

    private void rename(Path from, Path to) {
        byte[] bytes = content(from);
        if (exists(to)) {
            throw EditingException.fileExists(to);
        }
        requireFolder(to);
        steps.add(new Step.Move(from, to));
        files.put(from, Optional.empty());
        files.put(to, Optional.of(bytes));
        renamed.add(new Renamed(from, to));
        diff.append(UnifiedDiff.of(from.toString(), "", to.toString(), ""));
    }

    private void delete(Path file) {
        byte[] bytes = content(file);
        steps.add(new Step.Delete(file, bytes));
        files.put(file, Optional.empty());
        deleted.add(file);
        // The lines of a file that is not text cannot be shown: its headers will do.
        String text = TextFile.tryDecode(bytes).map(TextFile::text).orElse("");
        diff.append(UnifiedDiff.of(file.toString(), text, NOTHING, ""));
    }

    /**
     * A path as the one name of its file: absolute, without {@code .} nor {@code ..} segments, so that {@code ./x},
     * {@code x} and {@code a/../x} are one entry of the picture. The cleaning is on the text of the path: a link in it
     * is not followed.
     */
    private static Path normalized(Path file) {
        return file.toAbsolutePath().normalize();
    }

    /** The set creates files, never folders: the folder of a new file must be there already,
     * and be a folder. */
    private void requireFolder(Path file) {
        // A file that is to be created is not the root, which exists: it has a folder.
        Path folder = Objects.requireNonNull(file.getParent());
        if (!store.isFolder(folder)) {
            throw EditingException.folderMissing(file, folder);
        }
    }

    private boolean exists(Path file) {
        Optional<byte[]> known = files.get(file);

        return known != null ? known.isPresent() : store.exists(file);
    }

    private byte[] content(Path file) {
        if (!exists(file)) {
            throw EditingException.fileMissing(file);
        }

        return files.computeIfAbsent(file, this::read).orElseThrow();
    }

    private Optional<byte[]> read(Path file) {
        try {
            return Optional.of(store.read(file));
        } catch (IOException failure) {
            throw EditingException.readFailed(file, failure);
        }
    }
}
